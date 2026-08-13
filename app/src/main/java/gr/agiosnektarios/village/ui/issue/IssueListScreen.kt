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
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.theme.Motion

@Composable
fun IssueListScreen(
    onOpenIssue: (String) -> Unit,
    viewModel: IssueListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.issues_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            SortMenu(current = state.sort, onSelect = viewModel::onSortChange)
        }

        VillageTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = stringResource(R.string.issues_search),
            leadingIcon = Icons.Filled.Search,
            imeAction = ImeAction.Search,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(IssueStatus.entries.toList()) { status ->
                StatusChip(
                    status = status,
                    selected = status in state.statuses,
                    onClick = { viewModel.toggleStatus(status) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(IssueCategory.entries.toList()) { category ->
                CategoryChip(
                    category = category,
                    selected = category in state.categories,
                    onClick = { viewModel.toggleCategory(category) },
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexedStaggered(state.issues.map { it.id }) { index, _ ->
                    val issue = state.issues[index]
                    IssueCard(issue = issue, onClick = { onOpenIssue(issue.id) })
                }
            }
        }
    }
}

/**
 * Items fade and rise in sequence the first time they appear.
 *
 * The stagger is capped at [Motion.MAX_STAGGERED_ITEMS] so a long list does not
 * take a second to finish arriving — beyond the first screenful the effect is
 * invisible anyway, and paying for it would just delay content.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedStaggered(
    keys: List<String>,
    content: @Composable (index: Int, key: String) -> Unit,
) {
    items(count = keys.size, key = { keys[it] }) { index ->
        val visible: MutableState<Boolean> = remember(keys[index]) { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(keys[index]) {
            kotlinx.coroutines.delay(
                (index.coerceAtMost(Motion.MAX_STAGGERED_ITEMS) * Motion.STAGGER_MS).toLong(),
            )
            visible.value = true
        }
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 },
        ) {
            content(index, keys[index])
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
