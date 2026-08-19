package gr.agiosnektarios.village.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import gr.agiosnektarios.village.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Where "now" comes from.
 *
 * Everything below reads the clock through this rather than calling
 * [System.currentTimeMillis] directly, so a snapshot test can pin it. Without
 * it, any golden showing "3 hours ago" says something different tomorrow, and
 * the only way to keep such a golden green is to never render a recent time —
 * which is exactly the case that needs looking at, because it is the one the
 * strings are longest in.
 */
val LocalClock = staticCompositionLocalOf<() -> Long> { { System.currentTimeMillis() } }

/**
 * "just now" / "12 min ago" / a real date once it stops being useful as an
 * interval. Reads the current configuration's locale so the absolute form is
 * formatted Greek-style when the app is in Greek.
 */
@Composable
fun relativeTime(date: Date?): String {
    if (date == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    val elapsed = LocalClock.current() - date.time
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)

    return when {
        // A clock skew between device and server can make a fresh document look
        // like it arrives from the future; show it as new rather than negative.
        elapsed < 0 || minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
        days < 7 -> stringResource(R.string.time_days_ago, days.toInt())
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
    }
}

@Composable
fun absoluteDateTime(date: Date?): String {
    if (date == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(date)
}

/** Clock time only — used inside chat bubbles where the day is a header. */
@Composable
fun timeOfDay(date: Date?): String {
    if (date == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
}
