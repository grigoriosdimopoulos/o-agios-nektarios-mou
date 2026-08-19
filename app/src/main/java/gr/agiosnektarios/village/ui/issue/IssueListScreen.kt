package gr.agiosnektarios.village.ui.issue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.data.issue.IssueSort
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.fadingEdges
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.theme.Space
import androidx.compose.material3.minimumInteractiveComponentSize
import gr.agiosnektarios.village.ui.theme.primaryInk

@Composable
fun IssueListScreen(
    onOpenIssue: (String) -> Unit,
    viewModel: IssueListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IssueListContent(
        state = state,
        onOpenIssue = onOpenIssue,
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onToggleStatus = viewModel::toggleStatus,
        onToggleCategory = viewModel::toggleCategory,
        onClearFilters = viewModel::clearFilters,
    )
}

/**
 * The screen with its state handed in rather than collected.
 *
 * Split out so it can be rendered without Hilt, a repository or a device.
 * Every screen-level defect found so far — chrome the colour of the page,
 * screens showing through each other — was invisible while the only things
 * that could be rendered were individual components.
 */
@Composable
internal fun IssueListContent(
    state: IssueListUiState,
    onOpenIssue: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (IssueSort) -> Unit,
    onToggleStatus: (IssueStatus) -> Unit,
    onToggleCategory: (IssueCategory) -> Unit,
    onClearFilters: () -> Unit = {},
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.issues_title),
            actions = { SortMenu(current = state.sort, onSelect = onSortChange) },
        )

        VillageTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.issues_search),
            leadingIcon = Icons.Filled.Search,
            imeAction = ImeAction.Search,
            modifier = Modifier.padding(horizontal = Space.page),
        )

        // One button, not two rows of chips.
        //
        // Twenty chips over two horizontally scrolling rows sat permanently
        // above the content and, with the search field, took a fifth of the
        // screen before the first report. They also always ran off the right
        // edge — four statuses and sixteen categories never fit — so a word was
        // cut in half at rest, whatever the fade did to soften it. The map
        // already put exactly these filters behind one control, and this is the
        // same set.
        FilterBar(
            active = state.statuses.size + state.categories.size,
            onClick = { showFilters = true },
            modifier = Modifier.padding(horizontal = Space.page, vertical = 8.dp),
        )

        if (showFilters) {
            IssueFilterSheet(
                statuses = state.statuses,
                categories = state.categories,
                onToggleStatus = onToggleStatus,
                onToggleCategory = onToggleCategory,
                onClear = onClearFilters,
                onDismiss = { showFilters = false },
            )
        }

        when {
            state.loading -> ListSkeleton(modifier = Modifier.fillMaxWidth())
            state.issues.isEmpty() -> EmptyState(
                emoji = if (state.hasFilters) "🔍" else "🌿",
                title = stringResource(
                    if (state.hasFilters) {
                        R.string.issues_empty_filtered
                    } else {
                        R.string.issues_empty
                    },
                ),
                // A filtered-empty screen with no button is a dead end: the
                // reason there is nothing here is the filters, and the filters
                // are behind a bar the reader has just scrolled away from.
                action = if (state.hasFilters) {
                    {
                        SecondaryButton(
                            text = stringResource(R.string.map_filter_clear),
                            onClick = onClearFilters,
                        )
                    }
                } else {
                    null
                },
            )
            else -> LazyColumn(
                // Clears the overlaid navigation bar; see BottomBarDefaults.
                contentPadding = PaddingValues(
                    start = Space.page,
                    end = Space.page,
                    top = Space.gutter,
                    bottom = BottomBarDefaults.contentPadding() + Space.page,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.gutter),
            ) {
                // No entrance animation, deliberately.
                //
                // Rows used to fade and rise in sequence. The mechanism was
                // AnimatedVisibility(visible = false) until a delayed effect
                // flipped it — and a hidden AnimatedVisibility composes nothing
                // and measures 0x0. So a row you had not seen yet was zero
                // pixels tall for up to 300ms, LazyColumn kept composing the
                // next row to fill the space, that one was zero-height too, and
                // a fast scroll produced a run of empty rows that popped to
                // full height together while the content height lurched under
                // your thumb. Hoisting the "already arrived" flag out of the
                // item fixed the *re*-animation on recycle; it could not fix
                // this, because the first pass through any row still starts
                // hidden.
                //
                // A list that is simply there beats a list that performs, and
                // this is the screen a resident opens most. The motion budget
                // is better spent on the push between screens, where nothing
                // is waiting on it.
                items(state.issues, key = { it.id }) { issue ->
                    // Moves rather than teleports.
                    //
                    // A report changing status changes where it sorts, and the
                    // list used to answer that by having the row simply be
                    // somewhere else on the next frame — which is exactly the
                    // moment continuity matters, because something was just
                    // fixed. `animateItem` needs the stable key the list
                    // already supplies.
                    IssueCard(
                        issue = issue,
                        onClick = { onOpenIssue(issue.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: IssueSort, onSelect: (IssueSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        IssueSort.NEWEST to R.string.issue_sort_newest,
        IssueSort.TOP to R.string.issue_sort_top,
        IssueSort.MOST_DISCUSSED to R.string.issue_sort_discussed,
    )

    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.issue_sort),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (sort, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                    trailingIcon = {
                        if (sort == current) Text("✓", style = MaterialTheme.typography.labelMedium)
                    },
                )
            }
        }
    }
}

/**
 * "Φίλτρα", and how many are on.
 *
 * The count is what makes a collapsed control honest: a filter you cannot see
 * is a filter you forget you set, and then the list looks broken.
 */
@Composable
private fun FilterBar(active: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active > 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active > 0) {
                MaterialTheme.colorScheme.primaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = if (active > 0) {
                stringResource(R.string.issues_filters_active, active)
            } else {
                stringResource(R.string.issues_filters)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (active > 0) {
                MaterialTheme.colorScheme.primaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** The twenty chips, on a pane, where there is room for all of them at once. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IssueFilterSheet(
    statuses: Set<IssueStatus>,
    categories: Set<IssueCategory>,
    onToggleStatus: (IssueStatus) -> Unit,
    onToggleCategory: (IssueCategory) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.map_filter_statuses),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IssueStatus.entries.forEach { status ->
                    StatusChip(
                        status = status,
                        selected = status in statuses,
                        onClick = { onToggleStatus(status) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.map_filter_categories),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IssueCategory.entries.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category in categories,
                        onClick = { onToggleCategory(category) },
                    )
                }
            }

            SecondaryButton(
                text = stringResource(R.string.map_filter_clear),
                onClick = onClear,
                enabled = statuses.isNotEmpty() || categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
