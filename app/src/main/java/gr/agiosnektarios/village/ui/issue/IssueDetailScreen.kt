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
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Comment
import gr.agiosnektarios.village.core.model.IssuePhoto
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.ui.components.BytesImage
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.LoadingState
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.VoteBar
import gr.agiosnektarios.village.ui.components.relativeTime
import gr.agiosnektarios.village.ui.theme.raisedOutline
import gr.agiosnektarios.village.ui.theme.raisedContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.res.pluralStringResource
import gr.agiosnektarios.village.ui.map.MiniMap
import gr.agiosnektarios.village.ui.theme.Space
import androidx.compose.foundation.border
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.IssueTimeline
import androidx.compose.material.icons.filled.PanTool
import gr.agiosnektarios.village.ui.components.sharedElementOrNone
import gr.agiosnektarios.village.ui.components.sharedBoundsOrNone
import gr.agiosnektarios.village.ui.components.SharedKeys
import gr.agiosnektarios.village.ui.theme.primaryInk
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import gr.agiosnektarios.village.ui.theme.errorInk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onOpenChatWith: (String) -> Unit,
    onOpenMap: () -> Unit,
    showSnackbar: (String) -> Unit,
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
                photos = state.photos,
                viewer = state.viewer,
                myVote = state.myVote,
                onVote = viewModel::castVote,
                onDeleteComment = viewModel::deleteComment,
                contentPadding = padding,
                onToggleAssignment = viewModel::toggleAssignment,
                onOpenMap = onOpenMap,
                councilEmail = state.councilEmail,
                councilEnabled = state.councilEnabled,
                onRememberCouncilEmail = viewModel::rememberCouncilEmail,
                onRecordCouncil = viewModel::setCouncilReport,
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
                        color = MaterialTheme.colorScheme.errorInk,
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

