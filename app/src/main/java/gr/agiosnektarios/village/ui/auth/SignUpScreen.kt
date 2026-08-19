package gr.agiosnektarios.village.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import gr.agiosnektarios.village.ui.components.BlockDropdown
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillagePasswordField
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.theme.errorInk

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.sign_up_title),
        subtitle = stringResource(R.string.sign_up_subtitle),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.email),
                error = state.emailError?.let { stringResource(it) },
                keyboardType = KeyboardType.Email,
            )
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
            )
            BlockDropdown(
                blocks = state.blocks,
                selectedBlockId = state.blockId,
                onSelect = viewModel::onBlock,
                modifier = Modifier.fillMaxWidth(),
            )
            VillagePasswordField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = stringResource(R.string.password),
                error = state.passwordError?.let { stringResource(it) },
            )
            VillagePasswordField(
                value = state.passwordConfirmation,
                onValueChange = viewModel::onPasswordConfirmation,
                label = stringResource(R.string.password_confirm),
                error = state.confirmationError?.let { stringResource(it) },
                imeAction = ImeAction.Done,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = state.acceptedTerms,
                    onCheckedChange = viewModel::onAcceptTerms,
                )
                Text(
                    text = stringResource(R.string.accept_terms),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            state.termsError?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.errorInk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AuthErrorText(message = state.errorMessage)

            PrimaryButton(
                text = stringResource(R.string.sign_up),
                onClick = viewModel::submit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.have_account))
            }
        }
    }
}
