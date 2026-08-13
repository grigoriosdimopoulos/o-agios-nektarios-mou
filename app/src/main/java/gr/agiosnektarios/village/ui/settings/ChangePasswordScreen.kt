package gr.agiosnektarios.village.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.validation.Validators
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillagePasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val doneMessage = stringResource(R.string.profile_saved)

    LaunchedEffect(state.done) {
        if (state.done) {
            showSnackbar(doneMessage)
            onBack()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val newPasswordError = state.newPassword
        .takeIf { it.isNotEmpty() }
        ?.let { Validators.password(it) }
    val confirmationError = state.confirmation
        .takeIf { it.isNotEmpty() }
        ?.let { Validators.passwordConfirmation(state.newPassword, it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_change_password)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VillagePasswordField(
                value = state.currentPassword,
                onValueChange = viewModel::onCurrent,
                label = stringResource(R.string.password),
            )
            VillagePasswordField(
                value = state.newPassword,
                onValueChange = viewModel::onNew,
                label = stringResource(R.string.settings_change_password),
                error = newPasswordError?.let { stringResource(it) },
            )
            VillagePasswordField(
                value = state.confirmation,
                onValueChange = viewModel::onConfirmation,
                label = stringResource(R.string.password_confirm),
                error = confirmationError?.let { stringResource(it) },
                imeAction = ImeAction.Done,
            )

            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::submit,
                enabled = state.currentPassword.isNotBlank() &&
                    newPasswordError == null &&
                    confirmationError == null &&
                    state.newPassword.isNotBlank() &&
                    state.confirmation.isNotBlank(),
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
