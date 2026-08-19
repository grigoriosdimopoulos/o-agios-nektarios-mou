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
import gr.agiosnektarios.village.ui.theme.primaryInk

/**
 * What a filter chip is, to a finger and to a screen reader.
 *
 * `toggleable` rather than `clickable`, so TalkBack announces "tick box,
 * ticked" and not an unlabelled tap target — twenty-one of these make up the
 * only filter controls in the app, and which ones are on was conveyed by a
 * half-density border and an alpha change of the same hue.
 *
 * The 48dp minimum is [touchTarget], applied on each chip's own chain.
 */
private fun Modifier.filterToggle(selected: Boolean, onClick: () -> Unit): Modifier = this
    .toggleable(value = selected, role = Role.Checkbox) { onClick() }

/**
 * 48dp of touch, and only where there is something to touch.
 *
 * First in the chain, because a layout modifier wraps everything after it: at
 * the end of a chain it expands the content and then clip/background/border
 * draw over the expanded box, giving a 48dp pill rather than a 32dp pill with
 * 48dp of touch around it.
 *
 * And conditional, because these same two chips are also used as labels — on
 * every report card, and on the report detail. Applying it there added 21dp to
 * every card in the list, 11% of the first one, as dead space above and below
 * a status pill nobody can tap.
 */
private fun Modifier.touchTarget(interactive: Boolean): Modifier =
    if (interactive) minimumInteractiveComponentSize() else this

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
            .touchTarget(onClick != null)
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
            .touchTarget(onClick != null)
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

/**
 * Small role/count pill used in profile, admin and announcement rows.
 *
 * The ink and the fill are two colours, which sounds obvious and was not: this
 * painted the label in the same value it used at 12% for the background, so an
 * author's name on a pinned notice measured 1.83:1 against its own pill and
 * the ADMIN badge 2.39:1. One parameter served two jobs that pull in opposite
 * directions — the same mistake, in the same shape, as the primary and error
 * colours before them.
 *
 * The fill is opaque, and that is the second half of the fix. A translucent
 * wash takes its value from whatever is behind it, so the same pill that
 * cleared 4.87:1 on an ordinary notice measured 4.09:1 on a pinned one, where
 * the card underneath is yellow. An opaque fill plus a hairline reads as a
 * pill on any background and can be checked once.
 */
@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.primaryInk,
    fill: Color = MaterialTheme.colorScheme.surface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, ink.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
