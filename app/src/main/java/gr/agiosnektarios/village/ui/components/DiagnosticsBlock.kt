package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R

/**
 * A block of technical facts, with a button that copies them.
 *
 * The copy button is the whole point. What matters in here is a SHA-1 — forty
 * characters of hex that has to be compared, exactly, against a console on
 * another screen. Transcribing one by eye, or reading it off a photograph, is
 * how a diagnosis takes three attempts instead of one.
 */
@Composable
fun DiagnosticsBlock(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { clipboard.setText(AnnotatedString(text)) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_copy),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
