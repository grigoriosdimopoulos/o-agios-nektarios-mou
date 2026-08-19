package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.DayForecast
import gr.agiosnektarios.village.core.model.HourForecast
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import gr.agiosnektarios.village.core.model.Wind
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.theme.VillageTheme
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.map.MapSheet
import gr.agiosnektarios.village.ui.weather.DateLine
import gr.agiosnektarios.village.ui.weather.WeatherChip
import gr.agiosnektarios.village.ui.weather.WeatherOverlay
import gr.agiosnektarios.village.ui.weather.WeatherSheetContent
import gr.agiosnektarios.village.ui.weather.WeatherUiState
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import gr.agiosnektarios.village.ui.components.LocalClock

/**
 * The instant every golden in this file pretends it is.
 *
 * Pinned through [LocalClock] so "3 hours ago" stays "3 hours ago" tomorrow.
 */
private const val GOLDEN_NOW = 1_787_120_700_000L

/**
 * The weather, rendered.
 *
 * Two things are being checked by eye here and neither can be asserted. The
 * first is that the sheet reads as information rather than as a dashboard —
 * the fire level is the loudest thing on it, and the caveat about the official
 * map is legible rather than buried. The second is the overlay: rain, snow and
 * wind are drawn code, and drawn code that is never looked at is decoration
 * with bugs in it.
 *
 * The clock is pinned to Athens for the duration. Every date and time on these
 * screens is formatted in the device's zone, so without this the goldens would
 * be a record of where the machine that recorded them happened to be.
 */
