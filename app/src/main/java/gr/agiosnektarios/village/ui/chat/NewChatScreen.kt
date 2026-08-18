package gr.agiosnektarios.village.ui.chat

import gr.agiosnektarios.village.ui.theme.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.ErrorBanner
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField

/**
 * One screen for both conversation types: picking a single resident starts a
 * direct message, picking several turns it into a group and reveals the name
 * field. Making the user choose the type up front would be a decision they do
 * not have enough information to make yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onChatReady: (String) -> Unit,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdChatId) {
        state.createdChatId?.let(onChatReady)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_new)) },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VillageTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = stringResource(R.string.chat_search_people),
                leadingIcon = Icons.Filled.Search,
                modifier = Modifier.padding(horizontal = Space.page),
            )

            if (state.selected.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.selected, key = { it.id }) { profile ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleSelection(profile) },
                            label = { Text(profile.displayName) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.isGroup,
                enter = fadeIn() + expandVertically(),
            ) {
                VillageTextField(
                    value = state.groupTitle,
                    onValueChange = viewModel::onGroupTitleChange,
                    label = stringResource(R.string.chat_group_name),
                    modifier = Modifier.padding(horizontal = Space.page),
                )
            }

            // An empty picker is the normal state of a village that has just
            // opened its app, not a fault — but a blank list above a button
            // that refuses to press is indistinguishable from a broken screen,
            // so it says which of the two is happening.
            if (state.results.isEmpty()) {
                val searching = state.query.isNotBlank()
                EmptyState(
                    emoji = if (searching) "🔍" else "👋",
                    title = stringResource(
                        if (searching) R.string.chat_no_residents_match
                        else R.string.chat_no_residents_yet,
                    ),
                    subtitle = stringResource(
                        if (searching) R.string.chat_no_residents_match_hint
                        else R.string.chat_no_residents_yet_hint,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.results, key = { it.id }) { profile ->
                        val checked = state.selected.any { it.id == profile.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSelection(profile) }
                                .padding(horizontal = Space.page, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(
                                bytes = profile.avatarBytes,
                                initials = profile.initials,
                                seed = profile.id,
                                size = 42.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (profile.address.isNotBlank()) {
                                    Text(
                                        text = profile.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleSelection(profile) },
                            )
                        }
                    }
                }
            }

            ErrorBanner(
                message = state.errorMessage,
                modifier = Modifier.padding(horizontal = Space.page),
            )

            PrimaryButton(
                text = stringResource(R.string.action_create),
                onClick = viewModel::create,
                enabled = state.canCreate,
                loading = state.creating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.page)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
