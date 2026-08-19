package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.auth.AuthErrorText
import gr.agiosnektarios.village.ui.auth.AuthScaffold
import gr.agiosnektarios.village.ui.auth.OrDivider
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillagePasswordField
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.theme.VillageTheme
import org.junit.Rule
import org.junit.Test

/**
 * The sign-in and registration forms, which no golden had ever rendered.
 *
 * Every resident passes through these once and every returning one passes
 * through the first when their session lapses, so they are the last screens in
 * the app that should be unlooked-at. Two things are on trial here: the error
 * ink in the dark theme, and whether the large-text rule that moved a field's
 * label above its border reached the password field as well as the text field.
 */
class AuditTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val greek = DeviceConfig.PIXEL_5.copy(locale = "el")
    private val greekMax = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f, locale = "el")

    private fun render(
        dark: Boolean = false,
        config: DeviceConfig? = null,
        name: String? = null,
        content: @Composable () -> Unit,
    ) {
        config?.let(paparazzi::unsafeUpdateConfig)
        paparazzi.snapshot(name = name) {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Composable
    private fun SignInBody(withErrors: Boolean) {
        AuthScaffold(
            title = stringResource(R.string.sign_in_welcome),
            subtitle = stringResource(R.string.sign_in_subtitle),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VillageTextField(
                    value = "maria@",
                    onValueChange = {},
                    label = stringResource(R.string.email),
                    error = if (withErrors) stringResource(R.string.error_email_invalid) else null,
                    keyboardType = KeyboardType.Email,
                )
                VillagePasswordField(
                    value = "secret",
                    onValueChange = {},
                    label = stringResource(R.string.password),
                    error = if (withErrors) stringResource(R.string.error_password_short) else null,
                    imeAction = ImeAction.Done,
                )
                AuthErrorText(
                    message = if (withErrors) stringResource(R.string.error_generic) else null,
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = {}) { Text(stringResource(R.string.forgot_password)) }
                }
                PrimaryButton(
                    text = stringResource(R.string.sign_in),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                OrDivider()
                SecondaryButton(
                    text = stringResource(R.string.continue_with_google),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.no_account))
                }
            }
        }
    }

    /** Every red thing on the screen a resident meets when a sign-in fails, after dark. */
    @Test
    fun sign_in_errors_dark() = render(dark = true, config = greek, name = "signin_errors_dark") {
        SignInBody(withErrors = true)
    }

    /** The same, in daylight, for comparison. */
    @Test
    fun sign_in_errors_light() = render(config = greek, name = "signin_errors_light") {
        SignInBody(withErrors = true)
    }

    /**
     * Greek at twice the text. The email field stacks its label above the
     * border; the password field beside it does not, because the rule was only
     * added to [VillageTextField].
     */
    @Test
    fun sign_in_greek_max() = render(config = greekMax, name = "signin_max") {
        SignInBody(withErrors = false)
    }

    /**
     * Registration at twice the text: five stacked labels and two notched
     * ones, including the longest label in the file.
     */
    @Test
    fun sign_up_greek_max() = render(config = greekMax, name = "signup_max") {
        AuthScaffold(
            title = stringResource(R.string.sign_up_title),
            subtitle = stringResource(R.string.sign_up_subtitle),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VillageTextField(
                    value = "",
                    onValueChange = {},
                    label = stringResource(R.string.address),
                )
                VillagePasswordField(
                    value = "",
                    onValueChange = {},
                    label = stringResource(R.string.password),
                )
                VillagePasswordField(
                    value = "",
                    onValueChange = {},
                    label = stringResource(R.string.password_confirm),
                    imeAction = ImeAction.Done,
                )
            }
        }
    }

    /**
     * Change-password, Greek, twice the text, with every field filled so all
     * three labels are floated into the notch that only fits one line.
     */
    @Test
    fun change_password_greek_max() = render(config = greekMax, name = "changepw_max") {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VillagePasswordField(
                value = "secret12",
                onValueChange = {},
                label = stringResource(R.string.password),
            )
            VillagePasswordField(
                value = "secret12",
                onValueChange = {},
                label = stringResource(R.string.settings_change_password),
            )
            VillagePasswordField(
                value = "secret12",
                onValueChange = {},
                label = stringResource(R.string.password_confirm),
                imeAction = ImeAction.Done,
            )
        }
    }
}
