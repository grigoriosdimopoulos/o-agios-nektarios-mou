package gr.agiosnektarios.village.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.ui.theme.raisedContainer

/**
 * The weather in the width of a thumb.
 *
 * This lives in the map drawer's peek strip, which is the one piece of the map
 * that is always on screen — the village asked for the numbers to be "somewhere
 * at the bottom", and the drawer header is the bottom of the map that is not
 * already the navigation bar, the button or the sheet's own contents.
 *
 * It carries four things and no more: what the sky is doing, how warm it is,
 * where the wind is going and — only when it is worth saying — that the fire
 * risk is up. Everything else is one tap away in [WeatherSheet], because a
 * strip that tried to hold humidity and sunset too would be a strip nobody
 * could read at a glance, which is the only way this one is ever read.
 */
@Composable
fun WeatherChip(
    state: WeatherUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot ?: return
    val fire = state.fire
    val windLabel = stringResource(R.string.weather_beaufort, snapshot.wind.beaufort)
    val sector = snapshot.wind.sectorLabel()
    val conditionLabel = snapshot.condition.label()
    // Shown at every level, not only the alarming ones.
    //
    // It used to appear from "high" upward, which meant that on the ordinary
    // days — half of them — somebody opening the map to ask "can I burn these
    // cuttings" found nothing at all and had to open the sheet to be told.
    // A fire indicator that is absent whenever the answer is reassuring is an
    // indicator you cannot trust the absence of.
    val shownFire = fire?.takeIf { state.fireIsToday }
    // Spelled out for a screen reader, where the colour and the flame that
    // qualify the pill visually are not available at all.
    val fireLabel = shownFire?.let {
        stringResource(R.string.fire_risk_spoken, it.level.label())
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            // One description for the whole chip. Read out piece by piece it
            // is "cloud, 24 degrees, north, 3" — four fragments that mean
            // nothing in a row.
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(conditionLabel)
                    append(", ").append(snapshot.temperature.asDegrees())
                    append(", ").append(windLabel).append(' ').append(sector)
                    if (fireLabel != null) append(", ").append(fireLabel)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = snapshot.condition.icon(snapshot.isDay),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = snapshot.temperature.asDegrees(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.North,
                contentDescription = null,
                // Pointing where the wind is *going*, which is half a turn
                // from the direction the forecast reports.
                modifier = Modifier.size(14.dp).rotate(snapshot.wind.arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = windLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Silent only on a reading that is not today's: a colour carried over
        // from a cached reading taken while the phone was out of signal is a
        // claim about right now with no date attached to it. The level is
        // still in the sheet either way, where it can say which day it is for.
        if (shownFire != null) {
            FireLevelPill(level = shownFire.level)
        }
    }
}

/**
 * The level, in the colours of the official scale, with the app's own mark on
 * it.
 *
 * The colours and the wording are deliberately the ones the village already
 * hears on the evening news — a second vocabulary for one thing would help
 * nobody. But a purple pill reading "Συναγερμός", sitting on the map with no
 * qualifier, is a reproduction of a badge that carries legal force, and this
 * one is computed on a phone. The flame is what separates them: it is the
 * app's mark, it is not on the official scale, and it is on every level rather
 * than only the alarming ones so that it reads as a label rather than as part
 * of the warning.
 */
@Composable
fun FireLevelPill(
    level: FireRisk.Level,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(level.color())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = level.onColor(),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = level.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = level.onColor(),
        )
    }
}

/**
 * The date, under the drawer's report count.
 *
 * Small, quiet, and there because a screen that opens on a map of your own
 * village is a screen you look at without a reason, and knowing what day it is
 * turns out to be one of the things people want from such a screen.
 */
@Composable
fun DateLine(millis: Long, modifier: Modifier = Modifier) {
    Text(
        text = longDate(millis).replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Kept here so the strip and the sheet cannot disagree about the surface. */
@Composable
internal fun FactTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(raisedContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
