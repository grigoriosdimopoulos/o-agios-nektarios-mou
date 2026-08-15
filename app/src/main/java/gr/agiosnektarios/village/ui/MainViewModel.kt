package gr.agiosnektarios.village.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.crash.CrashReporter
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.data.settings.AppSettings
import gr.agiosnektarios.village.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Application-level state: which theme and language to render in, and whether
 * anyone is signed in. Held by the activity so the whole tree reacts to a
 * settings change at once.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    sessionRepository: SessionRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    /**
     * The previous run's crash, read once. Testers here have no computer and
     * therefore no logcat, so the app has to hand its own stack trace over.
     */
    private val _lastCrash = MutableStateFlow(crashReporter.lastCrash())
    val lastCrash: StateFlow<String?> = _lastCrash

    fun dismissCrashReport() {
        crashReporter.clear()
        _lastCrash.value = null
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val session: StateFlow<SessionState> = sessionRepository.state
}
