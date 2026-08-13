package gr.agiosnektarios.village.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.TagPill
import gr.agiosnektarios.village.ui.components.isGreekLocale
import gr.agiosnektarios.village.ui.theme.Motion

@Composable
fun ProfileScreen(
    onOpenIssue: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val greek = isGreekLocale()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (profile?.isAdmin == true) {
                    IconButton(onClick = onOpenAdmin) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = stringResource(R.string.admin_title),
                        )
                    }
                }
                IconButton(onClick = onEditProfile) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.profile_edit),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                    )
                }
                IconButton(onClick = viewModel::signOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.sign_out),
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(
                    photoUrl = profile?.photoUrl.orEmpty(),
                    initials = profile?.initials.orEmpty(),
                    seed = profile?.id.orEmpty(),
                    size = 96.dp,
                )
                Text(
                    text = profile?.displayName.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (profile != null && profile.roleType != Role.USER) {
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
                val blockName = if (greek) state.blockNameEl else state.blockNameEn
                val locationLine = listOfNotNull(
                    profile?.address?.takeIf { it.isNotBlank() },
                    blockName.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (locationLine.isNotBlank()) {
                    Text(
                        text = locationLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    value = state.myIssues.size,
                    label = stringResource(R.string.profile_stats_reports),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = state.resolvedCount,
                    label = stringResource(R.string.profile_stats_resolved),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = state.upvotesReceived,
                    label = stringResource(R.string.profile_stats_upvotes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.profile_my_issues),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        when {
            state.loading -> item { ListSkeleton(itemCount = 2) }
            state.myIssues.isEmpty() -> item {
                EmptyState(emoji = "📝", title = stringResource(R.string.issues_empty))
            }
            else -> items(state.myIssues, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    onClick = { onOpenIssue(issue.id) },
                    showPhoto = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Counters animate up from zero the first time the profile is drawn — small
 * enough to be a reward for contributing, brief enough not to delay reading.
 */
@Composable
private fun StatTile(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = Motion.slow(),
        label = "statTile",
    )

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = animated.toInt().toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
