package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import gr.agiosnektarios.village.ui.theme.errorInk
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Whether labels sit above their fields rather than notched into the border.
 *
 * An OutlinedTextField cuts its notch for exactly one line. At twice the text
 * size "Ό,τι βοηθάει — τι, και πού ακριβώς" wraps, and the second line was
 * drawn straight through the field's own top border and into the text above it
 * — on the note field of the fire-alarm screen, at the size the people who
 * most need it are running. The floating label is a way of saving a line; when
 * a line is affordable and the label no longer fits, the label wins.
 */
@Composable
@ReadOnlyComposable
private fun stackLabels(): Boolean = LocalDensity.current.fontScale > 1.3f

/**
 * The label above the field, when [stacked].
 *
 * Deliberately *not* given a contentDescription on the field below it. An
 * earlier version did, which made TalkBack read the label and then read it
 * again as the field's own description — and a contentDescription on an
 * editable node can shadow the text that has been typed into it. Compose
 * already associates an adjacent label with its field.
 */
@Composable
private fun StackedLabel(label: String, stacked: Boolean) {
    if (!stacked) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/**
 * A labelled text field with an inline error that animates in rather than
 * appearing abruptly and shifting the form under the user's thumb.
 */
@Composable
fun VillageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    val stacked = stackLabels()
    Column(modifier = modifier.fillMaxWidth()) {
        StackedLabel(label, stacked)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = if (stacked) null else ({ Text(label) }),
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.errorInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun VillagePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }

    val stacked = stackLabels()
    Column(modifier = modifier.fillMaxWidth()) {
        StackedLabel(label, stacked)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = if (stacked) null else ({ Text(label) }),
            isError = error != null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = null,
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.errorInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
