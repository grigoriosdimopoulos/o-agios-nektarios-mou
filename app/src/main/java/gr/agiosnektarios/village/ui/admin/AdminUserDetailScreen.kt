package gr.agiosnektarios.village.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    viewModel: AdminUserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val resetSentMessage = stringResource(R.string.admin_reset_password_sent)

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.message, state.errorMessage) {
        (state.errorMessage ?: state.message)?.let {
            showSnackbar(it)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName.orEmpty()) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.working) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(
                        photoUrl = profile?.photoUrl.orEmpty(),
                        initials = profile?.initials.orEmpty(),
                        seed = profile?.id.orEmpty(),
                        size = 84.dp,
                    )
                    Text(
                        text = profile?.displayName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = profile?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOfNotNull(
                        profile?.phone?.takeIf { it.isNotBlank() },
                        profile?.address?.takeIf { it.isNotBlank() },
                    ).forEach {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.admin_set_role),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Role.entries.forEach { role ->
                            FilterChip(
                                selected = profile?.roleType == role,
                                onClick = { viewModel.setRole(role) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (role) {
                                                Role.ADMIN -> R.string.profile_role_admin
                                                Role.MODERATOR -> R.string.profile_role_moderator
                                                Role.USER -> R.string.profile_role_user
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.admin_rename_user),
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.admin_reset_password),
                        onClick = { viewModel.sendPasswordReset(resetSentMessage) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(
                            if (profile?.disabled == true) {
                                R.string.admin_enable_user
                            } else {
                                R.string.admin_disable_user
                            },
                        ),
                        onClick = { viewModel.setDisabled(profile?.disabled != true) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.admin_delete_user),
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(16.dp)) }

            item {
                Text(
                    text = stringResource(R.string.profile_my_issues),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            items(state.issues, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    onClick = { onOpenIssue(issue.id) },
                    showPhoto = false,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showRenameDialog && profile != null) {
        var first by remember { mutableStateOf(profile.firstName) }
        var last by remember { mutableStateOf(profile.lastName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.admin_rename_user)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        label = { Text(stringResource(R.string.first_name)) },
                    )
                    OutlinedTextField(
                        value = last,
                        onValueChange = { last = it },
                        label = { Text(stringResource(R.string.last_name)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        viewModel.rename(first, last)
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.admin_delete_user)) },
            text = {
                Text(
                    stringResource(
                        R.string.admin_delete_user_confirm,
                        profile?.displayName.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteUser()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
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
