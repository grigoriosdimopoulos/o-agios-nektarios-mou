package gr.agiosnektarios.village.ui.issue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.rememberHaptics

/**
 * Handing one report to the people whose job it is.
 *
 * The text is shown before it is sent, in full, because it goes out over the
 * resident's own name and they are entitled to read it first. It carries what
 * an office actually needs and what only a village has: what, where in words,
 * the coordinates, a map link, when it was first reported, and how many
 * neighbours confirmed it.
 *
 * The address is asked for once and remembered on the device. The app ships no
 * municipal address because it has none it has checked, and a plausible wrong
 * one would send the village's reports into nothing while everybody believed
 * they had been sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouncilSheet(
    issue: Issue,
    savedEmail: String,
    canRecord: Boolean,
    onRememberEmail: (String) -> Unit,
    onRecord: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val haptics = rememberHaptics()
    var address by remember { mutableStateOf(savedEmail) }
    var reference by remember { mutableStateOf(issue.councilReference) }

    val body = remember(issue, locale) { CouncilHandoff.body(context, issue, locale) }
    val subject = remember(issue) { CouncilHandoff.subject(context, issue) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.council_send),
                style = MaterialTheme.typography.titleMedium,
            )

            // The whole message, before it goes anywhere.
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VillageTextField(
                value = address,
                onValueChange = { address = it },
                label = stringResource(R.string.council_address),
                keyboardType = KeyboardType.Email,
            )

            PrimaryButton(
                text = stringResource(R.string.council_send_email),
                onClick = {
                    haptics.committed()
                    onRememberEmail(address)
                    CouncilHandoff.email(context, address.trim(), subject, body)
                },
                enabled = address.contains('@'),
                icon = Icons.Filled.Email,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.council_send_other),
                onClick = {
                    haptics.tick()
                    CouncilHandoff.share(context, subject, body)
                },
                icon = Icons.Filled.Share,
                modifier = Modifier.fillMaxWidth(),
            )

            // Recording it is a separate act from sending it, and a stricter
            // one: sending is something anybody can do, while saying "this is
            // now with the municipality" is a claim the whole village reads.
            if (canRecord) {
                VillageTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = stringResource(R.string.council_reference),
                )
                if (issue.reportedToCouncilAt == null) {
                    SecondaryButton(
                        text = stringResource(R.string.council_mark),
                        onClick = {
                            haptics.committed()
                            onRecord(reference, true)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SecondaryButton(
                        text = stringResource(R.string.council_clear),
                        onClick = {
                            haptics.tick()
                            onRecord("", false)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
