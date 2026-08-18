package gr.agiosnektarios.village.ui.navigation

import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * How far in from the leading edge a drag has to start to count as a back
 * gesture.
 *
 * Deliberately narrower than [gr.agiosnektarios.village.ui.theme.Space.page],
 * the 20dp margin every screen keeps clear. At 24dp this band reached *past*
 * that margin and into content — including the map embedded in the report
 * composer, where a 90dp pan would have popped the screen and thrown away a
 * half-written report.
 */
private val EDGE_WIDTH = 16.dp

/** How far it has to travel before it commits. */
private val COMMIT_DISTANCE = 90.dp

/**
 * Swipe from the left edge to go back, on the Android versions where the
 * system will not do it.
 *
 * The manifest opts into `enableOnBackInvokedCallback`, and Navigation Compose
 * seeks its pop transition from `onBackProgressed` — but that callback only
 * exists from API 34. On API 33 the platform delivers a back *invocation* with
 * no progress, and below 33 the opt-in is inert entirely. This app's minimum
 * is 26. So on a phone older than Android 14 the predictive gesture does
 * nothing at all, and "there is no swipe back" stays true no matter what the
 * manifest says.
 *
 * This is the fallback, and it is deliberately the simpler thing: it does not
 * try to reimplement finger-tracked seeking, it recognises the gesture and
 * pops. The screen still slides away on the graph's own pop transition, so it
 * reads as a back gesture rather than as a button press — it just does not
 * follow the thumb mid-drag. Above API 34 this is switched off entirely,
 * because the real thing is better and two handlers would fight.
 *
 * Guards, in order of how much trouble they save:
 *  - Only from the leading 16dp, which is inside the page margin, so it
 *    cannot reach content that respects it.
 *  - Abandoned the moment anything else claims the drag. A horizontal strip —
 *    the photos on a report, the people in a new chat — has touch bounds that
 *    start at x=0 whatever its content padding, so a narrow band alone is not
 *    enough. Watching on the final pass and checking `isConsumed` lets the
 *    scroller win, which is right: if you are dragging a row of photos, that
 *    is what you meant.
 *  - Only when [enabled], which the caller sets from "is there anywhere to go
 *    back to" — on a top-level tab this must never fire.
 *  - Only when the drag is more horizontal than vertical, so a diagonal scroll
 *    is not read as a dismissal.
 */
fun Modifier.edgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier = composed {
    if (!enabled || Build.VERSION.SDK_INT >= 34) return@composed this

    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_WIDTH.toPx() }
    val commitPx = with(density) { COMMIT_DISTANCE.toPx() }

    pointerInput(enabled) {
        awaitEachGesture {
            // Initial pass: look at the touch before anything else claims it,
            // but do not consume — a tap near the edge must still reach what
            // is under it.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > edgePx) return@awaitEachGesture

            var dx = 0f
            var dy = 0f
            var fired = false
            while (true) {
                // Final pass: everything else — scrollable rows, the map view —
                // has already had its say by now, so a consumed change means
                // some other gesture owns this drag and this one must let go.
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!fired && change.isConsumed) return@awaitEachGesture
                dx += change.position.x - change.previousPosition.x
                dy += change.position.y - change.previousPosition.y
                if (!fired && dx > commitPx && dx > abs(dy)) {
                    fired = true
                    onBack()
                }
            }
        }
    }
}
