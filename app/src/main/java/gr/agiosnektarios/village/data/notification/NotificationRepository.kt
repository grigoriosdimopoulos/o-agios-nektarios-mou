package gr.agiosnektarios.village.data.notification

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.NotificationPrefs
import gr.agiosnektarios.village.core.runCatchingUnit
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** What a notification is about; picks the channel and the resident's opt-out. */
enum class NotificationType(val id: String) {
    COMMENT("COMMENT"),
    STATUS("STATUS"),
    VOTE("VOTE"),
    ANNOUNCEMENT("ANNOUNCEMENT"),
    CHAT("CHAT"),
    ;

    companion object {
        fun fromId(id: String?): NotificationType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One notice addressed to one resident, at
 * `users/{userId}/notifications/{id}`.
 *
 * [bodyKey] names a string in the app rather than carrying Greek or English
 * text, so a notice written by a Greek phone still arrives in English on an
 * English one. [body] is the fallback for anything without a key.
 */
data class AppNotification(
    @DocumentId val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val bodyKey: String = "",
    val bodyArg: String = "",
    val deepLink: String = "",
    /** Who caused it. Used to never notify someone about their own action. */
    val actorId: String = "",
    /** Collapses repeats about the same thing instead of stacking them. */
    val collapseKey: String = "",
    val seen: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
) {
    val notificationType: NotificationType? get() = NotificationType.fromId(type)

    /** Honours the switches in Settings, which are per resident, not per device. */
    fun allowedBy(prefs: NotificationPrefs): Boolean = when (notificationType) {
        NotificationType.COMMENT -> prefs.comments
        NotificationType.STATUS -> prefs.statusChanges
        NotificationType.VOTE -> prefs.votes
        NotificationType.ANNOUNCEMENT -> prefs.announcements
        NotificationType.CHAT -> prefs.chat
        null -> false
    }
}

/**
 * Delivers notices by writing them into the recipient's own subcollection.
 *
 * Sending a real push needs a server credential that cannot ship inside an
 * app, and a server needs the paid plan. So the acting client writes the notice
 * instead: comment on someone's report and your phone puts a document under
 * *their* user, and their phone raises the notification when it sees it.
 *
 * The honest limit of that approach — it only fires while the recipient's app
 * is running — is documented on [NotificationDispatcher], which is the half
 * that listens.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private fun inbox(userId: String) =
        firestore.collection(Collections.USERS)
            .document(userId)
            .collection(Collections.NOTIFICATIONS)

    /** The recent notices for one resident, newest first. */
    fun observe(userId: String, limit: Long = 50): Flow<List<AppNotification>> =
        inbox(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asFlow()
            .map { it.toObjectsSafe<AppNotification>() }
            .orEmptyOnError("notifications of $userId")

    /**
     * Addresses one notice to each recipient, skipping the person who caused it.
     *
     * Nobody wants telling that they themselves commented, and a report's author
     * commenting on their own report is the common case.
     */
    suspend fun notify(
        recipientIds: Collection<String>,
        actorId: String,
        type: NotificationType,
        title: String,
        bodyKey: String = "",
        bodyArg: String = "",
        body: String = "",
        deepLink: String = "",
        collapseKey: String = "",
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val recipients = recipientIds.toSet() - actorId
            if (recipients.isEmpty()) return@runCatchingUnit

            // Chunked because a batch holds 500 writes and an announcement to a
            // whole village is the one case that could approach it.
            recipients.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { recipient ->
                    batch.set(
                        inbox(recipient).document(),
                        mapOf(
                            "type" to type.id,
                            "title" to title.take(TITLE_LIMIT),
                            "body" to body.take(BODY_LIMIT),
                            "bodyKey" to bodyKey,
                            "bodyArg" to bodyArg.take(BODY_LIMIT),
                            "deepLink" to deepLink,
                            "actorId" to actorId,
                            "collapseKey" to collapseKey,
                            "seen" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
                batch.commit().await()
            }
        }
    }

    /**
     * One-shot read of what has not been announced yet.
     *
     * Deliberately unordered in the query: `seen == false` is a single
     * equality, which Firestore indexes on its own, while adding an orderBy
     * would need a hand-created composite index — and this app has none by
     * design. A page this small is sorted here instead.
     */
    suspend fun unseen(userId: String, limit: Long = 30): Result<List<AppNotification>> =
        withContext(io) {
            runCatching {
                inbox(userId)
                    .whereEqualTo("seen", false)
                    .limit(limit)
                    .get().await()
                    .toObjectsSafe<AppNotification>()
                    .sortedBy { it.createdAt?.time ?: 0L }
            }
        }

    suspend fun markSeen(userId: String, notificationId: String): Result<Unit> =
        withContext(io) {
            runCatchingUnit { inbox(userId).document(notificationId).update("seen", true).await() }
        }

    suspend fun markAllSeen(userId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val unseen = inbox(userId).whereEqualTo("seen", false).limit(BATCH_LIMIT.toLong())
                .get().await()
            if (unseen.isEmpty) return@runCatchingUnit
            val batch = firestore.batch()
            unseen.documents.forEach { batch.update(it.reference, "seen", true) }
            batch.commit().await()
        }
    }

    /** Everyone's uid, for an announcement. Admin-only in practice. */
    suspend fun allResidentIds(): Result<List<String>> = withContext(io) {
        runCatching {
            firestore.collection(Collections.USERS)
                .get().await()
                .documents.map { it.id }
        }
    }

    private companion object {
        const val BATCH_LIMIT = 400
        const val TITLE_LIMIT = 120
        const val BODY_LIMIT = 240
    }
}
