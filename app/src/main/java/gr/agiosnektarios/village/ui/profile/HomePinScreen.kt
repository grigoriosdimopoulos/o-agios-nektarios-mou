package gr.agiosnektarios.village.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.GlassSurface
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.map.VillageMap
import gr.agiosnektarios.village.ui.theme.LocalIsDarkTheme
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Putting your own house on the map.
 *
 * Forty-six houses in this settlement and not one street address between them.
 * When somebody here calls an ambulance the hard part has never been the
 * telephone number — it is telling the driver where to come, and "the third
 * turning after the church, the one with the blue gate" is genuinely what
 * people say. A pinned house turns that into a coordinate that can be read out
 * or sent, and it is what the urgent screen offers as a place.
 *
 * Voluntary, clearable, and read by nobody but the person who set it — not
 * other residents, not an administrator. It lives at
 * `users/{uid}/private/home` rather than on the profile document for
 * exactly that reason; see [gr.agiosnektarios.village.core.model.HomePin].
 */
@Composable
fun HomePinScreen(
    onDone: () -> Unit,
    viewModel: HomePinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalIsDarkTheme.current
    val haptics = rememberHaptics()
    var confirmingClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Box(modifier = Modifier.fillMaxSize()) {
        VillageMap(
            modifier = Modifier.fillMaxSize(),
            clusters = emptyList(),
            pendingPin = state.pin,
            darkTheme = dark,
            basemap = state.basemap,
            greekLabels = true,
            streetNames = state.streetNames,
            focusedIssueId = null,
            myPosition = state.myPosition,
            // The pin being placed *is* the house, so drawing the saved one
            // underneath it would put two houses on the map at once.
            homePosition = null,
            allowRoadTaps = false,
            onZoomChanged = {},
            onMapTap = viewModel::onTap,
            onClusterTap = {},
            onRoadTap = {},
        )

        IconButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier.padding(Space.page),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_pin_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.placeLabel.ifBlank {
                        stringResource(
                            if (state.pin == null) {
                                R.string.home_pin_hint
                            } else {
                                R.string.home_pin_set
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = { haptics.committed(); viewModel.save() },
                        enabled = state.pin != null && !state.saving,
                        loading = state.saving,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.home_pin_clear),
                        onClick = { haptics.warning(); confirmingClear = true },
                        enabled = !state.saving,
                    )
                }
            }
        }
    }

    // Asked, because it sits at equal weight beside Save and undoing it means
    // finding the house on the map again — which is the thing this screen
    // exists to spare somebody who is telephoning for an ambulance.
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.home_pin_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingClear = false; viewModel.clear() }) {
                    Text(stringResource(R.string.home_pin_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
