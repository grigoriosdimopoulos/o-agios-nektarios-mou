package gr.agiosnektarios.village.ui.issue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.issue.IssueSort
import gr.agiosnektarios.village.data.issue.sortedForDisplay
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class IssueListUiState(
    val issues: List<Issue> = emptyList(),
    val query: String = "",
    val sort: IssueSort = IssueSort.NEWEST,
    val categories: Set<IssueCategory> = emptySet(),
    val statuses: Set<IssueStatus> = emptySet(),
    val loading: Boolean = true,
) {
    val hasFilters: Boolean
        get() = query.isNotBlank() || categories.isNotEmpty() || statuses.isNotEmpty()
}

@HiltViewModel
class IssueListViewModel @Inject constructor(
    issueRepository: IssueRepository,
) : ViewModel() {

    private data class Criteria(
        val query: String = "",
        val sort: IssueSort = IssueSort.NEWEST,
        val categories: Set<IssueCategory> = emptySet(),
        val statuses: Set<IssueStatus> = emptySet(),
    )

    private val criteria = MutableStateFlow(Criteria())

    val uiState: StateFlow<IssueListUiState> = combine(
        issueRepository.observeIssues(),
        criteria,
    ) { issues, current ->
        // Search runs locally over the already-live list: a village's worth of
        // reports fits comfortably in memory, and this keeps typing instant and
        // working offline, which a Firestore text query would not.
        val needle = current.query.trim().lowercase()
        val filtered = issues.filter { issue ->
            (current.categories.isEmpty() || issue.category in current.categories) &&
                (current.statuses.isEmpty() || issue.status in current.statuses) &&
                (
                    needle.isEmpty() ||
                        issue.title.lowercase().contains(needle) ||
                        issue.description.lowercase().contains(needle) ||
                        issue.authorName.lowercase().contains(needle)
                    )
        }
        IssueListUiState(
            issues = filtered.sortedForDisplay(current.sort),
            query = current.query,
            sort = current.sort,
            categories = current.categories,
            statuses = current.statuses,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueListUiState())

    fun onQueryChange(value: String) = criteria.update { it.copy(query = value) }

    fun onSortChange(sort: IssueSort) = criteria.update { it.copy(sort = sort) }

    fun toggleCategory(category: IssueCategory) = criteria.update { current ->
        current.copy(
            categories = if (category in current.categories) {
                current.categories - category
            } else {
                current.categories + category
            },
        )
    }

    fun toggleStatus(status: IssueStatus) = criteria.update { current ->
        current.copy(
            statuses = if (status in current.statuses) {
                current.statuses - status
            } else {
                current.statuses + status
            },
        )
    }

    fun clearFilters() = criteria.update { Criteria(sort = it.sort) }
}
