package gr.agiosnektarios.village.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField

/**
 * Shown when someone is authenticated but has no village profile yet — the
 * normal path after a first Google sign-in.
 */
@Composable
fun CompleteProfileScreen(
    viewModel: CompleteProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.sign_up_title),
        subtitle = stringResource(R.string.sign_up_subtitle),
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

            AuthErrorText(message = state.errorMessage)

            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = viewModel::submit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            // The only way out of this screen without finishing: otherwise a
            // half-registered Google account would be stuck here forever.
            TextButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
