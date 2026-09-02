package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.Announcement
import gr.agiosnektarios.village.core.model.Chat
import gr.agiosnektarios.village.core.model.ChatMessage
import gr.agiosnektarios.village.core.model.ChatType
import gr.agiosnektarios.village.core.model.DayForecast
import gr.agiosnektarios.village.core.model.HourForecast
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.model.Wind
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.announcements.AnnouncementsContent
import gr.agiosnektarios.village.ui.announcements.AnnouncementsUiState
import gr.agiosnektarios.village.ui.chat.ChatRoomContent
import gr.agiosnektarios.village.ui.chat.ChatRoomUiState
import gr.agiosnektarios.village.ui.chat.ChatsContent
import gr.agiosnektarios.village.ui.chat.ChatsUiState
import gr.agiosnektarios.village.ui.components.LocalClock
import gr.agiosnektarios.village.ui.profile.ProfileContent
import gr.agiosnektarios.village.ui.profile.ProfileUiState
import gr.agiosnektarios.village.ui.theme.VillageTheme
import gr.agiosnektarios.village.ui.weather.WeatherSheetContent
import gr.agiosnektarios.village.ui.weather.WeatherUiState
import java.util.Date
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The instant these goldens pretend it is; the same one the other files use. */
private const val UNLOOKED_NOW = 1_787_120_700_000L

/**
 * The screens four adversarial rounds never rendered.
 *
 * The notice board is the only screen in the app that an administrator writes
 * and every resident reads, and until now nothing had ever drawn it. The chat
 * and the weather sheet had goldens in one language at one text size, which is
 * the configuration nobody in this village is running: Greek words are longer
 * than English ones and the people this app is for turn the text up.
 */
class UnlookedTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val original: TimeZone = TimeZone.getDefault()

    @Before fun pinClock() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"))

    @After fun restoreClock() = TimeZone.setDefault(original)

    private val greek = DeviceConfig.PIXEL_5.copy(locale = "el")
    private val greekLarge = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el")
    private val greekMax = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el")

    private fun render(
        dark: Boolean = false,
        config: DeviceConfig? = null,
        name: String? = null,
        content: @Composable () -> Unit,
    ) {
        config?.let(paparazzi::unsafeUpdateConfig)
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(LocalClock provides { UNLOOKED_NOW }) {
                VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) { content() }
                }
            }
        }
    }

    // ---------------------------------------------------------- announcements

    @Composable
    private fun Announcements(isAdmin: Boolean = false) = AnnouncementsContent(
        state = AnnouncementsUiState(announcements = sampleNotices, loading = false),
        isAdmin = isAdmin,
        onEdit = {},
        onDelete = {},
    )

    @Test fun announcements_light() = render(config = greek, name = "notices_light") {
        Announcements()
    }

    @Test fun announcements_dark() = render(dark = true, config = greek, name = "notices_dark") {
        Announcements()
    }

    /** The row of edit/delete buttons only an administrator sees. */
    @Test fun announcements_admin() = render(config = greek, name = "notices_admin") {
        Announcements(isAdmin = true)
    }

    /**
     * Greek at twice the text, with the pin, the title and the two admin
     * buttons all sharing one row.
     */
    @Test fun announcements_greek_max() = render(config = greekMax, name = "notices_max") {
        Announcements(isAdmin = true)
    }

    @Test fun announcements_empty() = render(config = greek, name = "notices_empty") {
        AnnouncementsContent(
            state = AnnouncementsUiState(announcements = emptyList(), loading = false),
            isAdmin = false,
            onEdit = {},
            onDelete = {},
        )
    }

    // ------------------------------------------------------------------- chat

    @Composable
    private fun Room() = ChatRoomContent(
        state = ChatRoomUiState(
            chat = groupChat,
            messages = sampleTalk,
            currentUserId = "me",
            draft = "",
            loading = false,
        ),
        onBack = {},
        onDraftChange = {},
        onSend = {},
        onLeave = {},
    )

    /** A group thread in Greek: sender names, avatars and the clock in the bubble. */
    @Test fun chat_room_greek() = render(config = greek, name = "room_greek") { Room() }

    @Test fun chat_room_greek_max() = render(config = greekMax, name = "room_max") { Room() }

    @Test fun chats_greek_max() = render(config = greekMax, name = "chats_max") {
        ChatsContent(
            state = ChatsUiState(chats = sampleThreads, currentUserId = "me", loading = false),
            onOpenChat = {},
            onNewChat = {},
        )
    }

    // ---------------------------------------------------------------- roles

    /**
     * The role pill, which no golden had ever drawn because every profile
     * fixture in the suite is an ordinary resident.
     *
     * [gr.agiosnektarios.village.ui.components.TagPill] paints its label in the
     * tint it also uses, at 12% alpha, as its own fill — so the two are the
     * same hue at two densities and the label has to be read against it.
     */
    @Composable
    private fun AdminProfile() = ProfileContent(
        state = ProfileUiState(
            profile = admin,
            myIssues = emptyList(),
            loading = false,
        ),
        onOpenIssue = {},
        onEditProfile = {},
        onOpenSettings = {},
        onOpenAdmin = {},
        onSignOut = {},
    )

    @Test fun profile_admin_light() = render(config = greek, name = "role_pill_light") {
        AdminProfile()
    }

    @Test fun profile_admin_dark() = render(dark = true, config = greek, name = "role_pill_dark") {
        AdminProfile()
    }

    // -------------------------------------------------------------- weather

    /**
     * The half of the weather sheet the fire card pushes off the screen.
     *
     * `fire = null` is the honest state of a phone whose forecast arrived
     * without the history the assessment needs, and it is the only way to see
     * the six fact tiles and the day strip in one frame.
     */
    @Test fun weather_facts_greek_large() = render(config = greekLarge, name = "facts_large") {
        WeatherSheetContent(
            state = WeatherUiState(snapshot = august, fire = null, fireIsToday = true),
            onCallFireService = {},
            onOpenContacts = {},
            onOpenOfficialMap = {},
            onToggleMapWeather = {},
            onRefresh = {},
        )
    }

    @Test fun weather_facts_greek_max() = render(config = greekMax, name = "facts_max") {
        WeatherSheetContent(
            state = WeatherUiState(snapshot = august, fire = null, fireIsToday = true),
            onCallFireService = {},
            onOpenContacts = {},
            onOpenOfficialMap = {},
            onToggleMapWeather = {},
            onRefresh = {},
        )
    }
}