class WeatherTest {

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
            CompositionLocalProvider(LocalClock provides { GOLDEN_NOW }) {
                VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) { content() }
                }
            }
        }
    }

    /** For the store listing, in Greek. */
    @Test fun shot_weather() = render(
        config = DeviceConfig.PIXEL_5.copy(locale = "el"),
        name = "shot_weather",
    ) { Sheet(august) }

    @Test fun weather_sheet_light() = render { Sheet(august) }

    @Test fun weather_sheet_dark() = render(dark = true) { Sheet(august) }

    /** A wet January: the level is pinned low and the reasons say why. */
    @Test fun weather_sheet_winter() = render { Sheet(january) }

    /** Nothing has arrived yet — the state a phone with no signal opens in. */
    @Test fun weather_sheet_empty() = render {
        WeatherSheetContent(
            state = WeatherUiState(),
            onCallFireService = {},
            onOpenContacts = {},
            onOpenOfficialMap = {},
            onToggleMapWeather = {},
            onRefresh = {},
        )
    }

    /**
     * The day the whole feature exists for: a gale on a fortnight of drought.
     * Rendered in Greek at one and a half times the text, which is the
     * configuration this village mostly reads in.
     */
    @Test
    fun weather_sheet_alert_greek() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "alert",
    ) {
        Sheet(alert)
    }

    /**
     * The strip as it appears in the drawer's peek, which is where nearly
     * everybody will see the weather and where almost nobody will open the
     * sheet. Rendered on its own because the drawer needs a live map to exist.
     */
    @Test fun weather_chip_light() = render { Chip(august) }

    @Test fun weather_chip_dark() = render(dark = true) { Chip(august) }

    /** With the fire level raised, which is the only time the badge appears. */
    @Test
    fun weather_chip_alert_greek() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "chip_alert",
    ) {
        Chip(alert)
    }

    /**
     * The safety case: a reading the phone picked up days ago, restored from
     * disk after a spell with no signal. The level must still be shown — the
     * village would rather know Sunday was an alert day than know nothing —
     * but it has to say, in the app's error colour, that it is not today's.
     */
    @Test
    fun weather_sheet_not_today() = render {
        WeatherSheetContent(
            state = WeatherUiState(
                snapshot = alert,
                fire = FireRisk.assess(alert),
                stale = true,
                fireIsToday = false,
                fresh = false,
            ),
            onCallFireService = {},
            onOpenContacts = {},
            onOpenOfficialMap = {},
            onToggleMapWeather = {},
            onRefresh = {},
        )
    }

    /** And the badge that goes with it: no colour on the map for an old day. */
    @Test
    fun weather_chip_not_today() = render {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WeatherChip(
                state = WeatherUiState(
                    snapshot = alert,
                    fire = FireRisk.assess(alert),
                    fireIsToday = false,
                ),
                onClick = {},
            )
        }
    }

    /**
     * The strip in the place it actually lives: the drawer's peek, over the
     * map, with the report count and the date beside it. This is the whole of
     * what "somewhere at the bottom" turned out to mean, and until it was
     * rendered together nobody had seen whether the three fit on one line.
     */
    @Test
    fun map_sheet_with_weather() = render {
        MapSheet(
            issues = sampleIssues,
            onOpenIssue = {},
            subtitle = { DateLine(august.observedAt) },
            trailing = {
                WeatherChip(
                    state = WeatherUiState(
                        snapshot = august,
                        fire = FireRisk.assess(august),
                        fireIsToday = true,
                        fresh = true,
                    ),
                    onClick = {},
                )
            },
        )
    }

    /** And in Greek at one and a half times the text, where it has to wrap. */
    @Test
    fun map_sheet_with_weather_greek() = render(
        config = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f, locale = "el"),
        name = "sheet_greek",
    ) {
        MapSheet(
            issues = sampleIssues,
            onOpenIssue = {},
            subtitle = { DateLine(august.observedAt) },
            trailing = {
                WeatherChip(
                    state = WeatherUiState(
                        snapshot = alert,
                        fire = FireRisk.assess(alert),
                        fireIsToday = true,
                        fresh = true,
                    ),
                    onClick = {},
                )
            },
        )
    }

    @Test
    fun overlay_rain() = render {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherOverlay(snapshot = raining, phase = 0.35f, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun overlay_snow() = render {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherOverlay(snapshot = snowing, phase = 0.35f, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun overlay_wind() = render {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherOverlay(snapshot = alert, phase = 0.35f, modifier = Modifier.fillMaxSize())
        }
    }

    /**
     * An ordinary breeze, which is what the village mostly gets.
     *
     * The layer used to start at 3 Beaufort and this frame was therefore
     * blank — and since 72% of hours here are below that, "blank" was the
     * normal appearance of a feature somebody had switched on to watch. It has
     * to show something at 2 Bft without shouting.
     */
    @Test
    fun overlay_light_breeze() = render {
        Box(modifier = Modifier.fillMaxSize()) {
            WeatherOverlay(
                snapshot = august.copy(wind = Wind(9.0, 17.0, 45)),
                phase = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    private fun Chip(snapshot: WeatherSnapshot) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WeatherChip(
                state = WeatherUiState(
                    snapshot = snapshot,
                    fire = FireRisk.assess(snapshot),
                    fireIsToday = true,
                    fresh = true,
                ),
                onClick = {},
            )
        }
    }

    @Composable
    private fun Sheet(snapshot: WeatherSnapshot) {
        WeatherSheetContent(
            state = WeatherUiState(
                snapshot = snapshot,
                fire = FireRisk.assess(snapshot),
                animateOnMap = true,
                fireIsToday = true,
                fresh = true,
            ),
            onCallFireService = {},
            onOpenContacts = {},
            onOpenOfficialMap = {},
            onToggleMapWeather = {},
            onRefresh = {},
        )
    }
}

// ------------------------------------------------------------------ fixtures
//
// The August one is the real 19 August 2026 forecast for this village, taken
// from the same response the parser is tested against, so what these goldens
// show is a day that actually happened here rather than a day invented to make
// the layout look good.

private const val AUGUST_OBSERVED = 1_787_122_800_000L // 2026-08-19 10:00 +03:00
private const val AUGUST_PEAK = 1_787_140_800_000L // 15:00
private const val AUGUST_MIDNIGHT = 1_787_086_800_000L

private fun day(offset: Int, high: Double, low: Double, condition: WeatherCondition) = DayForecast(
    date = AUGUST_MIDNIGHT + offset * 86_400_000L,
    high = high,
    low = low,
    precipitationMm = if (condition.isWet) 4.2 else 0.0,
    maxWindKmh = 13.5,
    maxGustKmh = 31.3,
    condition = condition,
)

private val peakHour = HourForecast(
    time = AUGUST_PEAK,
    temperature = 30.1,
    humidity = 34,
    windKmh = 8.8,
    gustKmh = 29.5,
    windDirection = 36,
    precipitationMm = 0.0,
    precipitationChance = 0,
    condition = WeatherCondition.PARTLY_CLOUDY,
)

private val august = WeatherSnapshot(
    observedAt = AUGUST_OBSERVED,
    // Five minutes and a second back, rather than a fixed instant: the footer
    // prints "taken N minutes ago" against the wall clock, so a constant here
    // would make the golden go stale within the hour. The extra second keeps
    // the floor a whole minute away from flipping to four.
    fetchedAt = GOLDEN_NOW - (5 * 60_000L + 1_000L),
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
    peakToday = peakHour,
    today = day(0, 30.1, 19.4, WeatherCondition.OVERCAST),
    days = listOf(
        day(0, 30.1, 19.4, WeatherCondition.OVERCAST),
        day(1, 31.4, 20.2, WeatherCondition.CLEAR),
        day(2, 29.8, 19.1, WeatherCondition.PARTLY_CLOUDY),
        day(3, 27.6, 18.4, WeatherCondition.RAIN),
    ),
    hours = emptyList(),
)

/** The one that matters: gale-force gusts on a month without rain. */
private val alert = august.copy(
    peakToday = peakHour.copy(temperature = 36.2, humidity = 16, windKmh = 34.0, gustKmh = 71.0),
    temperature = 33.8,
    feelsLike = 35.9,
    humidity = 21,
    wind = Wind(34.0, 71.0, 20),
    dryDays = 31,
    dryDaysAtLeast = true,
)

private val raining = august.copy(
    condition = WeatherCondition.HEAVY_RAIN,
    precipitation = 6.4,
    cloudCover = 96,
    wind = Wind(24.0, 44.0, 210),
)

private val snowing = august.copy(
    observedAt = 1_768_000_000_000L,
    condition = WeatherCondition.SNOW,
    temperature = -1.4,
    // Overcast, because it does not snow out of a clear sky and the light wash
    // is driven from the cloud cover: with the August fixture's zero the snow
    // frame was rendering under a summer sun.
    cloudCover = 100,
    snowDepthCm = 9.0,
    wind = Wind(18.0, 33.0, 350),
    isDay = true,
)

private val january = august.copy(
    observedAt = 1_768_000_000_000L,
    // Real January times for this latitude, not August's carried over: a
    // golden is documentation, and one showing a quarter past eight sunset in
    // January is documentation of nothing.
    sunrise = 1_768_026_600_000L,
    sunset = 1_768_060_800_000L,
    peakToday = peakHour.copy(time = 1_768_020_000_000L, temperature = 9.2, humidity = 88),
    temperature = 7.1,
    feelsLike = 4.0,
    humidity = 91,
    cloudCover = 100,
    precipitation = 1.8,
    rainSoFarMm = 3.6,
    condition = WeatherCondition.RAIN,
    wind = Wind(12.0, 26.0, 200),
    dryDays = 0,
    dryDaysAtLeast = false,
    today = day(0, 9.2, 3.1, WeatherCondition.RAIN),
    days = listOf(
        day(0, 9.2, 3.1, WeatherCondition.RAIN),
        day(1, 8.4, 2.2, WeatherCondition.SNOW),
    ),
)
