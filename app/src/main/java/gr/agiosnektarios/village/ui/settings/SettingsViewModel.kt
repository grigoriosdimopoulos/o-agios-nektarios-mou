package gr.agiosnektarios.village.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.NotificationPrefs
import gr.agiosnektarios.village.data.admin.AdminRepository
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.settings.AppLanguage
import gr.agiosnektarios.village.data.settings.AppSettings
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _events = MutableStateFlow(SettingsEvents())
    val events: StateFlow<SettingsEvents> = _events.asStateFlow()

    /** Google accounts have no password to change, so the row is hidden. */
    val canChangePassword: Boolean get() = !authRepository.isGoogleOnlyAccount()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    /**
     * Notification toggles are written twice on purpose: to DataStore so the
     * switch responds instantly and works offline, and to the user document
     * because the Cloud Functions read *that* copy before sending anything.
     */
    fun setNotificationPref(pref: SettingsRepository.NotificationPref, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationPref(pref, enabled)
            val userId = sessionRepository.currentUserId ?: return@launch
            val current = settings.value
            val updated = NotificationPrefs(
                comments = if (pref == SettingsRepository.NotificationPref.COMMENTS) {
                    enabled
                } else {
                    current.notifyComments
                },
                statusChanges = if (pref == SettingsRepository.NotificationPref.STATUS) {
                    enabled
                } else {
                    current.notifyStatus
                },
                votes = if (pref == SettingsRepository.NotificationPref.VOTES) {
                    enabled
                } else {
                    current.notifyVotes
                },
                announcements = if (pref == SettingsRepository.NotificationPref.ANNOUNCEMENTS) {
                    enabled
                } else {
                    current.notifyAnnouncements
                },
                chat = if (pref == SettingsRepository.NotificationPref.CHAT) {
                    enabled
                } else {
                    current.notifyChat
                },
            )
            userRepository.updateNotificationPrefs(userId, updated)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val result = adminRepository.deleteMyAccount()
            _events.update {
                it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage)
            }
            // On success the auth account is gone; signing out locally clears
            // the cached session immediately rather than waiting for a refresh.
            if (result.isSuccess) authRepository.signOut()
        }
    }

    fun consumeError() = _events.update { it.copy(errorMessage = null) }
}

data class SettingsEvents(val errorMessage: String? = null)

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val errorMessage: String? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrent(value: String) = _uiState.update { it.copy(currentPassword = value) }
    fun onNew(value: String) = _uiState.update { it.copy(newPassword = value) }
    fun onConfirmation(value: String) = _uiState.update { it.copy(confirmation = value) }

    fun submit() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val result = authRepository.changePassword(state.currentPassword, state.newPassword)
            _uiState.update {
                it.copy(
                    loading = false,
                    done = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
