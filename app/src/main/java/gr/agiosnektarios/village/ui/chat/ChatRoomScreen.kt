package gr.agiosnektarios.village.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.ChatMessage
import gr.agiosnektarios.village.core.model.ChatType
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.ErrorBanner
import gr.agiosnektarios.village.ui.components.LoadingState
import gr.agiosnektarios.village.ui.components.timeOfDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    onBack: () -> Unit,
    viewModel: ChatRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // New message arriving while the room is open: scroll to it and clear the
    // unread badge, since the user is demonstrably reading.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
            viewModel.markRead()
        }
    }

    ChatRoomContent(
        state = state,
        listState = listState,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onLeave = { viewModel.leave(onBack) },
    )
}

/** Stateless, so the busiest screen in the app can be rendered off-device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLeave: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.chat?.displayTitle(state.currentUserId).orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.chat?.chatType == ChatType.GROUP) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_leave)) },
                                    onClick = {
                                        menuExpanded = false
                                        onLeave()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                // Above the composer, because the draft is restored on failure
                // and this explains why it came back.
                ErrorBanner(
                    message = state.errorMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                MessageComposer(
                    draft = state.draft,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            // Bottom-anchored, which is the single thing that separates a
            // conversation from a list of messages. `Arrangement.Bottom` only
            // takes effect when the content is shorter than the viewport, so a
            // short conversation sits on the composer instead of floating at
            // the top of an empty screen; once it overflows, this is a no-op
            // and normal scrolling takes over.
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        ) {
            items(state.messages, key = { it.id }) { message ->
                when {
                    message.isSystem -> SystemMessage(message)
                    else -> MessageBubble(
                        message = message,
                        isMine = message.senderId == state.currentUserId,
                        isGroup = state.chat?.chatType == ChatType.GROUP,
                    )
                }
            }
        }
    }
}

/**
 * Bubbles are asymmetric — the corner nearest the sender is squared off — which
 * is what makes a conversation readable at a glance without needing avatars on
 * every row.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isGroup: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMine && isGroup) {
            Avatar(
                bytes = null,
                initials = message.senderName.take(2).uppercase(),
                seed = message.senderId,
                size = 28.dp,
                modifier = Modifier.padding(end = 8.dp, top = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp,
                    ),
                )
                .background(
                    if (isMine) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!isMine && isGroup) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = timeOfDay(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun SystemMessage(message: ChatMessage) {
    Text(
        text = when (message.systemEvent) {
            "GROUP_CREATED" -> stringResource(R.string.chat_group_created, message.senderName)
            else -> message.text
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun MessageComposer(
    draft: String,
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
            placeholder = { Text(stringResource(R.string.chat_message_hint)) },
            shape = MaterialTheme.shapes.large,
            maxLines = 5,
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
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
