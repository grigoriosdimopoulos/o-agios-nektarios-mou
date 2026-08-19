package gr.agiosnektarios.village.ui.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.WeatherCondition
import gr.agiosnektarios.village.core.model.Wind
import gr.agiosnektarios.village.core.weather.FireRisk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How the weather is written and drawn.
 *
 * Everything here is one-way — value in, something showable out — and nothing
 * here decides anything. Kept apart from the screens because the same
 * temperature has to read identically in the strip, in the sheet and in the
 * forecast row, and three call sites each rounding it their own way is how
 * "23°" and "24°" end up on screen at once.
 */

@Composable
@ReadOnlyComposable
fun WeatherCondition.label(): String = stringResource(
    when (this) {
        WeatherCondition.CLEAR -> R.string.condition_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.condition_partly_cloudy
        WeatherCondition.OVERCAST -> R.string.condition_overcast
        WeatherCondition.FOG -> R.string.condition_fog
        WeatherCondition.DRIZZLE -> R.string.condition_drizzle
        WeatherCondition.RAIN -> R.string.condition_rain
        WeatherCondition.HEAVY_RAIN -> R.string.condition_heavy_rain
        WeatherCondition.SNOW -> R.string.condition_snow
        WeatherCondition.THUNDERSTORM -> R.string.condition_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.condition_unknown
    },
)

/**
 * [isDay] only changes the clear-sky icon.
 *
 * A sun at eleven at night is the small wrongness that makes a weather display
 * look like it is not really connected to anything. Rain at night is still
 * rain, so nothing else needs a second form.
 */
fun WeatherCondition.icon(isDay: Boolean = true): ImageVector = when (this) {
    WeatherCondition.CLEAR -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.Nightlight
    WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.FilterDrama
    WeatherCondition.OVERCAST -> Icons.Filled.Cloud
    WeatherCondition.FOG -> Icons.Filled.BlurOn
    WeatherCondition.DRIZZLE -> Icons.Filled.Grain
    WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN -> Icons.Filled.WaterDrop
    WeatherCondition.SNOW -> Icons.Filled.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Filled.Thunderstorm
    WeatherCondition.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

/** Whole degrees. Nobody in a village wants a tenth of a degree. */
fun Double.asDegrees(): String = "${roundToInt()}°"

fun Double.asMillimetres(): String =
    if (this >= 10.0) roundToInt().toString() else String.format(Locale.US, "%.1f", this)

@Composable
@ReadOnlyComposable
fun Wind.sectorLabel(): String = stringResource(
    when (sector) {
        0 -> R.string.wind_n
        1 -> R.string.wind_ne
        2 -> R.string.wind_e
        3 -> R.string.wind_se
        4 -> R.string.wind_s
        5 -> R.string.wind_sw
        6 -> R.string.wind_w
        else -> R.string.wind_nw
    },
)

/**
 * Which way to point an arrow that means "the wind is going that way".
 *
 * [Wind.directionDegrees] is meteorological and says where the wind comes
 * *from*, so an arrow drawn at that bearing points backwards. Adding half a
 * turn is the whole fix, and getting it wrong on a mountain in August is the
 * kind of mistake that makes a safety feature dangerous. The icon used with
 * this must point north at zero rotation.
 */
val Wind.arrowRotation: Float get() = (directionDegrees + 180).toFloat() % 360f

@Composable
@ReadOnlyComposable
fun FireRisk.Level.label(): String = stringResource(
    when (this) {
        FireRisk.Level.LOW -> R.string.fire_level_low
        FireRisk.Level.MODERATE -> R.string.fire_level_moderate
        FireRisk.Level.HIGH -> R.string.fire_level_high
        FireRisk.Level.VERY_HIGH -> R.string.fire_level_very_high
        FireRisk.Level.EXTREME -> R.string.fire_level_extreme
    },
)

/**
 * The five levels, in the colours the official scale uses.
 *
 * Green, yellow, orange, red, purple — the same ramp the Civil Protection map
 * is printed in, and the same one the evening news shows. Inventing a palette
 * would mean the village learning a second colour language for one thing.
 *
 * These are fixed rather than drawn from the theme on purpose: a fire level is
 * not decoration that should restyle itself, and "red" has to be red in both
 * themes. They are darkened for the light theme so text on them still clears
 * contrast, which a raw traffic-light yellow does not.
 */
@Composable
@ReadOnlyComposable
fun FireRisk.Level.color(): Color {
    val dark = MaterialTheme.colorScheme.surface.let {
        (0.299f * it.red + 0.587f * it.green + 0.114f * it.blue) < 0.5f
    }
    return when (this) {
        FireRisk.Level.LOW -> if (dark) Color(0xFF57A05B) else Color(0xFF2E7D32)
        FireRisk.Level.MODERATE -> if (dark) Color(0xFFD9C04A) else Color(0xFF8A6D0B)
        FireRisk.Level.HIGH -> if (dark) Color(0xFFE39B3F) else Color(0xFFB35309)
        FireRisk.Level.VERY_HIGH -> if (dark) Color(0xFFE05B4A) else Color(0xFFC62828)
        FireRisk.Level.EXTREME -> if (dark) Color(0xFFB07BD6) else Color(0xFF6A1B9A)
    }
}

/**
 * Ink that can actually be read on [color].
 *
 * Not a constant. The first version of this returned white for all ten
 * combinations with a comment claiming that had been checked; it had not, and
 * white on the dark theme's moderate yellow measures 1.81:1 — less than half
 * the 4.5:1 a label needs. Chosen from the fill's own luminance, every one of
 * the ten pairings clears 4.9:1.
 */
@Composable
@ReadOnlyComposable
fun FireRisk.Level.onColor(): Color {
    val fill = color()
    val luminance = 0.299f * fill.red + 0.587f * fill.green + 0.114f * fill.blue
    return if (luminance < 0.5f) Color.White else Color.Black
}

// ------------------------------------------------------------------- dates

@Composable
fun rememberLocale(): Locale = LocalConfiguration.current.locales[0]

/** "Wednesday 19 August", in the app's language. */
@Composable
fun longDate(millis: Long): String =
    SimpleDateFormat("EEEE d MMMM", rememberLocale()).format(Date(millis))

/** "Wed 21", for a forecast column. */
@Composable
fun shortDay(millis: Long): String =
    SimpleDateFormat("EEE d", rememberLocale()).format(Date(millis))

/**
 * A time of day, always on the 24-hour clock.
 *
 * Not the locale's short format, which for `el` is 12-hour: a resident picked
 * 19:00 in the event form — where the picker is 24-hour — and the card printed
 * "7:00 μ.μ.", and the weather sheet said the day peaked at "3:00 μ.μ." Greece
 * writes times as 19:00, and one feature should not use two clocks.
 */
@Composable
fun clock(millis: Long): String =
    SimpleDateFormat("HH:mm", rememberLocale()).format(Date(millis))
