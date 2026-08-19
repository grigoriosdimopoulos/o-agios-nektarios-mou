package gr.agiosnektarios.village.ui.settings

import gr.agiosnektarios.village.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.BuildConfig
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.data.settings.AppLanguage
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.theme.primaryInk
import gr.agiosnektarios.village.ui.theme.errorInk
import gr.agiosnektarios.village.core.model.Feature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenContacts: () -> Unit,
    showSnackbar: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val smsConsent by viewModel.smsConsent.collectAsStateWithLifecycle()
    val flags by viewModel.flags.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val adminUnlock by viewModel.adminUnlock.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val onVersionTap = rememberTapUnlock(onUnlocked = viewModel::openAdminUnlock)
    val elevatedMessage = stringResource(R.string.admin_unlock_done)

    LaunchedEffect(events.errorMessage) {
        events.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(adminUnlock.elevated) {
        if (adminUnlock.elevated) {
            // The Admin tab appears on its own: the bottom bar is driven by the
            // profile document, which the elevation just rewrote.
            showSnackbar(elevatedMessage)
            viewModel.consumeElevation()
        }
    }

    if (adminUnlock.visible) {
        AdminUnlockDialog(
            passphrase = adminUnlock.passphrase,
            submitting = adminUnlock.submitting,
            errorMessage = adminUnlock.errorRes?.let { stringResource(it) },
            onPassphraseChange = viewModel::onAdminPassphrase,
            onSubmit = viewModel::submitAdminUnlock,
            onDismiss = viewModel::dismissAdminUnlock,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(bottom = 32.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            },
                        ),
                        selected = settings.themeMode == mode,
                        onSelect = { viewModel.setTheme(mode) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_language))

            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    RadioRow(
                        label = when (language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_theme_system)
                            AppLanguage.GREEK -> stringResource(R.string.settings_language_el)
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
                        },
                        selected = settings.language == language,
                        onSelect = { viewModel.setLanguage(language) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_notifications))

            SwitchRow(
                label = stringResource(R.string.settings_notif_comments),
                checked = settings.notifyComments,
                onCheckedChange = {
                    viewModel.setNotificationPref(SettingsRepository.NotificationPref.COMMENTS, it)
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_notif_status),
                checked = settings.notifyStatus,
                onCheckedChange = {
                    viewModel.setNotificationPref(SettingsRepository.NotificationPref.STATUS, it)
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_notif_votes),
                checked = settings.notifyVotes,
                onCheckedChange = {
                    viewModel.setNotificationPref(SettingsRepository.NotificationPref.VOTES, it)
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_notif_announcements),
                checked = settings.notifyAnnouncements,
                onCheckedChange = {
                    viewModel.setNotificationPref(
                        SettingsRepository.NotificationPref.ANNOUNCEMENTS,
                        it,
                    )
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_notif_chat),
                checked = settings.notifyChat,
                onCheckedChange = {
                    viewModel.setNotificationPref(SettingsRepository.NotificationPref.CHAT, it)
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.phone))

            // Whether your number may be read by your neighbours' phones.
            //
            // Only shown when the village has the feature on at all: a switch
            // that publishes nothing, because an administrator has switched
            // the whole thing off, would be asking for consent to nothing.
            if (smsConsent.enabled) {
                SwitchRow(
                    label = stringResource(R.string.sms_opt_in),
                    checked = smsConsent.shared,
                    onCheckedChange = viewModel::setSmsConsent,
                )
                Text(
                    text = stringResource(
                        if (smsConsent.shared) {
                            R.string.sms_opt_in_explain
                        } else {
                            R.string.sms_opt_in_off
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.page),
                )
            } else {
                Text(
                    text = stringResource(R.string.sms_opt_in_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.page),
                )
            }

            if (flags.isOn(Feature.CONTACTS)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(R.string.contacts_title))

                // A second way in. The first is the fire-risk card on the map,
                // which is where somebody reaches for a number in a hurry;
                // this is where they look for one calmly, and both go to the
                // same list.
                ActionRow(
                    label = stringResource(R.string.contacts_title),
                    onClick = onOpenContacts,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_account))

            if (viewModel.canChangePassword) {
                ActionRow(
                    label = stringResource(R.string.settings_change_password),
                    onClick = onChangePassword,
                )
            }
            ActionRow(
                label = stringResource(R.string.settings_delete_account),
                onClick = { showDeleteDialog = true },
                tint = MaterialTheme.colorScheme.errorInk,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_about))
            // The hidden door. Nothing marks this row as tappable — see
            // rememberTapUnlock for why that is the design and not an oversight.
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVersionTap,
                    )
                    .padding(horizontal = Space.page),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.errorInk,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primaryInk,
        modifier = Modifier.padding(horizontal = Space.page, vertical = 10.dp),
    )
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = Space.page, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Space.page, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun ActionRow(
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.page, vertical = 14.dp),
    )
}
