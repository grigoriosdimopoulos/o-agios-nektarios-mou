package gr.agiosnektarios.village.ui.issue

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.agiosnektarios.village.core.UserMessages
import gr.agiosnektarios.village.core.geo.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.data.issue.IssueDraft
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.core.model.IssuePhoto
import gr.agiosnektarios.village.data.media.ImageCodec
import gr.agiosnektarios.village.data.media.ImageSpec
import gr.agiosnektarios.village.core.geo.label
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
    /** Photos already saved with the report, shown so they can be removed. */
    val existingPhotos: List<IssuePhoto> = emptyList(),
    /** Newly picked photos, already encoded and waiting to be written. */
    val newPhotos: List<ByteArray> = emptyList(),
    val removedPhotoIds: List<String> = emptyList(),
    val thumbnail: ByteArray? = null,
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
) {
    /** Photos the report will have once saved, kept and new together. */
    val photoCount: Int get() = existingPhotos.size + newPhotos.size
}

@HiltViewModel
class IssueComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val issueRepository: IssueRepository,
    private val imageCodec: ImageCodec,
    private val placeNamer: gr.agiosnektarios.village.data.village.PlaceNamer,
    private val blockRepository: VillageBlockRepository,
    private val sessionRepository: SessionRepository,
    private val messages: UserMessages,
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
                    existingPhotos = issueRepository.getPhotos(issueId).getOrDefault(emptyList()),
                    // Carried forward so editing the title does not silently
                    // strip the card's picture.
                    thumbnail = issue.thumbnail?.toBytes(),
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

    /**
     * Encodes a picked photo and holds it in memory until the report is saved.
     *
     * Nothing is written yet on purpose: a resident who backs out of composing
     * should not leave photos behind in the database, and there is no storage
     * bucket to sweep them from.
     */
    fun addPhoto(uri: Uri) {
        if (_uiState.value.photoCount >= MAX_PHOTOS) return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPhoto = true, errorMessage = null) }
            val result = imageCodec.encode(uri, ImageSpec.ISSUE_PHOTO)
            _uiState.update { state ->
                state.copy(
                    uploadingPhoto = false,
                    newPhotos = result.getOrNull()?.let { state.newPhotos + it }
                        ?: state.newPhotos,
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
            // The card thumbnail is cut from whatever is now first.
            refreshThumbnail(uri)
        }
    }

    /** Kept separate so removing the first photo re-cuts the stamp. */
    private suspend fun refreshThumbnail(uri: Uri) {
        val thumb = imageCodec.encode(uri, ImageSpec.ISSUE_THUMBNAIL).getOrNull()
        _uiState.update { if (it.thumbnail == null) it.copy(thumbnail = thumb) else it }
    }

    fun removeExistingPhoto(photoId: String) = _uiState.update {
        it.copy(
            existingPhotos = it.existingPhotos.filterNot { photo -> photo.id == photoId },
            removedPhotoIds = it.removedPhotoIds + photoId,
        ).withoutOrphanThumbnail()
    }

    fun removeNewPhoto(index: Int) = _uiState.update {
        it.copy(newPhotos = it.newPhotos.filterIndexed { i, _ -> i != index })
            .withoutOrphanThumbnail()
    }

    /** A card thumbnail for a report with no photos left is a picture of nothing. */
    private fun IssueComposeUiState.withoutOrphanThumbnail(): IssueComposeUiState =
        if (photoCount == 0) copy(thumbnail = null) else this

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
            val place = runCatching { placeNamer.describe(position) }.getOrNull()
            val draft = IssueDraft(
                title = state.title,
                description = state.description,
                category = state.category!!,
                position = position,
                blockId = block?.id.orEmpty(),
                placeLabel = place.label(),
                photos = state.newPhotos,
                thumbnail = state.thumbnail,
            )

            val result = if (state.isEditing) {
                issueRepository.updateIssue(
                    issueId = editingIssueId,
                    draft = draft,
                    authorId = author.id,
                    keptPhotoCount = state.existingPhotos.size,
                    removedPhotoIds = state.removedPhotoIds,
                ).map { editingIssueId }
            } else {
                issueRepository.createIssue(draft, author)
            }

            _uiState.update {
                it.copy(
                    saving = false,
                    savedIssueId = result.getOrNull(),
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        /**
         * Each photo is a document of its own, so this is a courtesy to the
         * person on village mobile data rather than a storage limit.
         */
        const val MAX_PHOTOS = 4
    }
}