/** Stateless already; internal so it can be rendered off-device. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssueDetailContent(
    issue: Issue,
    comments: List<Comment>,
    photos: List<IssuePhoto>,
    viewer: UserProfile?,
    myVote: Int,
    onVote: (Int) -> Unit,
    onDeleteComment: (String) -> Unit,
    contentPadding: PaddingValues,
    onToggleAssignment: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    councilEmail: String = "",
    /** Whether the village hands reports on to the municipality at all. */
    councilEnabled: Boolean = true,
    onRememberCouncilEmail: (String) -> Unit = {},
    onRecordCouncil: (String, Boolean) -> Unit = { _, _ -> },
) {
    var showCouncil by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        if (photos.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.page),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        BytesImage(
                            bytes = photo.bytes,
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

        // Where it is, before what was said about it.
        if (issue.lat != 0.0 || issue.lng != 0.0) {
            item {
                MiniMap(
                    issue = issue,
                    onOpenMap = onOpenMap,
                    modifier = Modifier.padding(
                        start = Space.page,
                        end = Space.page,
                        top = Space.gutter,
                    ),
                )
            }
        }

        item {
            // The same raised card the feed uses.
            //
            // This screen laid chips, title, description, author and votes as
            // raw text straight onto the page, with one grey rule under it. So
            // you tapped a crisp white card in the feed and arrived somewhere
            // with no card at all — the app changing its own vocabulary at the
            // exact moment it should be confirming it.
            Column(
                modifier = Modifier
                    .padding(horizontal = Space.page, vertical = Space.gutter)
                    .fillMaxWidth()
                    // The other half of the transition: the card you tapped
                    // grows into this. See SharedTransition.kt.
                    .sharedBoundsOrNone(SharedKeys.issueCard(issue.id))
                    .clip(MaterialTheme.shapes.medium)
                    .background(raisedContainer)
                    .then(
                        raisedOutline?.let { Modifier.border(it, MaterialTheme.shapes.medium) }
                            ?: Modifier,
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // FlowRow, like every other chip group in the app. As a Row
                // this crushed the status chip to two lines inside a circular
                // pill — "Σε / εξέλιξη" — at Greek one and a half times.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip(category = issue.category)
                    StatusChip(status = issue.status)
                }
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.sharedElementOrNone(SharedKeys.issueTitle(issue.id)),
                )
                if (issue.description.isNotBlank()) {
                    Text(
                        text = issue.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // The timeline replaces the "reported by X on date" line: it
                // already says that, and it also says who took the job on and
                // when it was cleared, which a single attribution line cannot.
                IssueTimeline(issue = issue)

                TakeItOn(
                    issue = issue,
                    viewer = viewer,
                    onToggle = onToggleAssignment,
                )

                // Getting it in front of somebody whose job it is.
                //
                // Anybody may forward a report — it is their own email, over
                // their own name. Recording that it *is* with the municipality
                // is a moderator's act, because the whole village reads it and
                // then stops sending it again.
                if (councilEnabled) CouncilRow(
                    issue = issue,
                    onSend = { showCouncil = true },
                )

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

        item {
            Text(
                text = stringResource(R.string.issue_comments),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Space.page, vertical = 12.dp),
            )
        }

        if (comments.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.issue_comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.page),
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

    if (showCouncil) {
        CouncilSheet(
            issue = issue,
            savedEmail = councilEmail,
            canRecord = viewer?.canModerate == true,
            onRememberEmail = onRememberCouncilEmail,
            onRecord = onRecordCouncil,
            onDismiss = { showCouncil = false },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            bytes = null,
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
            // 48dp, the minimum for anything a finger has to hit. The icon
            // inside stays small; it is the target that has to be big. This
            // was 32 — the same defect just fixed on the send button.
            IconButton(onClick = { confirming = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Deleting the report itself opens a dialog; deleting a comment went
    // straight through, on a 48dp target beside the text you were reading.
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.comment_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
                    MaterialTheme.colorScheme.primaryInk
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
                        color = MaterialTheme.colorScheme.errorInk,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.errorInk,
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

/**
 * The button that actually moves things.
 *
 * A village of two hundred houses does not need a workflow; it needs somebody
 * say out loud that they will deal with it. "Someone should clear that" is how
 * a fallen branch sits for a year. A name against the job is how it gets
 * cleared, and the app was recording every status change and none of that.
 *
 * Deliberately one control with three faces rather than a menu: take it if
 * nobody has, hand it back if you hold it, and if a neighbour holds it, say so
 * and offer nothing — there is nothing for a bystander to do here.
 */
@Composable
private fun CouncilRow(issue: Issue, onSend: () -> Unit) {
    val sentDays = CouncilHandoff.daysSince(issue.reportedToCouncilAt)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SecondaryButton(
            text = stringResource(R.string.council_send),
            onClick = onSend,
            icon = Icons.AutoMirrored.Filled.Send,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sentDays != null) {
            Text(
                text = buildString {
                    append(stringResource(R.string.council_sent))
                    append(" · ")
                    append(pluralStringResource(R.plurals.council_waiting, sentDays, sentDays))
                    if (issue.councilReference.isNotBlank()) {
                        append(" · ").append(issue.councilReference)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primaryInk,
            )
        }
    }
}

@Composable
private fun TakeItOn(issue: Issue, viewer: UserProfile?, onToggle: () -> Unit) {
    // Putting your name against a job is the loudest thing a resident can do
    // here short of raising an alarm, so it is the firm tick rather than the
    // light one. Handing it back is the light one: it undoes rather than
    // promises.
    val haptics = rememberHaptics()
    when {
        issue.canTake(viewer) -> SecondaryButton(
            text = stringResource(R.string.issue_take_on),
            onClick = { haptics.committed(); onToggle() },
            icon = Icons.Filled.PanTool,
            modifier = Modifier.fillMaxWidth(),
        )
        issue.isTakenBy(viewer) -> SecondaryButton(
            text = stringResource(R.string.issue_release),
            onClick = { haptics.tick(); onToggle() },
            modifier = Modifier.fillMaxWidth(),
        )
        issue.isTaken -> Text(
            text = stringResource(R.string.issue_taken_by, issue.assigneeName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primaryInk,
        )
        else -> Unit
    }
}
