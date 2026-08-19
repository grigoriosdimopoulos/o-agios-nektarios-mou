package gr.agiosnektarios.village.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * One motion vocabulary for the whole app, tuned to feel like a platform rather
 * than like a website.
 *
 * The thing that separates iOS and macOS motion from most Android motion is not
 * speed — it is that almost nothing is on a *timer*. A spring carries momentum,
 * so an interruption resolves from wherever the surface currently is instead of
 * jumping and restarting. Everything here that a finger can interrupt is
 * therefore a spring, and the durations that remain are only for things nobody
 * can interrupt: a crossfade, a colour, a shimmer.
 *
 * The second thing is restraint. Overshoot reads as expensive exactly once and
 * as cheap every time after, so [playful] is the only spec that bounces and it
 * is reserved for a deliberate, celebratory act — casting a vote. Everything
 * structural settles without bouncing at all.
 */
/**
 * Whether the resident has asked Android to stop things moving.
 *
 * Accessibility → Remove animations sets `ANIMATOR_DURATION_SCALE` to 0. Every
 * animation in this app ignored it, including five that never stop: the
 * emergency pulse, the rain and wind drifting across the map for as long as
 * the layer is on, two blobs behind the sign-in form, the loading shimmer, and
 * the splash. A person who turns animations off has usually done so because
 * movement makes them ill or makes the screen unreadable, and an app that
 * overrides that is not being expressive, it is being rude.
 *
 * Read once per composition and not observed: the setting is changed in
 * Android's own settings screen, which means leaving this app, which means
 * recomposition on the way back.
 */
@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

object Motion {

    // ---------------------------------------------------------------- springs

    /**
     * The workhorse. Presses, chips, toggles, icon states.
     *
     * Critically damped and stiff enough that it reads as immediate while still
     * carrying momentum if the user changes their mind mid-gesture.
     */
    fun <T> snap() = spring<T>(
        dampingRatio = 1f,
        stiffness = 1400f,
    )

    /**
     * Standard transition for anything with visible size or position: cards
     * settling, rows reordering, a badge growing.
     */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.92f,
        stiffness = 420f,
    )

    /**
     * Large surfaces — sheets, dialogs, whole screens. Slower and completely
     * settled, because weight is what makes a big surface feel substantial
     * rather than flimsy.
     */
    fun <T> gentle() = spring<T>(
        dampingRatio = 1f,
        stiffness = 260f,
    )

    /**
     * Anything irreversible: a delete, a suspension, an alarm.
     *
     * Slower than [gentle] and noticeably so. The app used to move everything
     * at the same speed, which meant a dialog asking "remove this permanently?"
     * arrived with exactly the same weight as a dialog offering to sort a list
     * — and speed is the one property of a transition a person reads before
     * they have read any words. Something that cannot be undone should take
     * long enough to arrive that the hand pauses.
     */
    fun <T> deliberate() = spring<T>(
        dampingRatio = 1f,
        stiffness = 130f,
    )

    /**
     * The one place bounce is allowed: the upvote. A single overshoot on the
     * thing the resident chose to do, and nowhere else.
     */
    fun <T> playful() = spring<T>(
        dampingRatio = 0.5f,
        stiffness = 650f,
    )

    /** Offsets need a threshold or they jitter for the last sub-pixel. */
    fun offsetSpring() = spring<IntOffset>(
        dampingRatio = 0.95f,
        stiffness = 380f,
        visibilityThreshold = IntOffset(1, 1),
    )

    // ---------------------------------------------------------------- easings

    /**
     * Slow-out. Almost all movement should start immediately and decelerate
     * into place — the reverse feels sluggish because the delay lands before
     * the user has any feedback.
     */
    val emphasized = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Enter and exit are asymmetric on purpose: arriving deserves more time. */
    val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // -------------------------------------------------------------- durations

    fun <T> instant() = tween<T>(durationMillis = 110, easing = standardEasing)

    fun <T> quick() = tween<T>(durationMillis = 190, easing = standardEasing)

    fun <T> medium() = tween<T>(durationMillis = 300, easing = emphasized)

    fun <T> slow() = tween<T>(durationMillis = 480, easing = emphasized)

    // ------------------------------------------------------------ navigation

    /**
     * How far the outgoing screen slides when a new one pushes over it.
     *
     * A third, not the full width. The old screen staying partly visible and
     * dimmed is what makes a push read as *depth* rather than as a swap, and it
     * is the single most recognisable thing about iOS navigation.
     */
    const val PARALLAX_FRACTION = 3

    const val PUSH_MS = 380
    const val POP_MS = 320

    /**
     * Tab switches, which cross-fade rather than push.
     *
     * Much shorter than a push on purpose: a push has distance to cover and
     * earns its time, while a cross-fade that lingers just looks like the app
     * is thinking. Anything past ~200ms on a fade reads as lag.
     */
    const val TAB_MS = 170

    /** How dark the screen being left behind goes. */
    const val UNDERLAY_DIM = 0.28f

    // ------------------------------------------------------------------ lists
    //
    // There is deliberately no list-entrance spec here any more.
    //
    // Rows used to fade and rise in sequence, and the mechanism was an
    // AnimatedVisibility that starts hidden — which composes nothing and
    // measures 0x0. A row you had not scrolled to yet was therefore zero
    // pixels tall until its delay elapsed, LazyColumn filled the gap with the
    // next row, which was also zero-height, and a fast scroll produced a run
    // of empty rows that popped to full height together. The animation was
    // costing the thing it was decorating. A list should simply be there.

    // ----------------------------------------------------------------- presses

    /** How far an interactive surface depresses. Subtle by design. */
    const val PRESS_SCALE = 0.972f

    /** Cards are larger, so the same *visual* depression needs less scale. */
    const val PRESS_SCALE_LARGE = 0.986f
}
