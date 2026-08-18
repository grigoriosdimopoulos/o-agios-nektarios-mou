package gr.agiosnektarios.village.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How anything raised above the page is coloured.
 *
 * This exists because the same mistake was made three separate times — on the
 * bottom bar's glass, on the issue cards, and on the profile's stat tiles — and
 * each time it was invisible until someone rendered the screen and looked.
 *
 * The mistake is assuming Material's container roles separate by themselves.
 * In a dark scheme they do: `surfaceContainerLow` over a near-black page is
 * visibly lighter, and raising the fill is enough. In this light scheme they do
 * not. The page is cream and every raised container is within two percent of
 * it, so a card painted `surfaceContainerLow` dissolves into the background.
 *
 * Light therefore goes the other way — the brightest container there is, which
 * is white, plus a hairline outline to hold its edge. That is what a grouped
 * list looks like on iOS, and it is why cards there read as objects sitting on
 * a surface rather than as paragraphs sharing one.
 *
 * Use these two together. Anything that calls itself a card, tile, sheet or
 * panel should take its fill from [raisedContainer] and its border from
 * [raisedOutline] rather than naming a container role directly.
 */
@Composable
@ReadOnlyComposable
fun ColorScheme.raisedContainer(): Color =
    if (surface.isDark()) surfaceContainerLow else surfaceContainerLowest

/** Null on dark, where a lighter fill already separates. */
@Composable
@ReadOnlyComposable
fun ColorScheme.raisedOutline(): BorderStroke? =
    if (surface.isDark()) null else BorderStroke(1.dp, outlineVariant.copy(alpha = 0.7f))

/** Convenience for the common `MaterialTheme.colorScheme.raisedContainer()`. */
val raisedContainer: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.raisedContainer()

val raisedOutline: BorderStroke?
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.raisedOutline()

/**
 * The lift under a raised surface in the light theme, and nothing in the dark.
 *
 * Dark schemes separate by value — a lighter block on near-black is already
 * "above" the page — and adding a shadow there only muddies it. Light schemes
 * separate by *depth*, and this app had none: every card was elevation 0 with
 * a hairline, so the light theme read as a printed page while the dark one
 * read as an app. That difference is most of why one felt finished and the
 * other did not.
 *
 * The shadow is tinted rather than black. Compose's default shadow is pure
 * black, which over a cream page goes grey and dirty; pulling it toward the
 * scheme's own ink keeps it warm.
 */
@Composable
@ReadOnlyComposable
fun ColorScheme.raisedShadow(): Dp = if (surface.isDark()) 0.dp else 2.dp

/** Warm ink rather than black, so the shadow does not go grey over cream. */
val shadowTint: Color = Color(0xFF2C2418)

val raisedShadow: Dp
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.raisedShadow()

/** Rough perceptual luminance test, to decide which way a treatment should go. */
fun Color.isDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
