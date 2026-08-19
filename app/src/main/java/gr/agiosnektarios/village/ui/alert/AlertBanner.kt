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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
    val pulse by rememberInfiniteTransition(label = "emergency").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "emergencyPulse",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error)
            .clickable { onOpen(emergency) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = emergency.alertKind.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(24.dp).alpha(pulse),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emergency.alertKind.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
            )
            Text(
                text = listOfNotNull(
                    emergency.placeLabel.takeIf { it.isNotBlank() },
                    emergency.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { emergency.raisedByName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onError,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(raisedContainer)
            .then(raisedOutline?.let { Modifier.border(it, RoundedCornerShape(16.dp)) } ?: Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = alert.alertKind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
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
                color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onResolve)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
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
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp),
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
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
                MaterialTheme.colorScheme.primary
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
