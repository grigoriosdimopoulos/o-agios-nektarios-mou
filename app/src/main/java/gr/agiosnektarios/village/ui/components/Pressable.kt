package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import gr.agiosnektarios.village.ui.theme.Motion

/**
 * Makes a surface depress under a finger the way a platform control does.
 *
 * Two details do most of the work. The scale is driven by the *pressed* state
 * rather than by the click, so it follows the finger — press and hold and it
 * stays down, slide off and it comes back up without firing. And it comes back
 * on a spring, so lifting off has weight instead of snapping.
 *
 * The haptic fires on press, not on release. That is what a hardware button
 * does, and it is why iOS controls feel connected to the hand: the confirmation
 * arrives while the finger is still down, not after it has left.
 */
fun Modifier.pressable(
    scaleTo: Float = Motion.PRESS_SCALE,
    haptics: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = Motion.standard(),
        label = "pressScale",
    )

    if (haptics && pressed) {
        // Keyed on the press so it fires once per press rather than per frame.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    scale(scale)
}

/**
 * The press scale without the haptic, for surfaces that already vibrate through
 * something else — a switch, a checkbox — so one tap does not buzz twice.
 */
fun Modifier.pressableQuiet(scaleTo: Float = Motion.PRESS_SCALE_LARGE): Modifier =
    pressable(scaleTo = scaleTo, haptics = false)

/**
 * A press source whose scale can be shared with a caller that also needs the
 * interaction — Material's own components want to own their ripple, so they get
 * handed the same source rather than a second one.
 */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }
