package gr.agiosnektarios.village.ui.alert

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.AlertSeverity
import gr.agiosnektarios.village.core.model.VillageAlert
import gr.agiosnektarios.village.ui.components.relativeTime
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedOutline
import gr.agiosnektarios.village.ui.theme.primaryInk
import androidx.compose.material3.minimumInteractiveComponentSize
import gr.agiosnektarios.village.ui.theme.reducedMotion

/**
 * An emergency, across the top of the map, impossible to mistake for anything
 * else.
 *
 * The one place in this app where motion is used to demand attention rather
 * than to explain a change. Everywhere else that would be a fault; here it is
 * the point, and it is why nothing else in the app pulses — a signal that is
 * everywhere is not a signal.
 */
@Composable
fun EmergencyBanner(
    alerts: List<VillageAlert>,
    onOpen: (VillageAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emergency = alerts.firstOrNull { it.alertKind.severity == AlertSeverity.EMERGENCY } ?: return
    val still = reducedMotion()
    val animated by rememberInfiniteTransition(label = "emergency").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "emergencyPulse",
    )
    // Full opacity when animations are off, not the dimmest point of a pulse
    // that is no longer happening. The low end is 0.72, which against the
    // darkened alarm red measures 3.51:1 — above the 3:1 a meaningful graphic
    // needs, where against the old #D64545 it was roughly 3.2:1 for half of
    // every cycle, on the first thing anybody identifies this banner by.
    val pulse = if (still) 1f else animated

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error)
            .clickable { onOpen(emergency) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = emergency.alertKind.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            // The one icon in the app that grows with the text. Everywhere
            // else a fixed 24dp beside larger type is only slightly odd; here
            // the flame is how someone identifies the banner before reading a
            // word of it, and at twice the text size a fixed icon shrinks to a
            // speck next to the heading it belongs to.
            modifier = Modifier
                .size(24.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f))
                .alpha(pulse),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emergency.alertKind.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
            )
            // Place and note on separate lines rather than joined by a dot.
            // Joined, a street name plus a neighbourhood filled both permitted
            // lines at large text and the actual message — the thing that says
            // what is burning and which way it is going — was cut off after
            // three words.
            val place = emergency.placeLabel.takeIf { it.isNotBlank() }
            val note = emergency.note.takeIf { it.isNotBlank() }
            if (place != null) {
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onError,
                    // Two lines, not one. On a fire banner the street is the
                    // load-bearing word, and "Οδός Ελατιάς, Άνω γ…" is the
                    // half of it that does not help anybody.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note != null || place == null) {
                Text(
                    text = note ?: emergency.raisedByName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A power cut, a dry tap, a blocked road — and how many houses have it.
 *
 * The count is the whole content. One house with no water has a broken pipe;
 * six have a broken main, and the sixth only found out because the fifth said
 * so. That is the entire reason this exists rather than a group message.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutageCard(
    alert: VillageAlert,
    userId: String,
    canResolve: Boolean,
    onConfirm: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine = userId.isNotBlank() && userId in alert.confirmedBy
    val haptics = rememberHaptics()
    var confirmingOver by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(raisedContainer)
            .then(raisedOutline?.let { Modifier.border(it, RoundedCornerShape(16.dp)) } ?: Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top, not centre. With a long name at large text the second line
        // wraps to three, and centring floated the icon down beside the
        // timestamp while the heading it belongs to sat alone at the top.
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = alert.alertKind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primaryInk,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.alertKind.label(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.alert_raised_by,
                        alert.raisedByName,
                        relativeTime(alert.raisedAt),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // At twice the text "Αναγνωστόπουλος" broke inside the
                    // word across four lines. Two lines and an ellipsis: the
                    // headline of this card is the household count, and a
                    // surname cut cleanly identifies its author better than
                    // one cut in half.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.alert_confirmed,
                    alert.confirmedBy.size,
                    alert.confirmedBy.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primaryInk,
                // The count took whatever width it wanted and squeezed the
                // column beside it until "Αναγνωστόπουλος" hyphen-broke inside
                // the word, across four lines. It is at most "12 σπίτια".
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (alert.note.isNotBlank()) {
            Text(
                text = alert.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfirmPill(confirmed = mine, onClick = { haptics.tick(); onConfirm() })
            if (canResolve) {
                Text(
                    text = stringResource(R.string.alert_resolve),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primaryInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmingOver = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .minimumInteractiveComponentSize(),
                )
            }
        }
    }

    // Asked, not assumed. "It is over" sits one tap from "I have it too", and
    // getting it wrong takes down the notice the rest of the village is
    // relying on to know the water is still off.
    if (confirmingOver) {
        AlertDialog(
            onDismissRequest = { confirmingOver = false },
            title = { Text(alert.alertKind.label()) },
            text = { Text(stringResource(R.string.alert_resolve_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingOver = false; onResolve() }) {
                    Text(stringResource(R.string.alert_resolve))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingOver = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConfirmPill(confirmed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (confirmed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .then(
                if (confirmed) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primaryInk,
                        RoundedCornerShape(12.dp),
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (confirmed) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = stringResource(
                if (confirmed) R.string.alert_confirm_have_done else R.string.alert_confirm_have,
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (confirmed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primaryInk
            },
        )
    }
}

/**
 * The way in, on the screen the app opens on.
 *
 * Small, because it is not the app's subject; red and permanent, because the
 * moment it is wanted is not a moment for looking through a menu. It sits
 * opposite the map's layer controls so the two never fight for the same corner.
 */
@Composable
fun UrgentButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.alert_button),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
