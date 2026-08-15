package gr.agiosnektarios.village.ui.issue

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.agiosnektarios.village.core.geo.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.data.issue.IssueDraft
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.media.MediaRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.village.VillageBlockRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IssueComposeUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: IssueCategory? = null,
    val position: GeoPoint? = null,
    /** Both languages, since the view model has no business knowing the locale. */
    val blockNameEl: String = "",
    val blockNameEn: String = "",
    val photoUrls: List<String> = emptyList(),
    val uploadingPhoto: Boolean = false,
    @StringRes val titleError: Int? = null,
    @StringRes val categoryError: Int? = null,
    @StringRes val locationError: Int? = null,
    /** Reports of the same kind already filed at this spot. */
    val similarNearby: List<Issue> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val savedIssueId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class IssueComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val issueRepository: IssueRepository,
    private val mediaRepository: MediaRepository,
    private val blockRepository: VillageBlockRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val editingIssueId: String = savedStateHandle.get<String>("issueId").orEmpty()

    private val _uiState = MutableStateFlow(IssueComposeUiState())
    val uiState: StateFlow<IssueComposeUiState> = _uiState.asStateFlow()

    init {
        if (editingIssueId.isNotBlank()) {
            loadExisting(editingIssueId)
        } else {
            // Coordinates arrive as strings: NavType.FloatType cannot carry the
            // precision a pin needs, and doubles are not a supported nav type.
            val lat = savedStateHandle.get<String>("lat")?.toDoubleOrNull()
            val lng = savedStateHandle.get<String>("lng")?.toDoubleOrNull()
            if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                setPosition(GeoPoint(lat, lng))
            }
        }
    }

    private fun loadExisting(issueId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, isEditing = true) }
            val issue = issueRepository.getIssue(issueId).getOrNull()
            if (issue == null) {
                _uiState.update { it.copy(loading = false, errorMessage = "Report not found") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    title = issue.title,
                    description = issue.description,
                    category = issue.category,
                    position = GeoPoint(issue.lat, issue.lng),
                    photoUrls = issue.photoUrls,
                )
            }
            refreshBlockName(GeoPoint(issue.lat, issue.lng))
        }
    }

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, titleError = null) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onCategoryChange(category: IssueCategory) {
        _uiState.update { it.copy(category = category, categoryError = null) }
        refreshSimilarNearby()
    }

    fun setPosition(position: GeoPoint) {
        _uiState.update { it.copy(position = position, locationError = null) }
        refreshBlockName(position)
        refreshSimilarNearby()
    }

    private fun refreshBlockName(position: GeoPoint) {
        viewModelScope.launch {
            val block = blockRepository.blockAt(position)
            _uiState.update {
                it.copy(
                    blockNameEl = block?.nameEl.orEmpty(),
                    blockNameEn = block?.nameEn.orEmpty(),
                )
            }
        }
    }

    /**
     * Surfaces duplicates *while composing* rather than after submitting.
     * Seeing "two neighbours already reported this" before you type is what
     * actually prevents a pile of duplicates for one pothole.
     */
    private fun refreshSimilarNearby() {
        val position = _uiState.value.position ?: return
        val category = _uiState.value.category ?: return
        viewModelScope.launch {
            val similar = issueRepository.findSimilarNearby(position, category)
                .getOrDefault(emptyList())
                .filter { it.id != editingIssueId }
            _uiState.update { it.copy(similarNearby = similar) }
        }
    }

    fun addPhoto(uri: Uri) {
        val userId = sessionRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPhoto = true) }
            val result = mediaRepository.uploadIssuePhoto(userId, uri)
            _uiState.update { state ->
                state.copy(
                    uploadingPhoto = false,
                    photoUrls = result.getOrNull()
                        ?.let { state.photoUrls + it }
                        ?: state.photoUrls,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun removePhoto(url: String) {
        _uiState.update { it.copy(photoUrls = it.photoUrls - url) }
        // The upload is already in Storage; drop it so abandoned photos do not
        // accumulate. Failure is not worth surfacing — the report is what matters.
        viewModelScope.launch { mediaRepository.delete(url) }
    }

    fun submit() {
        val state = _uiState.value
        val author = sessionRepository.currentProfile ?: return

        val titleError = if (state.title.isBlank()) R.string.issue_error_title_required else null
        val categoryError =
            if (state.category == null) R.string.issue_error_category_required else null
        val locationError = when {
            state.position == null -> R.string.issue_error_location_required
            !VillageConfig.isInsideVillage(state.position) ->
                R.string.issue_error_location_required
            else -> null
        }
        if (titleError != null || categoryError != null || locationError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    categoryError = categoryError,
                    locationError = locationError,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            val position = state.position!!
            val block = blockRepository.blockAt(position)
            val draft = IssueDraft(
                title = state.title,
                description = state.description,
                category = state.category!!,
                position = position,
                blockId = block?.id.orEmpty(),
                photoUrls = state.photoUrls,
            )

            val result = if (state.isEditing) {
                issueRepository.updateIssue(editingIssueId, draft).map { editingIssueId }
            } else {
                issueRepository.createIssue(draft, author)
            }

            _uiState.update {
                it.copy(
                    saving = false,
                    savedIssueId = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
