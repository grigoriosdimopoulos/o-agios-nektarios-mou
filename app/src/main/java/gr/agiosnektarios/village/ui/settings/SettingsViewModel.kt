package gr.agiosnektarios.village.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.UserMessages
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.NotificationPrefs
import gr.agiosnektarios.village.data.admin.AdminElevationRepository
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
import gr.agiosnektarios.village.core.model.Feature
import gr.agiosnektarios.village.data.settings.FeatureRepository
import gr.agiosnektarios.village.data.user.EmergencyContactRepository
import gr.agiosnektarios.village.data.session.SessionState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import gr.agiosnektarios.village.core.model.FeatureFlags

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val adminRepository: AdminRepository,
    private val adminElevationRepository: AdminElevationRepository,
    private val emergencyContacts: EmergencyContactRepository,
    private val features: FeatureRepository,
    private val messages: UserMessages,
) : ViewModel() {

    /**
     * Whether this resident has agreed their number may be texted, and whether
     * the village allows it at all.
     *
     * Both, because the screen has to say which of the two is why the switch
     * is not there — "you have not agreed" and "the village does not do this"
     * are different facts and only one of them is the resident's to change.
     */
    data class SmsConsent(val enabled: Boolean = false, val shared: Boolean = false)

    val smsConsent: StateFlow<SmsConsent> = combine(
        features.flags,
        sessionRepository.state.flatMapLatest { current ->
            when (current) {
                is SessionState.SignedIn -> emergencyContacts.observeMine(current.profile.id)
                else -> flowOf(false)
            }
        },
    ) { flags, shared ->
        SmsConsent(enabled = flags.isOn(Feature.SMS_TO_ALL), shared = shared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsConsent())

    /**
     * Agrees, or takes it back.
     *
     * The number is read from the private document at the moment of agreeing
     * rather than held anywhere: agreeing publishes the number as it stands,
     * and a resident who later changes it re-publishes by toggling. Nothing
     * mirrors automatically, because a mirror that runs without being asked is
     * how a number ends up somewhere its owner has forgotten about.
     */
    fun setSmsConsent(share: Boolean) {
        val me = sessionRepository.currentProfile ?: return
        viewModelScope.launch {
            if (!share) {
                emergencyContacts.withdraw(me.id)
                return@launch
            }
            val phone = userRepository.observePhone(me.id).first()
            if (phone.isBlank()) {
                _events.update { it.copy(errorMessage = messages.string(R.string.sms_needs_number)) }
                return@launch
            }
            emergencyContacts.share(me.id, me.displayName, phone)
                .onFailure { error -> _events.update { it.copy(errorMessage = messages.of(error)) } }
        }
    }

    val flags: StateFlow<FeatureFlags> = features.flags

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _events = MutableStateFlow(SettingsEvents())
    val events: StateFlow<SettingsEvents> = _events.asStateFlow()

    private val _adminUnlock = MutableStateFlow(AdminUnlockState())
    val adminUnlock: StateFlow<AdminUnlockState> = _adminUnlock.asStateFlow()

    fun openAdminUnlock() = _adminUnlock.update { AdminUnlockState(visible = true) }

    fun dismissAdminUnlock() = _adminUnlock.update { AdminUnlockState() }

    fun onAdminPassphrase(value: String) =
        _adminUnlock.update { it.copy(passphrase = value, errorRes = null) }

    /**
     * Attempts elevation.
     *
     * The passphrase is checked by the security rules, never here — this cannot
     * know whether it was right, only whether the server accepted it. That is
     * the point: a check the client could perform is a check an attacker could
     * skip.
     */
    fun submitAdminUnlock() {
        val userId = sessionRepository.currentUserId ?: return
        val passphrase = _adminUnlock.value.passphrase
        if (passphrase.isBlank()) return

        viewModelScope.launch {
            _adminUnlock.update { it.copy(submitting = true, errorRes = null) }
            val result = adminElevationRepository.elevate(userId, passphrase)
            _adminUnlock.update {
                if (result.isSuccess) {
                    AdminUnlockState(elevated = true)
                } else {
                    it.copy(submitting = false, errorRes = R.string.admin_unlock_rejected)
                }
            }
        }
    }

    fun consumeElevation() = _adminUnlock.update { AdminUnlockState() }

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
                it.copy(errorMessage = messages.of(result.exceptionOrNull()))
            }
            // On success the auth account is gone; signing out locally clears
            // the cached session immediately rather than waiting for a refresh.
            if (result.isSuccess) authRepository.signOut()
        }
    }

    fun consumeError() = _events.update { it.copy(errorMessage = null) }
}

data class SettingsEvents(val errorMessage: String? = null)

/** State of the hidden administrator unlock. */
data class AdminUnlockState(
    val visible: Boolean = false,
    val passphrase: String = "",
    val submitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val elevated: Boolean = false,
)

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
    private val messages: UserMessages,
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
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
