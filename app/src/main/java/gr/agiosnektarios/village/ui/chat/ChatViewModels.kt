package gr.agiosnektarios.village.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.Chat
import gr.agiosnektarios.village.core.model.ChatMessage
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.chat.ChatRepository
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.ui.navigation.DeepLinks
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.user.UserRepository
import gr.agiosnektarios.village.notifications.ActiveChatTracker
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------- chat list

data class ChatsUiState(
    val chats: List<Chat> = emptyList(),
    val currentUserId: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    chatRepository: ChatRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatsUiState> = sessionRepository.profile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(ChatsUiState(loading = false))
            } else {
                chatRepository.observeChats(profile.id)
                    .map { chats ->
                        ChatsUiState(
                            // Conversations with no messages yet are hidden:
                            // an empty room in the list is noise, and it
                            // reappears the moment someone writes.
                            chats = chats.filter {
                                it.lastMessage.isNotBlank() || it.createdById == profile.id
                            },
                            currentUserId = profile.id,
                            loading = false,
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState())
}

// ------------------------------------------------------------- chat room

data class ChatRoomUiState(
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentUserId: String = "",
    val draft: String = "",
    val sending: Boolean = false,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val chatId: String = savedStateHandle.get<String>("chatId").orEmpty()
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ChatRoomUiState> = combine(
        chatRepository.observeChat(chatId),
        chatRepository.observeMessages(chatId),
        sessionRepository.profile,
        local,
    ) { chat, messages, profile, localState ->
        ChatRoomUiState(
            chat = chat,
            messages = messages,
            currentUserId = profile?.id.orEmpty(),
            draft = localState.draft,
            sending = localState.sending,
            loading = false,
            errorMessage = localState.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatRoomUiState())

    init {
        // Suppresses pushes for the conversation currently on screen.
        ActiveChatTracker.activeChatId = chatId
        markRead()
    }

    fun onDraftChange(value: String) = local.update { it.copy(draft = value) }

    fun send() {
        val sender = sessionRepository.currentProfile ?: return
        val text = local.value.draft.trim()
        if (text.isEmpty()) return

        // Clear the field immediately: waiting for the round trip before the
        // input empties is what makes chat feel laggy.
        local.update { it.copy(draft = "", sending = true) }
        viewModelScope.launch {
            val result = chatRepository.sendMessage(chatId, sender, text)
            if (result.isSuccess) {
                val chat = uiState.value.chat
                notificationRepository.notify(
                    recipientIds = chat?.memberIds.orEmpty(),
                    actorId = sender.id,
                    type = NotificationType.CHAT,
                    title = chat?.displayTitle(sender.id).orEmpty()
                        .ifBlank { sender.displayName },
                    bodyKey = "notif_new_message",
                    bodyArg = sender.displayName,
                    deepLink = DeepLinks.chat(chatId),
                    // One line per conversation, not per message.
                    collapseKey = "CHAT:$chatId",
                )
            }
            local.update {
                it.copy(
                    sending = false,
                    // Restore the text so a failed send is recoverable.
                    draft = if (result.isFailure) text else it.draft,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun markRead() {
        val userId = sessionRepository.currentUserId ?: return
        viewModelScope.launch { chatRepository.markRead(chatId, userId) }
    }

    fun leave(onLeft: () -> Unit) {
        val userId = sessionRepository.currentUserId ?: return
        viewModelScope.launch {
            chatRepository.leaveChat(chatId, userId).onSuccess { onLeft() }
        }
    }

    fun consumeError() = local.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        // Only clear if this room is still the active one — a fast switch
        // between conversations can tear this view model down after the next
        // one has already registered itself.
        if (ActiveChatTracker.activeChatId == chatId) ActiveChatTracker.activeChatId = null
    }

    private data class LocalState(
        val draft: String = "",
        val sending: Boolean = false,
        val errorMessage: String? = null,
    )
}

// -------------------------------------------------------------- new chat

data class NewChatUiState(
    val query: String = "",
    val results: List<UserProfile> = emptyList(),
    val selected: List<UserProfile> = emptyList(),
    val groupTitle: String = "",
    val creating: Boolean = false,
    val createdChatId: String? = null,
    val errorMessage: String? = null,
) {
    val isGroup: Boolean get() = selected.size > 1
    val canCreate: Boolean
        get() = selected.isNotEmpty() && (!isGroup || groupTitle.isNotBlank()) && !creating
}

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val local = MutableStateFlow(NewChatUiState())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NewChatUiState> = combine(
        query
            // Every keystroke would otherwise be a Firestore query.
            .debounce(220)
            .flatMapLatest { text -> userRepository.searchResidents(text).catch { emit(emptyList()) } },
        local,
        query,
    ) { results, state, currentQuery ->
        val myId = sessionRepository.currentUserId
        state.copy(
            query = currentQuery,
            results = results.filter { it.id != myId && !it.disabled },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewChatUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onGroupTitleChange(value: String) = local.update { it.copy(groupTitle = value) }

    fun toggleSelection(profile: UserProfile) = local.update { state ->
        state.copy(
            selected = if (state.selected.any { it.id == profile.id }) {
                state.selected.filterNot { it.id == profile.id }
            } else {
                state.selected + profile
            },
        )
    }

    fun create() {
        val me = sessionRepository.currentProfile ?: return
        val state = local.value
        if (!state.canCreate) return

        viewModelScope.launch {
            local.update { it.copy(creating = true, errorMessage = null) }
            val result = if (state.selected.size == 1) {
                chatRepository.openDirectChat(me, state.selected.first())
            } else {
                chatRepository.createGroupChat(me, state.groupTitle, state.selected)
            }
            local.update {
                it.copy(
                    creating = false,
                    createdChatId = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun consumeError() = local.update { it.copy(errorMessage = null) }
}
