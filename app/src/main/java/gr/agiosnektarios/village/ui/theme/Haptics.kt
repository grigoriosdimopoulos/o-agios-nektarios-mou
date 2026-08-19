package gr.agiosnektarios.village.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What the app feels like in the hand.
 *
 * There was nothing here at all. Voting, taking a job on, sending a message,
 * raising an alarm — every one of them was silent to the hand, and on Android a
 * short tick at the moment something commits is the cheapest signal of quality
 * that exists. It also does work that no amount of colour can: it confirms the
 * tap landed, on a phone held at arm's length by someone who is not looking
 * closely.
 *
 * The rule is narrow on purpose. Haptics belong to **commitment** — a thing
 * that changed for everybody — and to nothing else. Buzzing on navigation, on
 * scroll, or on opening a sheet is how a phone becomes something people turn
 * off in settings, and then the one that mattered is gone too.
 *
 * Only two of the platform's constants are safe to lean on across the range of
 * phones this village owns: `LongPress` is a firm tick, `TextHandleMove` is a
 * light one. The richer types were added later or are simply ignored by some
 * manufacturers, so the vocabulary here is two words rather than a scale that
 * would be imaginary on half the devices.
 */
class VillageHaptics internal constructor(private val feedback: HapticFeedback) {

    /** Something is now true for the whole village: a report filed, a job taken. */
    fun committed() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** A smaller change that is still a change: a vote, a filter, a confirmation. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /**
     * Something is wrong, or about to be irreversible.
     *
     * Two firm ticks rather than one. There is no "error" constant that behaves
     * consistently across manufacturers, and a doubled tick is legible on every
     * phone because it is made of the one thing that is.
     */
    fun warning() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(): VillageHaptics = VillageHaptics(LocalHapticFeedback.current)
