package gr.agiosnektarios.village.data.user

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.runCatchingUnit
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.NotificationPrefs
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Reads and writes the resident directory at `users/`. */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val users get() = firestore.collection(Collections.USERS)

    fun observeProfile(userId: String): Flow<UserProfile?> =
        users.document(userId).asFlow().map { it.toObjectSafe<UserProfile>() }

    suspend fun getProfile(userId: String): Result<UserProfile?> = withContext(io) {
        runCatching { users.document(userId).get().await().toObjectSafe<UserProfile>() }
    }

    /**
     * Creates the profile document for a brand new account.
     *
     * `role` is deliberately absent from the payload: the security rules reject
     * a client-written role, and Cloud Functions own promotion to admin.
     */
    suspend fun createProfile(
        userId: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        address: String,
        blockId: String,
        photoUrl: String = "",
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val payload = mapOf(
                "firstName" to firstName.trim(),
                "lastName" to lastName.trim(),
                "nameLower" to "${firstName.trim()} ${lastName.trim()}".lowercase(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "address" to address.trim(),
                "blockId" to blockId,
                "photoUrl" to photoUrl,
                "role" to Role.USER.id,
                "disabled" to false,
                "notificationPrefs" to NotificationPrefs().toMap(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            // merge(): a Google sign-in may have raced a partially created doc.
            users.document(userId).set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    suspend fun updateProfile(
        userId: String,
        firstName: String,
        lastName: String,
        phone: String,
        address: String,
        blockId: String,
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update(
                mapOf(
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "nameLower" to "${firstName.trim()} ${lastName.trim()}".lowercase(),
                    "phone" to phone.trim(),
                    "address" to address.trim(),
                    "blockId" to blockId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    suspend fun updatePhoto(userId: String, photoUrl: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId)
                .update("photoUrl", photoUrl, "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
    }

    suspend fun updateNotificationPrefs(userId: String, prefs: NotificationPrefs): Result<Unit> =
        withContext(io) {
            runCatchingUnit {
                users.document(userId).update(
                    mapOf(
                        "notificationPrefs" to prefs.toMap(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }

    /** Tokens are a set: adding is idempotent, and stale ones are pruned server-side. */
    suspend fun addFcmToken(userId: String, token: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update("fcmTokens", FieldValue.arrayUnion(token)).await()
        }
    }

    suspend fun removeFcmToken(userId: String, token: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update("fcmTokens", FieldValue.arrayRemove(token)).await()
        }
    }

    /**
     * Prefix search over the lower-cased full name.
     *
     * `` is the highest code point Firestore will index, so `[q, q+]`
     * is the range of every string starting with `q`. Blank query returns the
     * directory ordered by name, which is what the "start a chat" picker wants.
     */
    fun searchResidents(query: String, limit: Long = 40): Flow<List<UserProfile>> {
        val normalized = query.trim().lowercase()
        val base: Query = if (normalized.isEmpty()) {
            users.orderBy("nameLower")
        } else {
            users.orderBy("nameLower")
                .startAt(normalized)
                .endAt(normalized + '\uf8ff')
        }
        return base.limit(limit).asFlow().map { it.toObjectsSafe<UserProfile>() }
            .orEmptyOnError("user search")
    }

    fun observeAllResidents(limit: Long = 500): Flow<List<UserProfile>> =
        users.orderBy("nameLower").limit(limit).asFlow().map { it.toObjectsSafe<UserProfile>() }
            .orEmptyOnError("user directory")

    suspend fun getProfiles(userIds: List<String>): Result<List<UserProfile>> = withContext(io) {
        runCatching {
            // whereIn caps at 30 values per query, so fetch in chunks.
            userIds.distinct().chunked(30).flatMap { chunk ->
                users.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await().toObjectsSafe<UserProfile>()
            }
        }
    }
}

internal fun NotificationPrefs.toMap(): Map<String, Boolean> = mapOf(
    "comments" to comments,
    "statusChanges" to statusChanges,
    "votes" to votes,
    "announcements" to announcements,
    "chat" to chat,
)
