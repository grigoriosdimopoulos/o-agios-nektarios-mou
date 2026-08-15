package gr.agiosnektarios.village.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import gr.agiosnektarios.village.R

/**
 * Shows the previous crash, once, with a button that copies it to the
 * clipboard.
 *
 * Deliberately blunt and untranslated in its content: the audience for the
 * stack trace is whoever is fixing the app, and the resident's job is only to
 * paste it somewhere. The surrounding words are localized so it does not look
 * like the app broke a second time.
 */
@Composable
fun CrashDialog(
    report: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.crash_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = report,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.copyToClipboard(report)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.crash_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private fun Context.copyToClipboard(text: String) {
    getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText("crash", text))
}
