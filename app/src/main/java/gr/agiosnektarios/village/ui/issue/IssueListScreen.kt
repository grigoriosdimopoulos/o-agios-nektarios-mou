package gr.agiosnektarios.village.ui.issue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.MutableState
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
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.components.fadingEdges
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.theme.Space

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
) {
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

        // Both chip rows run off the right edge at scroll 0 — 4 statuses and
        // 16 categories never fit — so a word is always cut in half at x=max.
        // contentPadding cannot help; it only applies past the end of the
        // content. A fade at the edge turns a severed word into an obvious
        // "keep scrolling", which is what every platform does here.
        val statusRow = rememberLazyListState()
        LazyRow(
            state = statusRow,
            modifier = Modifier.fadingEdges(statusRow),
            contentPadding = PaddingValues(horizontal = Space.page, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(IssueStatus.entries.toList()) { status ->
                StatusChip(
                    status = status,
                    selected = status in state.statuses,
                    onClick = { onToggleStatus(status) },
                )
            }
        }

        val categoryRow = rememberLazyListState()
        LazyRow(
            state = categoryRow,
            modifier = Modifier.fadingEdges(categoryRow),
            contentPadding = PaddingValues(horizontal = Space.page),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(IssueCategory.entries.toList()) { category ->
                CategoryChip(
                    category = category,
                    selected = category in state.categories,
                    onClick = { onToggleCategory(category) },
                )
            }
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
                    IssueCard(issue = issue, onClick = { onOpenIssue(issue.id) })
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
