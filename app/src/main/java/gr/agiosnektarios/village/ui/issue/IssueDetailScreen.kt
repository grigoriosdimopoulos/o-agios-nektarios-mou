package gr.agiosnektarios.village.ui.issue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Comment
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.LoadingState
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.VoteBar
import gr.agiosnektarios.village.ui.components.absoluteDateTime
import gr.agiosnektarios.village.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onOpenChatWith: (String) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.issue_deleted)

    // Someone else deleting the report while it is open closes the screen
    // rather than leaving a dangling view of a document that no longer exists.
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            showSnackbar(deletedMessage)
            onDeleted()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val issue = state.issue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (issue != null && !state.isOwnIssue) {
                        IconButton(onClick = { viewModel.messageAuthor(onOpenChatWith) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = stringResource(R.string.chat_new_direct),
                            )
                        }
                    }
                    if (state.canEdit && issue != null) {
                        IssueOverflowMenu(
                            onEdit = { onEdit(issue.id) },
                            onDelete = { showDeleteConfirm = true },
                            onChangeStatus = { showStatusDialog = true },
                            canChangeStatus = state.canChangeStatus,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (issue != null) {
                CommentComposer(
                    draft = state.commentDraft,
                    sending = state.sendingComment,
                    onDraftChange = viewModel::onCommentDraftChange,
                    onSend = viewModel::sendComment,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            issue == null -> Box(modifier = Modifier.fillMaxSize())
            else -> IssueDetailContent(
                issue = issue,
                comments = state.comments,
                viewer = state.viewer,
                myVote = state.myVote,
                onVote = viewModel::castVote,
                onDeleteComment = viewModel::deleteComment,
                contentPadding = padding,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.issue_delete_confirm_title)) },
            text = { Text(stringResource(R.string.issue_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteIssue(onDeleted)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showStatusDialog && issue != null) {
        StatusDialog(
            current = issue.status,
            canModerate = state.viewer?.canModerate == true,
            onDismiss = { showStatusDialog = false },
            onConfirm = { status, note ->
                showStatusDialog = false
                viewModel.setStatus(status, note)
            },
        )
    }
}

@Composable
private fun IssueDetailContent(
    issue: Issue,
    comments: List<Comment>,
    viewer: UserProfile?,
    myVote: Int,
    onVote: (Int) -> Unit,
    onDeleteComment: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        if (issue.photoUrls.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(issue.photoUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .height(210.dp)
                                .width(280.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip(category = issue.category)
                    StatusChip(status = issue.status)
                }
                Text(text = issue.title, style = MaterialTheme.typography.headlineSmall)
                if (issue.description.isNotBlank()) {
                    Text(
                        text = issue.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(
                        photoUrl = issue.authorPhotoUrl,
                        initials = issue.authorName.take(2).uppercase(),
                        seed = issue.authorId,
                        size = 36.dp,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.issue_reported_by, issue.authorName),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = absoluteDateTime(issue.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (issue.status.isTerminal && issue.resolutionNote.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(issue.status.tint.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = issue.resolutionNote,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (issue.resolvedByName.isNotBlank()) {
                            Text(
                                text = issue.resolvedByName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                VoteBar(
                    upvotes = issue.upvotes,
                    downvotes = issue.downvotes,
                    myVote = myVote,
                    onVote = onVote,
                    enabled = viewer != null,
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

        item {
            Text(
                text = stringResource(R.string.issue_comments),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (comments.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.issue_comments_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(comments, key = { it.id }) { comment ->
                CommentRow(
                    comment = comment,
                    canDelete = comment.canDelete(viewer),
                    onDelete = { onDeleteComment(comment.id) },
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            photoUrl = comment.authorPhotoUrl,
            initials = comment.authorName.take(2).uppercase(),
            seed = comment.authorId,
            size = 34.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommentComposer(
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.issue_comment_hint)) },
            shape = MaterialTheme.shapes.large,
            maxLines = 4,
        )
        IconButton(
            onClick = onSend,
            enabled = draft.isNotBlank() && !sending,
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = stringResource(R.string.action_send),
                tint = if (draft.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun IssueOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onChangeStatus: () -> Unit,
    canChangeStatus: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            if (canChangeStatus) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.issue_change_status)) },
                    onClick = {
                        expanded = false
                        onChangeStatus()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Status picker.
 *
 * Residents see only the states that make sense for their own report
 * (resolved / won't do / reopen); moderators additionally get the
 * work-in-progress states used to communicate that the municipality has picked
 * the report up.
 */
@Composable
private fun StatusDialog(
    current: IssueStatus,
    canModerate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (IssueStatus, String) -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    var note by remember { mutableStateOf("") }
    val options = if (canModerate) {
        IssueStatus.entries.toList()
    } else {
        IssueStatus.authorSelectable
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.issue_change_status)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { status ->
                    StatusChip(
                        status = status,
                        selected = status == selected,
                        onClick = { selected = status },
                    )
                }
                if (selected.isTerminal) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.issue_resolution_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, note) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
