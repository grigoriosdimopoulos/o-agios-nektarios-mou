package gr.agiosnektarios.village.ui.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.UserMessages
import gr.agiosnektarios.village.core.model.Comment
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.ui.navigation.DeepLinks
import gr.agiosnektarios.village.core.model.IssuePhoto
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
    /** Loaded only for this screen: full photos are far too large to carry in a list. */
    val photos: List<IssuePhoto> = emptyList(),
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
    private val notificationRepository: NotificationRepository,
    private val messages: UserMessages,
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

    /** Paired so the five-argument combine below still fits. */
    private val commentsAndPhotos = combine(
        issueRepository.observeComments(issueId),
        issueRepository.observePhotos(issueId),
    ) { comments, photos -> comments to photos }

    val uiState: StateFlow<IssueDetailUiState> = combine(
        issueRepository.observeIssue(issueId),
        commentsAndPhotos,
        vote,
        sessionRepository.profile,
        local,
    ) { issue, (comments, photos), myVote, viewer, localState ->
        IssueDetailUiState(
            issue = issue,
            comments = comments,
            photos = photos,
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
            // Only support is worth telling someone about. A downvote arriving
            // as a notification would make disagreeing feel like an attack.
            if (result.isSuccess && value == 1) notifyAuthorOfVote(viewer)
            local.update {
                it.copy(
                    // Clearing the optimistic value hands control back to the
                    // server snapshot, which is authoritative either way.
                    optimisticVote = null,
                    errorMessage = messages.of(result.exceptionOrNull()),
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
            if (result.isSuccess) {
                notifyAuthor(
                    actor = viewer,
                    type = NotificationType.COMMENT,
                    bodyKey = "notif_new_comment",
                )
            }
            local.update {
                it.copy(
                    sendingComment = false,
                    commentDraft = if (result.isSuccess) "" else it.commentDraft,
                    errorMessage = messages.of(result.exceptionOrNull()),
                )
            }
        }
    }

    /**
     * Tells the report's author what just happened to their report.
     *
     * The repository is left alone: notifying is a product decision about who
     * cares, not part of writing a comment, and the notification is best-effort
     * — a comment that saved must not fail because a notice did not.
     */
    private suspend fun notifyAuthor(
        actor: UserProfile,
        type: NotificationType,
        bodyKey: String,
        bodyArg: String = "",
    ) {
        val issue = uiState.value.issue ?: return
        notificationRepository.notify(
            recipientIds = listOf(issue.authorId),
            actorId = actor.id,
            type = type,
            title = issue.title,
            bodyKey = bodyKey,
            bodyArg = bodyArg,
            deepLink = DeepLinks.issue(issueId),
            // One notice per report per kind, replaced rather than stacked:
            // five comments should not mean five lines in the shade.
            collapseKey = "${type.id}:$issueId",
        )
    }

    private suspend fun notifyAuthorOfVote(actor: UserProfile) {
        val issue = uiState.value.issue ?: return
        notifyAuthor(
            actor = actor,
            type = NotificationType.VOTE,
            bodyKey = "notif_upvotes",
            // Reads from the live document, so the notice says the running
            // total rather than "somebody voted" over and over.
            bodyArg = (issue.upvotes + 1).toString(),
        )
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch { issueRepository.deleteComment(issueId, commentId) }
    }

    fun setStatus(status: IssueStatus, note: String = "") {
        val viewer = sessionRepository.currentProfile ?: return
        viewModelScope.launch {
            val result = issueRepository.setStatus(issueId, status, viewer, note)
            if (result.isSuccess) {
                notifyAuthor(
                    actor = viewer,
                    type = NotificationType.STATUS,
                    bodyKey = when (status) {
                        IssueStatus.RESOLVED -> "notif_status_resolved"
                        IssueStatus.WONT_DO -> "notif_status_wont_do"
                        else -> "notif_status_changed"
                    },
                )
            }
            result.exceptionOrNull()?.let { error ->
                local.update { it.copy(errorMessage = messages.of(error)) }
            }
        }
    }

    /**
     * Take the report on, or hand it back.
     *
     * Taking also moves an untouched report to IN_PROGRESS, because those two
     * things mean the same thing to everyone reading the list and asking the
     * resident to do them separately is asking them to do bookkeeping. Handing
     * it back leaves the status alone — work may well have happened.
     */
    fun toggleAssignment() {
        val issue = uiState.value.issue ?: return
        val me = sessionRepository.currentProfile ?: return
        val taking = !issue.isTakenBy(me)
        viewModelScope.launch {
            issueRepository.setAssignee(issueId, if (taking) me else null)
                .onSuccess {
                    if (taking && issue.status == IssueStatus.OPEN) {
                        issueRepository.setStatus(issueId, IssueStatus.IN_PROGRESS, me, "")
                    }
                }
                .onFailure { error -> local.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun deleteIssue(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = issueRepository.deleteIssue(issueId)
            if (result.isSuccess) {
                onDeleted()
            } else {
                local.update { it.copy(errorMessage = messages.of(result.exceptionOrNull())) }
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
                    local.update { it.copy(errorMessage = messages.of(error)) }
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
