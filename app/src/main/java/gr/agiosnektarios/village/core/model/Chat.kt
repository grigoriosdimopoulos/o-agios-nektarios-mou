package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A direct or group conversation at `chats/{chatId}`.
 *
 * [memberIds] is the access-control list: every read rule and every query is
 * an `array-contains` on it, so a conversation is invisible to non-members.
 * [memberNames] and [lastMessage] are denormalised to keep the conversation
 * list a single query with no fan-out reads.
 */
data class Chat(
    @DocumentId val id: String = "",
    val type: String = ChatType.DIRECT.id,
    val title: String = "",
    val photoUrl: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val memberPhotos: Map<String, String> = emptyMap(),
    val createdById: String = "",
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageAt: Date? = null,
    /** uid -> number of messages not yet seen by that member. */
    val unreadCounts: Map<String, Int> = emptyMap(),
    @ServerTimestamp val createdAt: Date? = null,
) {
    val chatType: ChatType get() = ChatType.fromId(type)

    fun otherMemberId(currentUserId: String): String? =
        memberIds.firstOrNull { it != currentUserId }

    /** Direct chats are titled by whoever the viewer is *not*. */
    fun displayTitle(currentUserId: String): String = when (chatType) {
        ChatType.GROUP -> title
        ChatType.DIRECT -> otherMemberId(currentUserId)?.let { memberNames[it] }.orEmpty()
    }

    fun displayPhoto(currentUserId: String): String = when (chatType) {
        ChatType.GROUP -> photoUrl
        ChatType.DIRECT -> otherMemberId(currentUserId)?.let { memberPhotos[it] }.orEmpty()
    }

    fun unreadFor(userId: String): Int = unreadCounts[userId] ?: 0
}

enum class ChatType(val id: String) {
    DIRECT("DIRECT"),
    GROUP("GROUP"),
    ;

    companion object {
        fun fromId(id: String?): ChatType = entries.firstOrNull { it.id == id } ?: DIRECT
    }
}

/** A message at `chats/{chatId}/messages/{messageId}`. */
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val text: String = "",
    val imageUrl: String = "",
    /** Set for the synthetic "X created the group" style entries. */
    val systemEvent: String = "",
    @ServerTimestamp val createdAt: Date? = null,
) {
    val isSystem: Boolean get() = systemEvent.isNotBlank()
}
