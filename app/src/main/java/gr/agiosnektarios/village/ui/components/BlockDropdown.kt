package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.VillageBlock

/** True when the app is currently rendering in Greek. */
@Composable
fun isGreekLocale(): Boolean = LocalConfiguration.current.locales[0].language == "el"

/**
 * Neighbourhood picker.
 *
 * Names are stored per-language on the block itself rather than as string
 * resources, because the set of neighbourhoods is data that a village
 * administrator changes, not something that ships with a build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDropdown(
    blocks: List<VillageBlock>,
    selectedBlockId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.block),
) {
    var expanded by remember { mutableStateOf(false) }
    val greek = isGreekLocale()
    val selectedName = blocks.firstOrNull { it.id == selectedBlockId }
        ?.localizedName(greek)
        .orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.block_select)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            blocks.forEach { block ->
                DropdownMenuItem(
                    text = { Text(block.localizedName(greek)) },
                    onClick = {
                        onSelect(block.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
