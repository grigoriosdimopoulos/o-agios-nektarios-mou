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
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.data.user.UserRepository
import gr.agiosnektarios.village.data.village.PlaceNamer
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    private val notifications: NotificationRepository,
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
            AlertPlace.HERE -> { homeJob?.cancel(); locate() }
            AlertPlace.HOME -> useHome()
            AlertPlace.NONE -> {
                homeJob?.cancel()
                _raise.update { it.copy(position = null, placeLabel = "", locating = false) }
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

    /**
     * Fills the place in from the resident's own house pin.
     *
     * Collected, not read once. `session.home` is Eagerly shared, which starts
     * the Firestore listener but does not make it deliver synchronously — so a
     * one-shot `.value` read on a cold start saw null, told the person their
     * house was not pinned, and then never corrected itself when the document
     * arrived a moment later. They typed a note and sent an ambulance alert
     * with no coordinates on it.
     *
     * The collection is cancelled the moment the place stops being HOME — by
     * [onPlace] and by [reset] — so choosing "where I am" afterwards is not
     * overwritten by a pin that arrives late.
     */
    private fun useHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            session.home.collect { home ->
                if (_raise.value.place != AlertPlace.HOME) return@collect
                _raise.update {
                    it.copy(
                        locating = false,
                        position = home?.position,
                        placeLabel = home?.place.orEmpty(),
                    )
                }
            }
        }
    }

    private var homeJob: Job? = null

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
            result.getOrNull()?.let { id ->
                announce(id, kind, current.placeLabel, author)
            }
        }
    }

    /**
     * Puts a notice in every resident's inbox.
     *
     * Without this the alarm reached only the phones with the app open at that
     * moment, while the screen raising it promised "a phone that is closed
     * sees it within about a quarter of an hour" — the sentence a person reads
     * when deciding whether the text message is also needed. The fan-out that
     * makes that true already existed and was being used for a notice-board
     * post; it was not being used for a fire.
     *
     * Fired after the alert is on disk, not before, and its failure is not
     * surfaced: the alert itself has succeeded by this point, and an error
     * about the notice would read as the alarm having failed.
     */
    private suspend fun announce(
        alertId: String,
        kind: AlertKind,
        placeLabel: String,
        author: UserProfile,
    ) {
        val recipients = notifications.allResidentIds().getOrNull().orEmpty()
        if (recipients.isEmpty()) return
        notifications.notify(
            recipientIds = recipients,
            actorId = author.id,
            type = NotificationType.ALERT,
            title = messages.string(kind.labelRes),
            bodyKey = if (placeLabel.isBlank()) "notif_alert_nowhere" else "notif_alert",
            bodyArg = placeLabel.ifBlank { author.displayName },
            // No deep link on purpose. The map is where an alert is shown —
            // banner across the top for an emergency, card above the reports
            // for an outage — and the map is where the app opens.
            //
            // Keyed on the alert, not on the kind. Two fires in one afternoon
            // are two fires, and collapsing the second onto the first would
            // replace the notice saying where the first one is.
            collapseKey = "ALERT:$alertId",
        )
    }

    fun reset() {
        homeJob?.cancel()
        _raise.value = RaiseAlertState()
    }

    /**
     * Failures on the cards, which have nowhere of their own to put a message.
     *
     * Firestore's local cache answers a write immediately, so a rejected
     * confirmation ticks the household count up and then drops it again a
     * second later with nothing said. On the number that is the entire content
     * of an outage report, that is worse than an error.
     */
    private val _cardErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val cardErrors: SharedFlow<String> = _cardErrors.asSharedFlow()

    fun toggleConfirmed(alert: VillageAlert) {
        val me = profile ?: return
        viewModelScope.launch {
            alerts.setConfirmed(alert.id, me, confirmed = me.id !in alert.confirmedBy)
                .onFailure { messages.of(it)?.let(_cardErrors::tryEmit) }
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
        viewModelScope.launch {
            alerts.resolve(alert.id, me.id)
                .onFailure { messages.of(it)?.let(_cardErrors::tryEmit) }
        }
    }
}