private val admin = UserProfile(
    id = "boss",
    firstName = "Δημήτρης",
    lastName = "Αναγνωστόπουλος",
    email = "d@example.gr",
    address = "Μαραθώνος 12",
    role = Role.ADMIN.id,
)

private fun ago(minutes: Long) = Date(UNLOOKED_NOW - minutes * 60_000L)

private val sampleNotices = listOf(
    Announcement(
        id = "a1",
        title = "Διακοπή νερού την Τρίτη",
        body = "Η ΕΥΔΑΠ θα κλείσει το δίκτυο από τις 08:00 ως τις 14:00 για " +
            "αντικατάσταση αγωγού στην κεντρική. Γεμίστε από πριν.",
        authorName = "Δημήτρης Αναγνωστόπουλος",
        pinned = true,
        createdAt = ago(60 * 3),
    ),
    Announcement(
        id = "a2",
        title = "Καθαρισμός δασικού δρόμου",
        body = "Το Σάββατο στις 08:00 στην πλατεία. Φέρτε γάντια και ψαλίδι.",
        authorName = "Μαρία Καραγιάννη",
        createdAt = ago(60 * 26),
    ),
    Announcement(
        id = "a3",
        title = "Γενική συνέλευση του οικοδομικού συνεταιρισμού",
        body = "Παρασκευή 28 Αυγούστου, 18:30, στο κοινοτικό κατάστημα.",
        authorName = "Νίκος Παπαδόπουλος",
        createdAt = ago(60 * 24 * 3),
    ),
)

private val groupChat = Chat(
    id = "chat2",
    type = ChatType.GROUP.id,
    title = "Πυροπροστασία",
    memberIds = listOf("me", "maria", "dimitris"),
    memberNames = mapOf("me" to "Γρηγόρης", "maria" to "Μαρία Κ.", "dimitris" to "Δημήτρης Α."),
    lastMessage = "Ραντεβού Σάββατο στις 9 στην πλατεία.",
    lastMessageSenderId = "dimitris",
    lastMessageAt = ago(30),
)

private val sampleThreads = listOf(
    Chat(
        id = "chat1",
        type = ChatType.DIRECT.id,
        memberIds = listOf("me", "maria"),
        memberNames = mapOf("me" to "Γρηγόρης", "maria" to "Μαρία Κ."),
        lastMessage = "Ναι, το είδα. Θα περάσω το απόγευμα.",
        lastMessageSenderId = "maria",
        lastMessageAt = ago(12),
        unreadCounts = mapOf("me" to 2),
    ),
    groupChat,
)

private val sampleTalk = listOf(
    ChatMessage(
        id = "m1", senderId = "maria", senderName = "Μαρία Κ.",
        text = "Είδες το δέντρο στην κάτω στροφή;", createdAt = ago(48),
    ),
    ChatMessage(
        id = "m2", senderId = "me", senderName = "Γρηγόρης",
        text = "Ναι, το ανέφερα στην εφαρμογή χθες βράδυ.", createdAt = ago(44),
    ),
    ChatMessage(
        id = "m3", senderId = "dimitris", senderName = "Δημήτρης Α.",
        text = "Έχω αλυσοπρίονο αν χρειαστεί, αλλά όχι πριν το Σάββατο το πρωί.",
        createdAt = ago(30),
    ),
)

private fun day(offset: Int, high: Double, low: Double, condition: WeatherCondition) =
    DayForecast(
        date = UNLOOKED_NOW + offset * 86_400_000L,
        high = high,
        low = low,
        condition = condition,
        precipitationMm = 0.0,
        maxWindKmh = 18.0,
        maxGustKmh = 31.0,
    )

private val august = WeatherSnapshot(
    observedAt = UNLOOKED_NOW,
    fetchedAt = UNLOOKED_NOW - (5 * 60_000L + 1_000L),
    temperature = 25.7,
    feelsLike = 26.8,
    humidity = 55,
    cloudCover = 0,
    precipitation = 0.0,
    rainSoFarMm = 0.0,
    snowDepthCm = 0.0,
    wind = Wind(6.8, 19.1, 360),
    condition = WeatherCondition.CLEAR,
    isDay = true,
    sunrise = 1_787_111_100_000L,
    sunset = 1_787_159_700_000L,
    dryDays = 25,
    peakToday = HourForecast(
        time = UNLOOKED_NOW,
        temperature = 30.1,
        humidity = 34,
        windKmh = 18.0,
        gustKmh = 31.0,
        windDirection = 360,
        precipitationMm = 0.0,
        precipitationChance = 0,
        condition = WeatherCondition.CLEAR,
    ),
    today = day(0, 30.1, 19.4, WeatherCondition.OVERCAST),
    days = listOf(
        day(0, 30.1, 19.4, WeatherCondition.OVERCAST),
        day(1, 31.4, 20.2, WeatherCondition.CLEAR),
        day(2, 29.8, 19.1, WeatherCondition.PARTLY_CLOUDY),
        day(3, 27.6, 18.4, WeatherCondition.RAIN),
    ),
    hours = emptyList(),
)
