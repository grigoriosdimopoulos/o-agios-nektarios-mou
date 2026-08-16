package gr.agiosnektarios.village.ui.announcements

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.Announcement
import gr.agiosnektarios.village.data.announcement.AnnouncementRepository
import gr.agiosnektarios.village.data.media.ImageCodec
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.ui.navigation.DeepLinks
import gr.agiosnektarios.village.data.media.ImageSpec
import gr.agiosnektarios.village.data.session.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnouncementsUiState(
    val announcements: List<Announcement> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementRepository,
) : ViewModel() {

    val uiState: StateFlow<AnnouncementsUiState> = repository.observeAnnouncements()
        .map { AnnouncementsUiState(announcements = it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnnouncementsUiState())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class AnnouncementComposeUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val body: String = "",
    val image: ByteArray? = null,
    val pinned: Boolean = false,
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean get() = title.isNotBlank() && body.isNotBlank() && !saving
}

@HiltViewModel
class AnnouncementComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AnnouncementRepository,
    private val imageCodec: ImageCodec,
    private val notificationRepository: NotificationRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val announcementId: String =
        savedStateHandle.get<String>("announcementId").orEmpty()

    private val _uiState = MutableStateFlow(AnnouncementComposeUiState())
    val uiState: StateFlow<AnnouncementComposeUiState> = _uiState.asStateFlow()

    init {
        if (announcementId.isNotBlank()) {
            _uiState.update { it.copy(isEditing = true) }
            viewModelScope.launch {
                repository.observeAnnouncement(announcementId)
                    .catch { }
                    .collect { announcement ->
                        if (announcement != null && _uiState.value.title.isBlank()) {
                            _uiState.update {
                                it.copy(
                                    title = announcement.title,
                                    body = announcement.body,
                                    image = announcement.image?.toBytes(),
                                    pinned = announcement.pinned,
                                )
                            }
                        }
                    }
            }
        }
    }

    fun onTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun onBody(value: String) = _uiState.update { it.copy(body = value) }
    fun onPinned(value: Boolean) = _uiState.update { it.copy(pinned = value) }

    fun setImage(uri: Uri) {
        val userId = sessionRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true) }
            val result = imageCodec.encode(uri, ImageSpec.ANNOUNCEMENT)
            _uiState.update {
                it.copy(
                    uploading = false,
                    image = result.getOrNull() ?: it.image,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun clearImage() = _uiState.update { it.copy(image = null) }

    fun submit() {
        val author = sessionRepository.currentProfile ?: return
        // Belt and braces: the screen is unreachable for non-admins and the
        // security rules reject the write, but a client-side guard keeps a
        // mis-navigation from producing a confusing permission error.
        if (!author.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Administrators only") }
            return
        }
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            val result = if (state.isEditing) {
                repository.update(
                    id = announcementId,
                    title = state.title,
                    body = state.body,
                    image = state.image,
                    pinned = state.pinned,
                )
            } else {
                repository.publish(
                    author = author,
                    title = state.title,
                    body = state.body,
                    image = state.image,
                    pinned = state.pinned,
                ).map { }
            }
            // Only a brand new announcement is worth waking the village for;
            // fixing a typo in one already published is not.
            if (result.isSuccess && !state.isEditing) {
                notificationRepository.allResidentIds().getOrNull()?.let { residents ->
                    notificationRepository.notify(
                        recipientIds = residents,
                        actorId = author.id,
                        type = NotificationType.ANNOUNCEMENT,
                        title = state.title,
                        bodyKey = "notif_new_announcement",
                        deepLink = DeepLinks.ANNOUNCEMENTS,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
