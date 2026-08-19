package gr.agiosnektarios.village.ui.alert

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.UserMessages
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.model.AlertKind
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageAlert
import gr.agiosnektarios.village.data.alert.AlertRepository
import gr.agiosnektarios.village.data.location.LocationProvider
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.data.user.UserRepository
import gr.agiosnektarios.village.data.village.PlaceNamer
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the alert says it is. */
enum class AlertPlace { HERE, HOME, NONE }

data class RaiseAlertState(
    val kind: AlertKind? = null,
    val note: String = "",
    val place: AlertPlace = AlertPlace.HERE,
    val position: GeoPoint? = null,
    val placeLabel: String = "",
    val locating: Boolean = false,
    val raising: Boolean = false,
    val raisedId: String? = null,
    val errorMessage: String? = null,
) {
    val canRaise: Boolean get() = kind != null && !raising
}

data class ActiveAlertsState(
    val alerts: List<VillageAlert> = emptyList(),
    val userId: String = "",
    val canModerate: Boolean = false,
    /**
     * Every resident's telephone number, for the message the phone sends
     * itself. Only those who filled one in — the rest simply are not reachable
     * this way, and the screen says so rather than pretending.
     */
    val residentNumbers: List<String> = emptyList(),
)

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val session: SessionRepository,
    private val users: UserRepository,
    private val location: LocationProvider,
    private val placeNamer: PlaceNamer,
    private val messages: UserMessages,
    savedState: SavedStateHandle,
) : ViewModel() {

    init {
        // Arrived from a banner rather than from the button: the kind is
        // already known, so skip the question and land on the screen with the
        // telephone number on it.
        AlertKind.entries
            .firstOrNull { it.name == savedState.get<String>("kind") }
            ?.let(::pick)
    }

    val active: StateFlow<ActiveAlertsState> = combine(
        alerts.observeActive(),
        session.state.map { it as? SessionState.SignedIn },
        users.observeAllResidents(),
    ) { live, signedIn, residents ->
        ActiveAlertsState(
            alerts = live,
            userId = signedIn?.profile?.id.orEmpty(),
            canModerate = signedIn?.profile?.canModerate == true,
            residentNumbers = residents
                .filter { it.id != signedIn?.profile?.id }
                .map { it.phone.filter { character -> character.isDigit() || character == '+' } }
                .filter { it.length >= 8 }
                .distinct(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveAlertsState())

    private val _raise = MutableStateFlow(RaiseAlertState())
    val raise: StateFlow<RaiseAlertState> = _raise.asStateFlow()

    private val profile: UserProfile? get() = (session.state.value as? SessionState.SignedIn)?.profile

    fun pick(kind: AlertKind) {
        _raise.update { it.copy(kind = kind, errorMessage = null) }
        locate()
    }

    fun onNote(value: String) = _raise.update { it.copy(note = value) }

    fun onPlace(place: AlertPlace) {
        _raise.update { it.copy(place = place) }
        when (place) {
            AlertPlace.HERE -> locate()
            AlertPlace.HOME -> useHome()
            AlertPlace.NONE -> _raise.update {
                it.copy(position = null, placeLabel = "", locating = false)
            }
        }
    }

    /**
     * Asks the phone where it is, and then asks the village what that place is
     * called.
     *
     * Both halves matter. Coordinates are what an ambulance needs; "Οδός
     * Ελατιάς, Κέντρο" is what a neighbour reading the alert needs, and neither
     * substitutes for the other.
     */
    fun locate() {
        if (_raise.value.place != AlertPlace.HERE) return
        _raise.update { it.copy(locating = true) }
        viewModelScope.launch {
            val point = location.current()
            val label = point?.let { describe(it) }.orEmpty()
            _raise.update {
                if (it.place != AlertPlace.HERE) {
                    it
                } else {
                    it.copy(locating = false, position = point, placeLabel = label)
                }
            }
        }
    }

    private fun useHome() {
        val home = session.home.value
        _raise.update {
            it.copy(
                locating = false,
                position = home?.position,
                placeLabel = home?.place.orEmpty(),
            )
        }
    }

    private suspend fun describe(point: GeoPoint): String {
        val place = runCatching { placeNamer.describe(point) }.getOrNull() ?: return ""
        return listOfNotNull(place.streetName, place.block?.nameEl).joinToString(", ")
    }

    fun send() {
        val current = _raise.value
        val kind = current.kind
        val author = profile
        if (kind == null || author == null) {
            // Never silent. This used to clear the error and return, so on the
            // one screen in the app that people reach while something is on
            // fire, tapping "tell the village" before the profile document had
            // loaded did nothing at all — no spinner, no message, no alert.
            _raise.update {
                it.copy(
                    errorMessage = messages.string(
                        if (kind == null) R.string.alert_invalid else R.string.error_signed_out,
                    ),
                )
            }
            return
        }
        _raise.update { it.copy(raising = true, errorMessage = null) }
        viewModelScope.launch {
            val result = alerts.raise(
                kind = kind,
                note = current.note,
                position = current.position,
                placeLabel = current.placeLabel,
                author = author,
            )
            _raise.update {
                it.copy(
                    raising = false,
                    raisedId = result.getOrNull(),
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    fun reset() {
        _raise.value = RaiseAlertState()
    }

    fun toggleConfirmed(alert: VillageAlert) {
        val me = profile ?: return
        viewModelScope.launch {
            alerts.setConfirmed(alert.id, me, confirmed = me.id !in alert.confirmedBy)
        }
    }

    /**
     * The card asks before it does this.
     *
     * Marking a village-wide outage over is one tap next to "I have it too",
     * and getting it wrong takes down the notice everybody else is relying on.
     */
    fun resolve(alert: VillageAlert) {
        val me = profile ?: return
        viewModelScope.launch { alerts.resolve(alert.id, me.id) }
    }
}
