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
import gr.agiosnektarios.village.ui.components.fadingEdge
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
    /** Rows already shown. Pre-fill it to render the list in its settled state. */
    revealed: MutableSet<String> = rememberRevealedRows(),
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
        LazyRow(
            modifier = Modifier.fadingEdge(),
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

        LazyRow(
            modifier = Modifier.fadingEdge(),
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
                itemsIndexedStaggered(state.issues.map { it.id }, revealed) { index, _ ->
                    val issue = state.issues[index]
                    IssueCard(issue = issue, onClick = { onOpenIssue(issue.id) })
                }
            }
        }
    }
}

/** Survives item recycling, because it lives above the list rather than in it. */
@Composable
private fun rememberRevealedRows(): MutableSet<String> =
    // A snapshot-backed list wrapped as a set: adding a key has to trigger
    // recomposition of the row watching it, which a plain mutableSetOf does not.
    remember { androidx.compose.runtime.mutableStateListOf<String>().asMutableSet() }

/** Minimal set view over a snapshot list — the app only ever adds and tests. */
private fun androidx.compose.runtime.snapshots.SnapshotStateList<String>.asMutableSet():
    MutableSet<String> = object : AbstractMutableSet<String>() {
        override val size: Int get() = this@asMutableSet.size
        override fun iterator(): MutableIterator<String> = this@asMutableSet.iterator()
        override fun add(element: String): Boolean =
            if (this@asMutableSet.contains(element)) false else this@asMutableSet.add(element)
    }

/**
 * Items fade and rise in sequence the first time the list appears.
 *
 * "The first time the list appears" is the whole difficulty, and the previous
 * version got it wrong in a way that is worse than having no animation at all.
 * It held the visibility flag in `remember(key)` *inside* the item — and a
 * LazyColumn destroys an item's composition as soon as it scrolls off. Scroll
 * down and back and the flag was gone, so every row faded and slid in again,
 * every time, for the life of the screen. A list whose rows animate on every
 * scroll does not read as polish; it reads as a list that cannot keep up.
 *
 * The set of rows that have already arrived is therefore held *above* the
 * LazyColumn, where recycling cannot touch it. Each row animates exactly once.
 *
 * [revealed] is hoisted for a second reason: at frame zero every row is
 * invisible, so a screenshot of this screen is a blank page — which is exactly
 * how the empty list was found. A caller that wants the settled state passes
 * a pre-filled set.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedStaggered(
    keys: List<String>,
    revealed: MutableSet<String>,
    content: @Composable (index: Int, key: String) -> Unit,
) {
    items(count = keys.size, key = { keys[it] }) { index ->
        val key = keys[index]
        val alreadyHere = key in revealed
        androidx.compose.runtime.LaunchedEffect(key) {
            if (!alreadyHere) {
                kotlinx.coroutines.delay(
                    (index.coerceAtMost(Motion.MAX_STAGGERED_ITEMS) * Motion.STAGGER_MS).toLong(),
                )
                revealed.add(key)
            }
        }
        AnimatedVisibility(
            visible = key in revealed,
            enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 },
        ) {
            content(index, key)
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
