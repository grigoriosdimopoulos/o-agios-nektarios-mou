package gr.agiosnektarios.village.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillagePasswordField
import gr.agiosnektarios.village.ui.components.VillageTextField

@Composable
fun SignInScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Credential Manager needs the activity, not the application context, to
    // host its bottom sheet.
    val context = LocalContext.current

    AuthScaffold(
        title = stringResource(R.string.sign_in_welcome),
        subtitle = stringResource(R.string.sign_in_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VillageTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.email),
                error = state.emailError?.let { stringResource(it) },
                keyboardType = KeyboardType.Email,
            )
            VillagePasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.password),
                error = state.passwordError?.let { stringResource(it) },
                imeAction = ImeAction.Done,
            )

            AuthErrorText(message = state.errorMessage)

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onNavigateToForgotPassword) {
                    Text(stringResource(R.string.forgot_password))
                }
            }

            PrimaryButton(
                text = stringResource(R.string.sign_in),
                onClick = viewModel::signIn,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            OrDivider()

            SecondaryButton(
                text = stringResource(R.string.continue_with_google),
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !state.googleLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = onNavigateToSignUp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.no_account))
            }
        }
    }
}

@Composable
internal fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.or),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** Blank provider messages fall back to the localized generic error. */
@Composable
internal fun AuthErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        text = message.ifBlank { stringResource(R.string.error_generic) },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth(),
    )
}
