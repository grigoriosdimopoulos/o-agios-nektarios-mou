package gr.agiosnektarios.village.data.chat

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.orNullOnError
import gr.agiosnektarios.village.core.runCatchingUnit
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.Chat
import gr.agiosnektarios.village.core.model.ChatMessage
import gr.agiosnektarios.village.core.model.ChatType
import gr.agiosnektarios.village.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val chats get() = firestore.collection(Collections.CHATS)

    /**
     * Every conversation this resident belongs to, most recent first.
     *
     * Sorted here rather than by the server: pairing `array-contains` with an
     * `orderBy` needs a composite index, and a composite index has to be
     * created by hand before the query works at all — which turns a first run
     * against a fresh project into a failure. A resident has tens of
     * conversations, not thousands, so the ordering costs nothing locally and
     * the app works the moment the project exists.
     */
    fun observeChats(userId: String): Flow<List<Chat>> =
        chats.whereArrayContains("memberIds", userId)
            .limit(200)
            .asFlow()
            .map { snapshot ->
                snapshot.toObjectsSafe<Chat>()
                    .sortedByDescending { it.lastMessageAt?.time ?: 0L }
            }
            .orEmptyOnError("chats of $userId")

    fun observeChat(chatId: String): Flow<Chat?> =
        chats.document(chatId).asFlow().map { it.toObjectSafe<Chat>() }
            .orNullOnError("chat $chatId")

    /**
     * Messages newest-first so the capped query keeps the *recent* tail; the UI
     * reverses the list to render oldest at the top.
     */
    fun observeMessages(chatId: String, limit: Long = 300): Flow<List<ChatMessage>> =
        chats.document(chatId).collection(Collections.MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asFlow()
            .map { it.toObjectsSafe<ChatMessage>().reversed() }
            .orEmptyOnError("messages of $chatId")

    /**
     * Opens the one-to-one conversation between two residents, creating it on
     * first use.
     *
     * The document id is derived from the sorted pair of uids, which makes the
     * operation idempotent: two people tapping "message" at the same moment
     * converge on one conversation instead of creating two.
     */
    suspend fun openDirectChat(me: UserProfile, other: UserProfile): Result<String> =
        withContext(io) {
            runCatching {
                val chatId = directChatId(me.id, other.id)
                val doc = chats.document(chatId)
                if (!doc.get().await().exists()) {
                    doc.set(
                        mapOf(
                            "type" to ChatType.DIRECT.id,
                            "title" to "",
                            "photoUrl" to "",
                            "memberIds" to listOf(me.id, other.id).sorted(),
                            "memberNames" to mapOf(
                                me.id to me.displayName,
                                other.id to other.displayName,
                            ),
                            "memberPhotos" to mapOf(
                                me.id to me.photoUrl,
                                other.id to other.photoUrl,
                            ),
                            "createdById" to me.id,
                            "lastMessage" to "",
                            "lastMessageSenderId" to "",
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "unreadCounts" to mapOf(me.id to 0, other.id to 0),
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
                chatId
            }
        }

    suspend fun createGroupChat(
        creator: UserProfile,
        title: String,
        members: List<UserProfile>,
    ): Result<String> = withContext(io) {
        runCatching {
            val everyone = (members + creator).distinctBy { it.id }
            require(everyone.size >= 2) { "A group needs at least two people" }
            val doc = chats.document()
            doc.set(
                mapOf(
                    "type" to ChatType.GROUP.id,
                    "title" to title.trim(),
                    "photoUrl" to "",
                    "memberIds" to everyone.map { it.id },
                    "memberNames" to everyone.associate { it.id to it.displayName },
                    "memberPhotos" to everyone.associate { it.id to it.photoUrl },
                    "createdById" to creator.id,
                    "lastMessage" to "",
                    "lastMessageSenderId" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to everyone.associate { it.id to 0 },
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            doc.collection(Collections.MESSAGES).add(
                mapOf(
                    "senderId" to creator.id,
                    "senderName" to creator.displayName,
                    "senderPhotoUrl" to creator.photoUrl,
                    "text" to "",
                    "imageUrl" to "",
                    "systemEvent" to "GROUP_CREATED",
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            doc.id
        }
    }

    /**
     * Appends a message and updates what the conversation list shows.
     *
     * The message, the preview line and every *other* member's unread counter
     * are written in one batch, so the list can never show a preview for a
     * message that failed to send, or a badge without a message behind it.
     * The sender's own counter is untouched: they have obviously read it.
     */
    suspend fun sendMessage(
        chatId: String,
        sender: UserProfile,
        text: String,
        imageUrl: String = "",
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            require(text.isNotBlank() || imageUrl.isNotBlank()) { "Empty message" }
            val chatRef = chats.document(chatId)
            val members = chatRef.get().await().get("memberIds") as? List<*> ?: emptyList<Any>()

            val batch = firestore.batch()
            batch.set(
                chatRef.collection(Collections.MESSAGES).document(),
                mapOf(
                    "senderId" to sender.id,
                    "senderName" to sender.displayName,
                    "senderPhotoUrl" to sender.photoUrl,
                    "text" to text.trim(),
                    "imageUrl" to imageUrl,
                    "systemEvent" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )

            val preview = text.trim().ifBlank { "📷" }
            val updates = mutableMapOf<String, Any>(
                "lastMessage" to preview.take(80),
                "lastMessageSenderId" to sender.id,
                "lastMessageAt" to FieldValue.serverTimestamp(),
            )
            members.filterIsInstance<String>()
                .filter { it != sender.id }
                .forEach { updates["unreadCounts.$it"] = FieldValue.increment(1) }
            batch.update(chatRef, updates)

            batch.commit().await()
        }
    }

    /** Clears the viewer's own unread badge; other members' counters are untouched. */
    suspend fun markRead(chatId: String, userId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            chats.document(chatId).update("unreadCounts.$userId", 0).await()
        }
    }

    suspend fun addMembers(chatId: String, members: List<UserProfile>): Result<Unit> =
        withContext(io) {
            runCatchingUnit {
                val updates = mutableMapOf<String, Any>(
                    "memberIds" to FieldValue.arrayUnion(*members.map { it.id }.toTypedArray()),
                )
                members.forEach {
                    updates["memberNames.${it.id}"] = it.displayName
                    updates["memberPhotos.${it.id}"] = it.photoUrl
                    updates["unreadCounts.${it.id}"] = 0
                }
                chats.document(chatId).update(updates).await()
            }
        }

    suspend fun leaveChat(chatId: String, userId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            chats.document(chatId).update(
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(userId),
                    "unreadCounts.$userId" to FieldValue.delete(),
                ),
            ).await()
        }
    }

    companion object {
        /** Deterministic id for a pair, independent of who opens the chat. */
        fun directChatId(a: String, b: String): String =
            listOf(a, b).sorted().joinToString("_")
    }
}
