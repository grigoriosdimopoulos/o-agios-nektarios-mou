package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The plumbing for elements that persist across a screen change.
 *
 * A shared element transition is the single most recognisable "this app is
 * expensive" detail there is: tap a card and the card itself becomes the page,
 * rather than the page sliding in over it while an identical-looking card is
 * drawn again underneath. Compose can do it, but only inside a
 * [SharedTransitionScope], and only when the element also knows which
 * [AnimatedVisibilityScope] it is animating within.
 *
 * Threading two scopes through every screen signature would touch a dozen
 * files and make every component harder to read for a feature that is
 * decoration. So they travel as composition locals, and [sharedBoundsOrNone]
 * degrades to nothing at all when either is absent.
 *
 * That degradation is not laziness — it is what makes the components still
 * renderable in Paparazzi, still usable from a preview, and still correct in
 * any screen that is not inside the navigation graph. An animation must never
 * be the reason a component cannot be drawn.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this composable as the same thing as the one with the same [key] on
 * the screen being left, so it morphs between them instead of cross-fading.
 *
 * Use for a *container* whose size and shape change across screens — a card
 * becoming a page header.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsOrNone(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedBoundsOrNone.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibility,
        )
    }
}

/**
 * As [sharedBoundsOrNone], but for content that is *the same size* on both
 * screens and should simply travel — a line of text, an avatar, an icon.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrNone(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedElementOrNone.sharedElement(
            state = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibility,
        )
    }
}

/** Key builders, so a typo cannot silently disable a transition. */
object SharedKeys {
    fun issueCard(id: String) = "issue-card-$id"
    fun issueTitle(id: String) = "issue-title-$id"
}
