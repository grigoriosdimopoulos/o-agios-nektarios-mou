package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.theme.VillageAccents

/**
 * Upvote / downvote control.
 *
 * Tapping the vote you already cast clears it, which is what people expect from
 * every other product with this affordance. The count rolls vertically in the
 * direction it moved, so the change is legible even when you were not looking
 * at the number.
 */
@Composable
fun VoteBar(
    upvotes: Int,
    downvotes: Int,
    myVote: Int,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // A vote is the smallest thing in this app that changes what everybody
    // else sees, so it is the smallest thing that earns a tick.
    val haptics = rememberHaptics()
    val vote: (Int) -> Unit = { value ->
        haptics.tick()
        onVote(value)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoteButton(
            count = upvotes,
            active = myVote == 1,
            activeTint = VillageAccents.upvote,
            activeIcon = Icons.Filled.ThumbUp,
            inactiveIcon = Icons.Outlined.ThumbUp,
            contentDescription = stringResource(R.string.issue_upvote),
            enabled = enabled,
            onClick = { vote(if (myVote == 1) 0 else 1) },
        )
        VoteButton(
            count = downvotes,
            active = myVote == -1,
            activeTint = VillageAccents.downvote,
            activeIcon = Icons.Filled.ThumbDown,
            inactiveIcon = Icons.Outlined.ThumbDown,
            contentDescription = stringResource(R.string.issue_downvote),
            enabled = enabled,
            onClick = { vote(if (myVote == -1) 0 else -1) },
        )
    }
}

@Composable
private fun VoteButton(
    count: Int,
    active: Boolean,
    activeTint: androidx.compose.ui.graphics.Color,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (active) activeTint else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.quick(),
        label = "voteTint",
    )
    // A brief pop the instant the vote lands: the optimistic feedback that makes
    // voting feel free even though a round trip is still happening.
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = Motion.playful(),
        label = "voteScale",
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) activeTint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (active) activeIcon else inactiveIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp).scale(scale),
        )
        AnimatedCount(count = count, tint = tint)
    }
}

@Composable
private fun AnimatedCount(count: Int, tint: androidx.compose.ui.graphics.Color) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val goingUp = targetState > initialState
            val height = if (goingUp) 1 else -1
            (
                slideInVertically { height * it } togetherWith
                    slideOutVertically { -height * it }
                )
        },
        label = "voteCount",
    ) { value ->
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}
