package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.ContactKind
import gr.agiosnektarios.village.core.model.EventKind
import gr.agiosnektarios.village.core.model.VillageContact
import gr.agiosnektarios.village.core.model.VillageEvent
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.contacts.ContactsContent
import gr.agiosnektarios.village.ui.contacts.ContactsUiState
import gr.agiosnektarios.village.ui.events.CalendarContent
import gr.agiosnektarios.village.ui.events.CalendarUiState
import gr.agiosnektarios.village.ui.theme.VillageTheme
import java.util.Date
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The calendar and the telephone list, rendered.
 *
 * Every row on the calendar is written relative to now — "Today", "Tomorrow",
 * then a date — and whether an event is over decides whether it offers a
 * button. The first version built its fixtures from the wall clock to keep the
 * relative labels alive, which meant the goldens were right on the day they
 * were recorded and byte-different the next morning: the third and fourth rows
 * print absolute dates. Both the clock and the fixtures are fixed instead, and
 * [CalendarContent] takes the "now" it should reason from.
 */
class VillageTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val original: TimeZone = TimeZone.getDefault()

    @Before fun pinClock() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"))

    @After fun restoreClock() = TimeZone.setDefault(original)

    private fun render(
        dark: Boolean = false,
        config: DeviceConfig? = null,
        name: String? = null,
        content: @Composable () -> Unit,
    ) {
        config?.let(paparazzi::unsafeUpdateConfig)
        paparazzi.snapshot(name = name) {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Test fun calendar_light() = render { Calendar() }

    @Test fun calendar_dark() = render(dark = true) { Calendar() }

    @Test fun calendar_empty() = render {
        CalendarContent(
            state = CalendarUiState(events = emptyList(), loading = false),
            onAttend = {},
            onEdit = {},
            onDelete = {},
            now = CALENDAR_NOW,
        )
    }

    /**
     * Greek at one and a half times the text: the configuration where the
     * "I'll be there" button and the list of names have to share a row.
     */
    @Test
    fun calendar_greek_large() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "large",
    ) {
        Calendar()
    }

    @Test fun contacts_light() = render { Contacts() }

    @Test fun contacts_dark() = render(dark = true) { Contacts() }

    /** With nothing local filled in, which is how every village starts. */
    @Test fun contacts_bare() = render {
        ContactsContent(
            state = ContactsUiState(local = emptyList(), canEdit = true, loading = false),
            onBack = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onCall = {},
            onCopy = {},
        )
    }

    @Composable
    private fun Calendar() {
        CalendarContent(
            state = CalendarUiState(
                events = sampleEvents(),
                userId = "maria",
                canModerate = false,
                loading = false,
            ),
            onAttend = {},
            onEdit = {},
            onDelete = {},
            now = CALENDAR_NOW,
        )
    }

    @Composable
    private fun Contacts() {
        ContactsContent(
            state = ContactsUiState(local = sampleContacts, canEdit = false, loading = false),
            onBack = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onCall = {},
            onCopy = {},
        )
    }
}

/** Wednesday 19 August 2026, 09:30 in Athens — the instant the whole suite pretends it is. */
private const val CALENDAR_NOW = 1_787_120_700_000L

private fun at(daysFromNow: Int, hour: Int, minute: Int = 0): Date {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = CALENDAR_NOW
        add(java.util.Calendar.DAY_OF_YEAR, daysFromNow)
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.time
}

// Nothing here starts today at a fixed hour, and that is deliberate. The first
// version put the work day at eight this morning, which meant the golden showed
// the "I'll be there" button when recorded before eight and hid it when
// recorded after — a snapshot whose content depended on the time of day it was
// taken. Only the all-day event sits on today, because an all-day event is not
// past until its day is.
private fun sampleEvents(): List<VillageEvent> = listOf(
    VillageEvent(
        id = "3",
        title = "Απορριμματοφόρο",
        description = "Βγάλτε τους κάδους από το προηγούμενο βράδυ.",
        kind = EventKind.SERVICE.name,
        startAt = at(0, 7),
        allDay = true,
        authorId = "d",
        authorName = "Δημήτρης Αναγνωστόπουλος",
    ),
    VillageEvent(
        id = "1",
        title = "Καθαρισμός δασικού δρόμου προς το ρέμα",
        description = "Φέρτε γάντια και ψαλίδι. Θα υπάρχει νερό.",
        place = "Πλατεία, εκκίνηση",
        kind = EventKind.WORK_DAY.name,
        startAt = at(1, 8),
        attendees = mapOf(
            "maria" to "Μαρία Καραγιάννη",
            "d" to "Δημήτρης Αναγνωστόπουλος",
            "n" to "Νίκος Παπαδόπουλος",
        ),
        authorId = "d",
        authorName = "Δημήτρης Αναγνωστόπουλος",
    ),
    VillageEvent(
        id = "2",
        title = "Εσπερινός",
        place = "Άγιος Νεκτάριος",
        kind = EventKind.CHURCH.name,
        startAt = at(2, 19),
        authorId = "p",
        authorName = "Παπα-Θανάσης",
    ),
    VillageEvent(
        id = "4",
        title = "Γενική συνέλευση του οικοδομικού συνεταιρισμού",
        place = "Κοινοτικό κατάστημα",
        kind = EventKind.MEETING.name,
        startAt = at(9, 18, 30),
        authorId = "maria",
        authorName = "Μαρία Καραγιάννη",
    ),
)

// Real dialling codes with unassignable subscriber parts. These numbers appear
// in a golden image that anyone can open, and a plausible-looking number for a
// real surgery that nobody checked is the exact mistake the whole design of
// this feature is built to avoid.
private val sampleContacts = listOf(
    VillageContact(
        id = "a",
        name = "Αγροτικό Ιατρείο Βιλίων",
        number = "22630 00000",
        note = "Δευτέρα ως Παρασκευή, πρωί",
        kind = ContactKind.HEALTH.name,
    ),
    VillageContact(
        id = "b",
        name = "Δήμος Μάνδρας-Ειδυλλίας",
        number = "213 000 0000",
        note = "Τεχνική υπηρεσία",
        kind = ContactKind.LOCAL.name,
    ),
)
