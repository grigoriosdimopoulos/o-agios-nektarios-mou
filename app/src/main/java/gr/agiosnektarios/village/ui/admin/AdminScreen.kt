package gr.agiosnektarios.village.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.TagPill
import gr.agiosnektarios.village.ui.components.VillageTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenIssue: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_title)) },
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
        // The screen is only reachable from an admin-only entry point, but a
        // stale navigation or a demoted account would otherwise show the tools.
        if (!state.authorized && !state.loading) {
            EmptyState(
                emoji = "🔒",
                title = stringResource(R.string.admin_only),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.admin_users)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.admin_issues)) },
                )
            }

            when (selectedTab) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    VillageTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        label = stringResource(R.string.admin_search_users),
                        leadingIcon = Icons.Filled.Search,
                        modifier = Modifier.padding(16.dp),
                    )
                    LazyColumn {
                        items(state.residents, key = { it.id }) { resident ->
                            ResidentRow(
                                profile = resident,
                                onClick = { onOpenUser(resident.id) },
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.issues, key = { it.id }) { issue ->
                        IssueCard(
                            issue = issue,
                            onClick = { onOpenIssue(issue.id) },
                            showPhoto = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResidentRow(profile: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            photoUrl = profile.photoUrl,
            initials = profile.initials,
            seed = profile.id,
            size = 42.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = profile.email,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (profile.disabled) {
            TagPill(
                text = stringResource(R.string.admin_disable_user),
                tint = MaterialTheme.colorScheme.error,
            )
        } else if (profile.roleType != Role.USER) {
            TagPill(
                text = stringResource(
                    if (profile.isAdmin) {
                        R.string.profile_role_admin
                    } else {
                        R.string.profile_role_moderator
                    },
                ),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
