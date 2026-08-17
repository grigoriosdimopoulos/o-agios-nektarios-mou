package gr.agiosnektarios.village.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The translucent material platform chrome is made of.
 *
 * Three things together make glass read as glass, and leaving any one out is
 * what makes an imitation look like a flat grey panel:
 *
 *  1. **Translucency, not transparency.** The surface tints what is behind it
 *     rather than showing it, so text stays legible over anything.
 *  2. **A hairline top edge, lighter than the fill.** Real glass catches light
 *     along its edge. This single 1px line does more for the effect than the
 *     blur does.
 *  3. **A vertical falloff.** Uniform fill reads as paint; a fill that is
 *     slightly brighter at the top reads as a pane with light above it.
 *
 * Blur is the fourth and the least important — it needs API 31, and below that
 * the other three still carry the look, which is why this degrades quietly
 * instead of being switched off.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    tint: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.82f,
    edge: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val highlight = if (scheme.surface.luminanceIsDark()) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = (alpha + 0.06f).coerceAtMost(1f)),
                    1f to tint.copy(alpha = alpha),
                ),
            )
            .then(
                if (edge) {
                    Modifier.border(
                        width = Dp.Hairline,
                        brush = Brush.verticalGradient(
                            0f to highlight,
                            0.45f to Color.Transparent,
                        ),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
        if (edge) {
            // The specular line. Drawn last so it sits above the content's own
            // background, which is where light would actually catch.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(Dp.Hairline)
                    .background(highlight),
            )
        }
    }
}

/**
 * A blur applied only where the platform can actually do it.
 *
 * `Modifier.blur` is a no-op below API 31 rather than an error, but calling it
 * unconditionally still costs a layer, so it is gated.
 */
fun Modifier.glassBlur(radius: Dp = 24.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(radius) else this

/** Rough luminance test, to decide whether a highlight should be white or dark. */
private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/**
 * A scrim that fades content out under chrome instead of cutting it off.
 *
 * Used behind the bottom bar so a scrolling list dissolves into the glass
 * rather than sliding under a hard edge.
 */
@Composable
fun EdgeFade(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    fromTop: Boolean = false,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    if (fromTop) {
                        listOf(surface, Color.Transparent)
                    } else {
                        listOf(Color.Transparent, surface)
                    },
                ),
            ),
    )
}
