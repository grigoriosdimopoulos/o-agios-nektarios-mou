package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.reducedMotion


/**
 * A failure the user needs to see, in place, where they caused it.
 *
 * Exists because several screens were tracking an `errorMessage` in state and
 * rendering it nowhere: the write failed, the state updated, and the button
 * appeared simply not to work. A silent failure is worse than an ugly one.
 */
@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        text = message.ifBlank { stringResource(R.string.error_generic) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Full-screen spinner for the brief moment before first data arrives. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

/**
 * Empty states carry an oversized emoji that breathes.
 *
 * An empty screen is where an app feels most lifeless, so this is exactly where
 * a little motion buys the most personality for the least complexity.
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    // Nothing animates here any more.
    //
    // This used to scale between 0.94 and 1.06 forever. A thing that pulses
    // permanently in the corner of the eye is not warmth, it is a tic — and an
    // empty state is, by definition, on screen while somebody is reading it and
    // working out what to do. The one thing in this app that pulses on purpose
    // is an emergency, and it can only mean that if nothing else does.

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = 8.dp)) { action() }
        }
    }
}

/**
 * Shimmering placeholder block.
 *
 * Skeletons rather than a spinner for list content: the layout does not jump
 * when real data replaces them, which matters on the map and issue screens
 * where the user is already reaching for a target.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val still = reducedMotion()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val sweep = if (still) 0.5f else progress
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        (sweep - 0.3f).coerceIn(0f, 1f) to base,
                        sweep.coerceIn(0f, 1f) to highlight,
                        (sweep + 0.3f).coerceIn(0f, 1f) to base,
                    ),
                ),
            ),
    )
}

/** A column of skeletons standing in for a list that has not loaded yet. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(itemCount) { ShimmerBlock() }
    }
}

/** Small inline spinner for buttons and rows. */
@Composable
fun InlineSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
    )
}
