package gr.agiosnektarios.village.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * One motion vocabulary for the whole app.
 *
 * The playful feel comes from springs with a little bounce on things the user
 * *did* (a vote, a marker landing, a sheet arriving) and flat, quick tweens on
 * things that merely changed (a colour, a counter). Mixing the two arbitrarily
 * is what makes an app feel cheap, so every screen pulls from here.
 */
object Motion {

    /** Snappy with a hint of overshoot — buttons, chips, vote taps. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Settles without overshoot — sheets, expanding cards. */
    fun <T> smooth() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** For offsets, which need a visibility threshold to avoid sub-pixel jitter. */
    fun offsetSpring() = spring<IntOffset>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset(1, 1),
    )

    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

    fun <T> quick() = tween<T>(durationMillis = 180, easing = standard)

    fun <T> medium() = tween<T>(durationMillis = 320, easing = emphasized)

    fun <T> slow() = tween<T>(durationMillis = 520, easing = emphasized)

    /** Stagger between list items in an entrance animation. */
    const val STAGGER_MS = 45

    /** Cap the stagger so long lists do not take a full second to appear. */
    const val MAX_STAGGERED_ITEMS = 8
}
