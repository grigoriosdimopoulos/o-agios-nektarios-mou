package gr.agiosnektarios.village.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.geo.IssueCluster
import gr.agiosnektarios.village.core.model.BlockSummary
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.ui.theme.CounterTextStyle

/**
 * A single report's pin: the category emoji in a coloured disc.
 *
 * Open reports in the two most urgent categories pulse slowly. This is the one
 * place in the app where motion is used as information rather than decoration,
 * so it is restricted to exactly those cases — everything pulsing would mean
 * nothing pulsing.
 */
@Composable
fun IssuePin(
    category: IssueCategory,
    open: Boolean,
    modifier: Modifier = Modifier,
) {
    val urgent = open && (category == IssueCategory.DANGER || category == IssueCategory.FIRE_RISK)
    val transition = rememberInfiniteTransition(label = "pin")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (urgent) 1.16f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pinPulse",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.scale(pulse)) {
        if (urgent) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(category.tint.copy(alpha = 0.22f)),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (open) category.tint else category.tint.copy(alpha = 0.45f))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = category.emoji, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A group of reports at one spot: the emoji plus how many.
 *
 * Same-category clusters keep the category colour so the map still reads at a
 * glance; mixed clusters (only possible when zoomed out) fall back to the
 * primary colour with a generic pin glyph.
 */
@Composable
fun ClusterPin(
    cluster: IssueCluster,
    modifier: Modifier = Modifier,
) {
    val tint = cluster.category?.tint ?: MaterialTheme.colorScheme.primary
    val glyph = cluster.category?.emoji ?: "📍"

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = glyph, style = MaterialTheme.typography.labelMedium)
        Text(
            text = cluster.size.toString(),
            style = CounterTextStyle,
            color = Color.White,
        )
    }
}

/**
 * The badge floating over a neighbourhood: how many reports are open inside it.
 *
 * Blocks with nothing open show a muted dot rather than "0" — an empty
 * neighbourhood should recede, not compete for attention with a busy one.
 */
@Composable
fun BlockBadge(
    summary: BlockSummary,
    label: String,
    modifier: Modifier = Modifier,
) {
    val hasOpen = summary.openCount > 0
    val tint = when {
        !hasOpen -> MaterialTheme.colorScheme.outline
        else -> summary.dominantCategory?.tint ?: MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f))
            .border(1.5.dp, tint, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (hasOpen) 20.dp else 8.dp)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            if (hasOpen) {
                Text(
                    text = if (summary.openCount > 99) "99+" else summary.openCount.toString(),
                    style = CounterTextStyle,
                    color = Color.White,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
