package gr.agiosnektarios.village.ui.admin

import gr.agiosnektarios.village.ui.theme.Space
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
import gr.agiosnektarios.village.ui.theme.errorInk
import gr.agiosnektarios.village.ui.theme.secondaryInk
import androidx.compose.material3.Switch
import gr.agiosnektarios.village.core.model.Feature
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.ui.theme.rememberHaptics

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
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.admin_features)) },
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

                2 -> FeatureSwitches(
                    flags = state.flags,
                    onChange = viewModel::setFeature,
                )

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.issues, key = { it.id }) { issue ->
                        IssueCard(
                            issue = issue,
                            // Only the Reports list claims a report's shared-element key;
                            // two live elements on one key is an artefact waiting for a
                            // tab cross-fade. See IssueCard's shareKey.
                            shareKey = false,
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
internal fun ResidentRow(profile: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        // The badge sits *under* the name, not beside it.
        //
        // Beside it, the pill measured at its intrinsic width first and the
        // weighted column took what was left: at twice the text size in Greek
        // "Αναστολή λογαριασμού" took the whole row and the suspended
        // resident's name and address vanished entirely — an administrator
        // looking at the list could see that somebody was suspended and not
        // who. The others fared little better, breaking "Αναγνωστόπουλος"
        // across four lines mid-word.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = profile.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = profile.email,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (profile.disabled) {
                TagPill(
                    text = stringResource(R.string.admin_disable_user),
                    ink = MaterialTheme.colorScheme.errorInk,
                    modifier = Modifier.padding(top = 4.dp),
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
                    ink = MaterialTheme.colorScheme.secondaryInk,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * What this village uses, and what happens when it does not.
 *
 * Every switch says plainly what turning it off does, because an
 * administrator here is a neighbour who was handed a passphrase, not somebody
 * who has read the code. The one that publishes telephone numbers gets a
 * longer sentence than the rest and sits where it cannot be missed.
 */
@Composable
internal fun FeatureSwitches(
    flags: FeatureFlags,
    onChange: (Feature, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.admin_features_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(Feature.entries, key = { it.id }) { feature ->
            FeatureSwitch(
                feature = feature,
                on = flags.isOn(feature),
                // FIRE_RISK cannot be on without WEATHER, so its switch says
                // so by going flat rather than by silently disagreeing with
                // what the resident's screen shows.
                enabled = feature != Feature.FIRE_RISK || flags[Feature.WEATHER],
                onChange = { onChange(feature, it) },
            )
        }
    }
}

@Composable
private fun FeatureSwitch(
    feature: Feature,
    on: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(feature.labelRes),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(feature.explainRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = on,
            enabled = enabled,
            onCheckedChange = {
                // A warning, not a tick: this changes the app for the whole
                // village at once.
                haptics.warning()
                onChange(it)
            },
        )
    }
}
