package gr.agiosnektarios.village.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Chat
import gr.agiosnektarios.village.core.model.ChatType
import gr.agiosnektarios.village.ui.components.Avatar
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.relativeTime
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.theme.Space

@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatsContent(state = state, onOpenChat = onOpenChat, onNewChat = onNewChat)
}

/** Stateless, so the screen can be rendered without Hilt or a device. */
@Composable
internal fun ChatsContent(
    state: ChatsUiState,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                // Explicit, not M3's default primaryContainer: that is a pale
                // mint which measures 1.26:1 against the cream page, so the
                // only way to start a conversation looked like a disabled
                // control. This is the same green as every other primary
                // action in the app.
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // Clears the navigation bar, which is drawn over this screen
                // rather than occupying a slot beside it.
                modifier = Modifier.padding(bottom = BottomBarDefaults.contentPadding()),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.chat_new),
                )
            }
        },
        // Only the top inset belongs to this Scaffold: the app's navigation
        // bar is drawn over the screen, so its height is added by whatever
        // must clear it rather than reserved here.
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = stringResource(R.string.chat_title))

            when {
                state.loading -> ListSkeleton()
                state.chats.isEmpty() -> EmptyState(
                    emoji = "💬",
                    title = stringResource(R.string.chat_empty),
                )
                else -> LazyColumn(
                    // Clears the overlaid navigation bar. A constant, not the
                    // Scaffold's padding: the bar is drawn over the content
                    // rather than measured beside it, so nothing reports its
                    // height and nothing resizes when it slides away.
                    contentPadding = PaddingValues(
                        bottom = BottomBarDefaults.contentPadding(),
                    ),
                ) {
                    itemsIndexed(state.chats, key = { _, chat -> chat.id }) { index, chat ->
                        ChatRow(
                            chat = chat,
                            currentUserId = state.currentUserId,
                            onClick = { onOpenChat(chat.id) },
                        )
                        // Inset separator, starting where the text starts
                        // rather than at the screen edge, and absent after the
                        // last row. Without it the conversations float on the
                        // page with nothing to say where one ends and the next
                        // begins — which is the difference between a list and
                        // some text that happens to be stacked.
                        if (index < state.chats.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = Space.separatorInset),
                                thickness = Dp.Hairline,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    currentUserId: String,
    onClick: () -> Unit,
) {
    val unread = chat.unreadFor(currentUserId)
    val title = chat.displayTitle(currentUserId).ifBlank {
        stringResource(R.string.chat_new_group)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.page, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (chat.chatType == ChatType.GROUP) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    bytes = null,
                    initials = title.take(2).uppercase(),
                    seed = chat.id,
                    size = 48.dp,
                )
            }
        } else {
            Avatar(
                bytes = null,
                initials = title.take(2).uppercase(),
                seed = chat.otherMemberId(currentUserId).orEmpty(),
                size = 48.dp,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chat.lastMessage.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = relativeTime(chat.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (unread > 0) {
                Badge(modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (unread > 99) "99+" else "$unread")
                }
            }
        }
    }
}
