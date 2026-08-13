package gr.agiosnektarios.village.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.BlockDropdown
import gr.agiosnektarios.village.ui.components.InlineSpinner
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedMessage = stringResource(R.string.profile_saved)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(viewModel::changePhoto) },
    )

    LaunchedEffect(state.saved) {
        if (state.saved) {
            showSnackbar(savedMessage)
            onBack()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(
                        photoUrl = state.photoUrl,
                        initials = "${state.firstName.take(1)}${state.lastName.take(1)}",
                        seed = state.photoUrl.ifBlank { state.firstName },
                        size = 96.dp,
                    )
                    if (state.uploadingPhoto) InlineSpinner()
                }
                Text(
                    text = stringResource(R.string.profile_change_photo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VillageTextField(
                    value = state.firstName,
                    onValueChange = viewModel::onFirstName,
                    label = stringResource(R.string.first_name),
                    error = state.firstNameError?.let { stringResource(it) },
                    modifier = Modifier.weight(1f),
                )
                VillageTextField(
                    value = state.lastName,
                    onValueChange = viewModel::onLastName,
                    label = stringResource(R.string.last_name),
                    error = state.lastNameError?.let { stringResource(it) },
                    modifier = Modifier.weight(1f),
                )
            }
            VillageTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.phone),
                error = state.phoneError?.let { stringResource(it) },
                keyboardType = KeyboardType.Phone,
            )
            VillageTextField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = stringResource(R.string.address),
                error = state.addressError?.let { stringResource(it) },
                imeAction = ImeAction.Done,
            )
            BlockDropdown(
                blocks = state.blocks,
                selectedBlockId = state.blockId,
                onSelect = viewModel::onBlock,
                modifier = Modifier.fillMaxWidth(),
            )

            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::save,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
