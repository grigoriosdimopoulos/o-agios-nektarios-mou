package gr.agiosnektarios.village.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.theme.isDark

/**
 * The translucent material platform chrome is made of.
 *
 * Three things together make glass read as glass, and leaving any one out is
 * what makes an imitation look like a flat grey panel:
 *
 *  1. **A tint that differs from the page underneath.** This is the one that
 *     was wrong: the default used to be `colorScheme.surface`, and in this
 *     palette `surface == background`, so the bar was painting the page colour
 *     onto the page. It was rendering — it was just literally invisible.
 *  2. **A hairline edge that separates it from the content.** On a dark
 *     background that edge is light, because glass catches light. On a *light*
 *     background it has to be dark: iOS separators over white are a translucent
 *     black, never white-on-white. Using one rule for both is what made the
 *     light theme look unfinished.
 *  3. **A vertical falloff.** Uniform fill reads as paint; a fill that shifts
 *     slightly from top to bottom reads as a pane with light above it.
 *
 * Blur is the fourth and the least important — it needs API 31, and below that
 * the other three still carry the look, which is why this degrades quietly
 * instead of being switched off.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.glassTint(),
    alpha: Float = 0.88f,
    edge: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.surface.isDark()

    // The falloff has to move the colour, not the alpha. The old version varied
    // alpha by 0.06, which no eye resolves; shifting the top toward white on
    // dark and the bottom toward ink on light is visible at a glance.
    val top = if (dark) lerp(tint, Color.White, 0.07f) else tint
    val bottom = if (dark) tint else lerp(tint, Color(0xFF17211E), 0.05f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to top.copy(alpha = alpha),
                    1f to bottom.copy(alpha = alpha),
                ),
            ),
    ) {
        content()
        if (edge) {
            // Drawn last, and as a real 1dp strip rather than `Dp.Hairline`.
            // Hairline is 0.dp: it means "thinnest line the renderer can draw"
            // to *stroke* APIs, but as a Box height it is simply zero, so the
            // specular line this was supposed to draw had no pixels at all.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(separatorColor(dark)),
            )
        }
    }
}

/**
 * The colour chrome should tint with, given the page it floats over.
 *
 * Deliberately not `surface`: it must contrast with the background, or the
 * whole effect collapses. Light picks the brightest container (white against
 * the cream page), dark picks a raised one (slate against near-black).
 */
@Composable
@ReadOnlyComposable
fun ColorScheme.glassTint(): Color =
    if (surface.isDark()) surfaceContainerHigh else surfaceContainerLowest

/** iOS-style hairline: light over dark, dark over light — never white on white. */
private fun separatorColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)

/**
 * A blur applied only where the platform can actually do it.
 *
 * `Modifier.blur` is a no-op below API 31 rather than an error, but calling it
 * unconditionally still costs a layer, so it is gated.
 */
fun Modifier.glassBlur(radius: Dp = 24.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(radius) else this

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
    val surface = MaterialTheme.colorScheme.background
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
