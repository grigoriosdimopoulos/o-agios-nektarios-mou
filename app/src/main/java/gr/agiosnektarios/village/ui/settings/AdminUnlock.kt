package gr.agiosnektarios.village.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.primaryInk

/**
 * Counts taps on a otherwise-inert row and opens after enough of them in a row.
 *
 * Modelled on how Android itself hides developer options: the affordance is
 * that there is no affordance. Nothing marks the row as tappable, so a resident
 * cannot stumble into it, and anyone who has been told "tap the version seven
 * times" finds it immediately.
 *
 * The run resets after a pause so that idle taps spread over a session never
 * accumulate into an unlock nobody meant.
 */
@Composable
fun rememberTapUnlock(
    requiredTaps: Int = 7,
    windowMillis: Long = 3_000,
    onUnlocked: () -> Unit,
): () -> Unit {
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    return {
        val now = System.currentTimeMillis()
        taps = if (now - lastTapAt > windowMillis) 1 else taps + 1
        lastTapAt = now
        if (taps >= requiredTaps) {
            taps = 0
            onUnlocked()
        }
    }
}

/**
 * Asks for the village's admin passphrase.
 *
 * Deliberately says nothing about what a correct passphrase looks like, and
 * reports failure in one sentence that covers both a wrong passphrase and a
 * village that never configured one — telling the two apart would help someone
 * guessing and helps nobody else.
 */
@Composable
fun AdminUnlockDialog(
    passphrase: String,
    submitting: Boolean,
    errorMessage: String?,
    onPassphraseChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.admin_unlock_title)) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                singleLine = true,
                label = { Text(stringResource(R.string.admin_unlock_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } },
                modifier = Modifier,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = passphrase.isNotBlank() && !submitting,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primaryInk,
                    )
                } else {
                    Text(stringResource(R.string.admin_unlock_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
