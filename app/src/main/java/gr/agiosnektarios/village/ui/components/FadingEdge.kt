package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Softens whichever edge of a horizontal row has content beyond it.
 *
 * A row of chips wider than the screen gets cut dead at the last pixel,
 * mid-word, in exactly the state every user sees first — which reads as a
 * layout bug rather than as scrollable content. Fading the cut turns it into
 * an invitation.
 *
 * The first version faded the right edge unconditionally, which was wrong at
 * both ends: scrolled fully right, the last chip sat ghosted with nothing
 * behind it, and scrolling right cut the leading chips dead at x=0 with no
 * fade at all. So the fade follows the scroll — each side appears only while
 * there is something past it, and animates in with the content rather than
 * being painted over an edge that has nothing to hide.
 *
 * Drawn as a destination-in mask on its own layer, so it fades whatever the
 * row painted rather than a colour guessed from the theme; that keeps it
 * correct over any background and in either theme. The offscreen layer costs
 * one buffer for the row — worth it for a chip strip, which is why this is a
 * named modifier rather than something to reach for on every list.
 */
@Composable
fun Modifier.fadingEdges(state: LazyListState, width: Dp = 28.dp): Modifier {
    val fadeStart by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    val fadeEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && (last.index < info.totalItemsCount - 1 ||
                last.offset + last.size > info.viewportEndOffset)
        }
    }

    if (!fadeStart && !fadeEnd) return this

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = width.toPx().coerceAtMost(size.width / 2f)
            if (fadeStart) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = fade,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (fadeEnd) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
