package gr.agiosnektarios.village.ui.issue

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.data.issue.IssueDraft
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.location.LocationProvider
import gr.agiosnektarios.village.data.media.ImageCodec
import gr.agiosnektarios.village.data.media.ImageSpec
import gr.agiosnektarios.village.core.geo.label
import gr.agiosnektarios.village.data.session.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the location for a quick report came from, because it changes what to say. */
enum class FixState { IDLE, LOCATING, FOUND, UNAVAILABLE }

data class QuickReportUiState(
    val photo: ByteArray? = null,
    val thumbnail: ByteArray? = null,
    val text: String = "",
    val position: GeoPoint? = null,
    val fix: FixState = FixState.IDLE,
    /** True once the resident has chosen the point themselves. */
    val positionIsManual: Boolean = false,
    val category: IssueCategory = IssueCategory.OTHER,
    val encoding: Boolean = false,
    val submitting: Boolean = false,
    val savedIssueId: String? = null,
    val errorMessage: String? = null,
) {
    /** A photo alone is a report. Text alone is a report. Neither is not. */
    val canSubmit: Boolean
        get() = !submitting && !encoding && position != null &&
            (photo != null || text.isNotBlank())

    // Arrays in a data class: equals must compare contents or every emission
    // looks like a change and the sheet recomposes on a timer.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuickReportUiState) return false
        return photo.contentEqualsOrNull(other.photo) &&
            thumbnail.contentEqualsOrNull(other.thumbnail) &&
            text == other.text && position == other.position && fix == other.fix &&
            positionIsManual == other.positionIsManual &&
            category == other.category && encoding == other.encoding &&
            submitting == other.submitting && savedIssueId == other.savedIssueId &&
            errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = photo?.contentHashCode() ?: 0
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        result = 31 * result + text.hashCode()
        result = 31 * result + (position?.hashCode() ?: 0)
        result = 31 * result + fix.hashCode() + category.hashCode()
        result = 31 * result + submitting.hashCode() + encoding.hashCode()
        return result
    }
}

private fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else contentEquals(other)

/**
 * Filing a report in about ten seconds.
 *
 * The full composer asks for a title, a description, one of sixteen
 * categories, a point on a map and photos. That is the right form for someone
 * sitting down at home; it is the wrong one for the person this app is for,
 * who is standing in a field looking at a fallen tree, possibly in their
 * seventies, holding the phone in one hand. Reports that take a minute to file
 * get told to a neighbour instead.
 *
 * So this asks for the two things the situation already provides — a
 * photograph and where you are standing — plus one line of text, and treats
 * everything else as something to fill in later or never. The title is the
 * first line of what was typed; the category defaults to OTHER and can be
 * corrected by anyone afterwards. A report that exists and is vague is worth
 * far more to the village than a perfect one that was never filed.
 */
@HiltViewModel
class QuickReportViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val placeNamer: gr.agiosnektarios.village.data.village.PlaceNamer,
    private val sessionRepository: SessionRepository,
    private val locationProvider: LocationProvider,
    private val imageCodec: ImageCodec,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickReportUiState())
    val uiState: StateFlow<QuickReportUiState> = _uiState.asStateFlow()

    /** Called as the sheet opens, so the fix is usually there before the typing is. */
    fun start() {
        _uiState.value = QuickReportUiState()
        locate()
    }

    fun locate() {
        // A point the resident placed by hand outranks anything the hardware
        // says afterwards. The permission callback calls this again on the way
        // back from the map, and without this guard a late fix would quietly
        // replace the spot they had just chosen.
        if (_uiState.value.positionIsManual) return
        if (!locationProvider.hasPermission || !locationProvider.isEnabled) {
            _uiState.update { it.copy(fix = FixState.UNAVAILABLE) }
            return
        }
        _uiState.update { it.copy(fix = FixState.LOCATING) }
        viewModelScope.launch {
            val point = locationProvider.current()
            _uiState.update {
                when {
                    // Re-checked on arrival, not only on entry. The lookup can
                    // take eight seconds, and the map picker is reachable
                    // throughout — so a resident could choose a point by hand
                    // and have the hardware overwrite it when it finally
                    // answered. The guard at the top of this function cannot
                    // see a choice made after it ran.
                    it.positionIsManual -> it
                    point == null -> it.copy(fix = FixState.UNAVAILABLE)
                    else -> it.copy(position = point, fix = FixState.FOUND)
                }
            }
        }
    }

    fun onPhotoTaken(uri: Uri) {
        _uiState.update { it.copy(encoding = true) }
        viewModelScope.launch {
            val full = imageCodec.encode(uri, ImageSpec.ISSUE_PHOTO)
            val thumb = imageCodec.encode(uri, ImageSpec.ISSUE_THUMBNAIL).getOrNull()
            _uiState.update { state ->
                full.fold(
                    onSuccess = { bytes ->
                        state.copy(photo = bytes, thumbnail = thumb, encoding = false)
                    },
                    onFailure = { error ->
                        state.copy(encoding = false, errorMessage = error.message)
                    },
                )
            }
        }
    }

    fun onTextChange(value: String) = _uiState.update { it.copy(text = value) }

    fun onCategoryChange(category: IssueCategory) =
        _uiState.update { it.copy(category = category) }

    /** Used when the resident says "I am not there" and picks a spot by hand. */
    fun setPosition(point: GeoPoint) =
        _uiState.update {
            it.copy(position = point, fix = FixState.FOUND, positionIsManual = true)
        }

    fun submit() {
        val state = _uiState.value
        val position = state.position ?: return
        val author = sessionRepository.currentProfile ?: return
        if (!state.canSubmit) return

        _uiState.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val place = runCatching { placeNamer.describe(position) }.getOrNull()
            val trimmed = state.text.trim()
            issueRepository.createIssue(
                draft = IssueDraft(
                    // The first line becomes the title, the rest the body. One
                    // field that behaves sensibly beats two fields that must
                    // both be filled.
                    title = trimmed.lineSequence().firstOrNull()?.take(TITLE_MAX).orEmpty()
                        .ifBlank { DEFAULT_TITLE },
                    description = trimmed.substringAfter('\n', "").trim(),
                    category = state.category,
                    position = position,
                    placeLabel = place.label(),
                    photos = listOfNotNull(state.photo),
                    thumbnail = state.thumbnail,
                ),
                author = author,
            ).fold(
                onSuccess = { id -> _uiState.update { it.copy(submitting = false, savedIssueId = id) } },
                onFailure = { error ->
                    _uiState.update { it.copy(submitting = false, errorMessage = error.message) }
                },
            )
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        const val TITLE_MAX = 80
        /** A photo with no words is still a report; it needs something to be called. */
        const val DEFAULT_TITLE = "Αναφορά με φωτογραφία"
    }
}
