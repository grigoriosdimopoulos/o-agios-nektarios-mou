package gr.agiosnektarios.village.ui.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.EventKind
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageEvent
import gr.agiosnektarios.village.data.event.EventRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.data.settings.FeatureRepository

data class CalendarUiState(
    val events: List<VillageEvent> = emptyList(),
    val userId: String = "",
    val canModerate: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    featureRepository: FeatureRepository,
    private val repository: EventRepository,
    private val session: SessionRepository,
) : ViewModel() {

    /** What the village uses, for the segmented control above this. */
    val features: StateFlow<FeatureFlags> = featureRepository.flags

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.observeUpcoming(),
        session.state.map { it as? SessionState.SignedIn },
    ) { events, signedIn ->
        CalendarUiState(
            events = events,
            userId = signedIn?.profile?.id.orEmpty(),
            canModerate = signedIn?.profile?.canModerate == true,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /**
     * Says you are coming, or takes it back.
     *
     * Deliberately not optimistic. Firestore's offline cache applies the write
     * locally and the listener re-emits within a frame or two, so the row
     * updates just as fast as a hand-rolled optimistic copy would — and
     * without a second source of truth that can disagree with the server when
     * the rules turn the write down.
     */
    fun toggleAttendance(event: VillageEvent) {
        val profile = (session.state.value as? SessionState.SignedIn)?.profile ?: return
        viewModelScope.launch {
            repository.setAttending(
                eventId = event.id,
                user = profile,
                attending = !event.isAttending(profile.id),
            )
        }
    }

    fun delete(eventId: String) {
        viewModelScope.launch { repository.delete(eventId) }
    }
}

data class EventComposeUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val place: String = "",
    val kind: EventKind = EventKind.OTHER,
    /** Local midnight of the chosen day. */
    val date: Long = startOfToday(),
    /** Minutes past midnight, ignored when [allDay]. */
    val minuteOfDay: Int = DEFAULT_MINUTE,
    val allDay: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val invalid: Boolean = false,
) {
    val startAt: Long
        get() = if (allDay) date else date.atMinuteOfDay(minuteOfDay)

    val canSubmit: Boolean get() = title.isNotBlank() && !saving
}

@HiltViewModel
class EventComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EventRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId").orEmpty()

    private val _uiState = MutableStateFlow(EventComposeUiState(isEditing = eventId.isNotBlank()))
    val uiState: StateFlow<EventComposeUiState> = _uiState.asStateFlow()

    init {
        if (eventId.isNotBlank()) {
            viewModelScope.launch {
                val existing = repository.observeEvent(eventId).first() ?: return@launch
                val calendar = Calendar.getInstance().apply { timeInMillis = existing.start }
                val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                _uiState.update {
                    it.copy(
                        title = existing.title,
                        description = existing.description,
                        place = existing.place,
                        kind = existing.eventKind,
                        date = existing.start.startOfLocalDay(),
                        minuteOfDay = minute,
                        allDay = existing.allDay,
                    )
                }
            }
        }
    }

    fun onTitle(value: String) = _uiState.update { it.copy(title = value, invalid = false) }

    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun onPlace(value: String) = _uiState.update { it.copy(place = value) }

    fun onKind(kind: EventKind) = _uiState.update { it.copy(kind = kind) }

    fun onDate(millis: Long) = _uiState.update { it.copy(date = millis, invalid = false) }

    fun onTime(minuteOfDay: Int) = _uiState.update { it.copy(minuteOfDay = minuteOfDay) }

    fun onAllDay(allDay: Boolean) = _uiState.update { it.copy(allDay = allDay) }

    fun save() {
        val current = _uiState.value
        if (!current.canSubmit) {
            _uiState.update { it.copy(invalid = true) }
            return
        }
        val author: UserProfile =
            (session.state.value as? SessionState.SignedIn)?.profile ?: return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.save(
                id = eventId.ifBlank { null },
                title = current.title,
                description = current.description,
                place = current.place,
                kind = current.kind,
                startAt = current.startAt,
                endAt = null,
                allDay = current.allDay,
                author = author,
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(saving = false, saved = true)
                } else {
                    it.copy(saving = false, invalid = true)
                }
            }
        }
    }
}

/**
 * Turning days into instants, and back.
 *
 * The date picker deals in days and an event stores an instant, so something
 * has to bridge the two. Doing it with [Calendar] rather than arithmetic on the
 * epoch is what keeps a day from being 23 or 25 hours long twice a year: adding
 * ten hours' worth of milliseconds to midnight on the last Sunday in March
 * gives eleven o'clock, and a liturgy an hour late is exactly the sort of small
 * wrongness nobody reports and everybody notices.
 */
internal fun startOfToday(): Long = System.currentTimeMillis().startOfLocalDay()

internal fun Long.startOfLocalDay(): Long = Calendar.getInstance().apply {
    timeInMillis = this@startOfLocalDay
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** [this] is a local midnight; the result is that day at the given wall clock. */
internal fun Long.atMinuteOfDay(minuteOfDay: Int): Long = Calendar.getInstance().apply {
    timeInMillis = this@atMinuteOfDay
    set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
    set(Calendar.MINUTE, minuteOfDay % 60)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Material's date picker answers in UTC midnight, whatever the device's zone.
 *
 * Handing that straight to [Long.startOfLocalDay] is how a picker set to the
 * fifth becomes an event on the fourth for everyone west of Greenwich and, in
 * Greece, an event three hours before the day it was picked. The fields are
 * therefore read back out in UTC and a fresh local day is built from them.
 */
internal fun localDayFromPickerMillis(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnight
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Ten in the morning: the hour most village things actually start. */
private const val DEFAULT_MINUTE = 10 * 60
