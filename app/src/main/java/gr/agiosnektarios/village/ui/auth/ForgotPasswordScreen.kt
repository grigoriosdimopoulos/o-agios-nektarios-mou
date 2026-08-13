package gr.agiosnektarios.village.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.reset_password),
        subtitle = stringResource(R.string.reset_password_subtitle),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VillageTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.email),
                error = state.emailError?.let { stringResource(it) },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                enabled = !state.sent,
            )

            AuthErrorText(message = state.errorMessage)

            AnimatedVisibility(visible = state.sent, enter = fadeIn() + expandVertically()) {
                Text(
                    text = stringResource(R.string.reset_password_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            PrimaryButton(
                text = if (state.sent) {
                    stringResource(R.string.action_back)
                } else {
                    stringResource(R.string.action_send)
                },
                onClick = { if (state.sent) onBack() else viewModel.submit() },
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
