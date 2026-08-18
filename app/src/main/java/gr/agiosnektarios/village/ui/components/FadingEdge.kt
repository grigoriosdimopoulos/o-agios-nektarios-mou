package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Softens the right edge of a horizontally scrolling row.
 *
 * A row of chips that is wider than the screen gets cut dead at the last pixel,
 * mid-word, in exactly the state every user sees first — which reads as a
 * layout bug rather than as scrollable content. Fading the last few dp turns
 * the cut into an invitation.
 *
 * Drawn with a destination-in mask on its own layer, so the fade applies to
 * whatever the row painted rather than to a colour guessed from the theme —
 * which means it stays correct over any background, in either theme.
 */
fun Modifier.fadingEdge(width: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx().coerceAtMost(size.width)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width - fade,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
