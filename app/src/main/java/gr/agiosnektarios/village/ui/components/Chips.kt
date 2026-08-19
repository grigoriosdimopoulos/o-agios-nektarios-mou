package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.ui.theme.Motion
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import gr.agiosnektarios.village.ui.theme.controlOutline

/**
 * What a filter chip is, to a finger and to a screen reader.
 *
 * `toggleable` rather than `clickable`, so TalkBack announces "tick box,
 * ticked" and not an unlabelled tap target — twenty-one of these make up the
 * only filter controls in the app, and which ones are on was conveyed by a
 * half-density border and an alpha change of the same hue.
 *
 * The 48dp minimum lives on each chip's own modifier chain rather than here,
 * because it has to come *before* clip/background/border to reserve touch
 * rather than inflate the pill — see the note on [CategoryChip].
 */
private fun Modifier.filterToggle(selected: Boolean, onClick: () -> Unit): Modifier = this
    .toggleable(value = selected, role = Role.Checkbox) { onClick() }

/**
 * Category pill. Selected chips fill with the category's own colour rather than
 * a generic accent, which is what ties the filter row to the pins on the map.
 */
@Composable
fun CategoryChip(
    category: IssueCategory,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            category.tint.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = Motion.quick(),
        label = "categoryChipBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = Motion.snap(),
        label = "categoryChipScale",
    )

    Row(
        modifier = modifier
            // First in the chain. A layout modifier wraps everything after it,
            // so at the end of a chain this expands the content and then
            // clip/background/border draw over the expanded box — a 48dp pill
            // instead of a 32dp pill with 48dp of touch around it. Here it
            // reports the larger size to the parent and centres the chip.
            // The status chip measured 28dp tall and this one 32dp, which is a
            // comfortable target for nobody and a miss for the older hands
            // this village mostly has.
            .minimumInteractiveComponentSize()
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .border(
                BorderStroke(
                    // 1.5dp even when unselected. A 1dp hairline is under
                    // three pixels at this density and antialiasing never lets
                    // the middle of it reach full colour: the token measures
                    // 3.5:1 against the page, and the darkest pixel actually
                    // drawn measured 2.58:1. Selected goes to 2dp so the
                    // difference is still weight as well as hue.
                    width = if (selected) 2.dp else 1.5.dp,
                    color = if (selected) {
                        category.tint
                    } else {
                        MaterialTheme.colorScheme.controlOutline
                    },
                ),
                CircleShape,
            )
            .then(if (onClick != null) Modifier.filterToggle(selected, onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = category.emoji, style = MaterialTheme.typography.labelMedium)
        Text(
            text = stringResource(category.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Status pill with a leading dot, so status is readable without relying on colour alone. */
@Composable
fun StatusChip(
    status: IssueStatus,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val tint = status.tint
    val background by animateColorAsState(
        targetValue = if (selected) tint.copy(alpha = 0.2f) else tint.copy(alpha = 0.1f),
        animationSpec = Motion.quick(),
        label = "statusChipBackground",
    )

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.5.dp,
                    // The status tint at 35% alpha measured 1.3:1 against the
                    // page, so the boundary of a control was invisible to
                    // anyone who did not already know it was one.
                    color = if (selected) tint else tint.copy(alpha = 0.8f),
                ),
                CircleShape,
            )
            .then(if (onClick != null) Modifier.filterToggle(selected, onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tint))
        Text(
            text = stringResource(status.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Circular category badge used as a list-row leading element. */
@Composable
fun CategoryBadge(
    category: IssueCategory,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(category.tint.copy(alpha = 0.16f))
            .border(1.dp, category.tint.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = category.tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Small role/count pill used in profile and admin rows. */
@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
