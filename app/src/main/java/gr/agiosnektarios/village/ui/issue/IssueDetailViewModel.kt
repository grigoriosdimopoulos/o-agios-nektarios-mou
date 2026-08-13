package gr.agiosnektarios.village.ui.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.Comment
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.chat.ChatRepository
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IssueDetailUiState(
    val issue: Issue? = null,
    val comments: List<Comment> = emptyList(),
    val myVote: Int = 0,
    val viewer: UserProfile? = null,
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val commentDraft: String = "",
    val sendingComment: Boolean = false,
    val errorMessage: String? = null,
) {
    val canEdit: Boolean get() = issue?.canEdit(viewer) == true
    val canChangeStatus: Boolean get() = issue?.canChangeStatus(viewer) == true
    val isOwnIssue: Boolean get() = issue != null && issue.authorId == viewer?.id
}

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val issueRepository: IssueRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val issueId: String = savedStateHandle.get<String>("issueId").orEmpty()

    private val local = MutableStateFlow(LocalState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vote: StateFlow<Int> = sessionRepository.profile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(0)
            } else {
                issueRepository.observeMyVote(issueId, profile.id).catch { emit(0) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val uiState: StateFlow<IssueDetailUiState> = combine(
        issueRepository.observeIssue(issueId).catch { emit(null) },
        issueRepository.observeComments(issueId).catch { emit(emptyList()) },
        vote,
        sessionRepository.profile,
        local,
    ) { issue, comments, myVote, viewer, localState ->
        IssueDetailUiState(
            issue = issue,
            comments = comments,
            // An optimistic vote is shown until the server's value arrives, so
            // the button never appears to ignore the tap on a slow connection.
            myVote = localState.optimisticVote ?: myVote,
            viewer = viewer,
            loading = false,
            // The document disappearing means someone deleted it while we were
            // reading; the screen closes rather than showing an empty shell.
            deleted = issue == null && localState.hasLoadedOnce,
            commentDraft = localState.commentDraft,
            sendingComment = localState.sendingComment,
            errorMessage = localState.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueDetailUiState())

    init {
        viewModelScope.launch {
            issueRepository.observeIssue(issueId)
                .catch { }
                .collect { local.update { state -> state.copy(hasLoadedOnce = true) } }
        }
    }

    fun onCommentDraftChange(value: String) = local.update { it.copy(commentDraft = value) }

    fun castVote(value: Int) {
        val viewer = sessionRepository.currentProfile ?: return
        local.update { it.copy(optimisticVote = value) }
        viewModelScope.launch {
            val result = issueRepository.castVote(issueId, viewer.id, value)
            local.update {
                it.copy(
                    // Clearing the optimistic value hands control back to the
                    // server snapshot, which is authoritative either way.
                    optimisticVote = null,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun sendComment() {
        val viewer = sessionRepository.currentProfile ?: return
        val text = local.value.commentDraft.trim()
        if (text.isEmpty()) return

        local.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            val result = issueRepository.addComment(issueId, viewer, text)
            local.update {
                it.copy(
                    sendingComment = false,
                    commentDraft = if (result.isSuccess) "" else it.commentDraft,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch { issueRepository.deleteComment(issueId, commentId) }
    }

    fun setStatus(status: IssueStatus, note: String = "") {
        val viewer = sessionRepository.currentProfile ?: return
        viewModelScope.launch {
            val result = issueRepository.setStatus(issueId, status, viewer, note)
            result.exceptionOrNull()?.let { error ->
                local.update { it.copy(errorMessage = error.localizedMessage) }
            }
        }
    }

    fun deleteIssue(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = issueRepository.deleteIssue(issueId)
            if (result.isSuccess) {
                onDeleted()
            } else {
                local.update { it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage) }
            }
        }
    }

    /** Opens (or reuses) a direct conversation with whoever filed the report. */
    fun messageAuthor(onReady: (String) -> Unit) {
        val viewer = sessionRepository.currentProfile ?: return
        val authorId = uiState.value.issue?.authorId ?: return
        if (authorId == viewer.id) return
        viewModelScope.launch {
            val author = userRepository.getProfile(authorId).getOrNull() ?: return@launch
            chatRepository.openDirectChat(viewer, author)
                .onSuccess(onReady)
                .onFailure { error ->
                    local.update { it.copy(errorMessage = error.localizedMessage) }
                }
        }
    }

    fun consumeError() = local.update { it.copy(errorMessage = null) }

    private data class LocalState(
        val commentDraft: String = "",
        val sendingComment: Boolean = false,
        val optimisticVote: Int? = null,
        val hasLoadedOnce: Boolean = false,
        val errorMessage: String? = null,
    )
}
