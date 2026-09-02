package gr.agiosnektarios.village.data.user

import com.google.firebase.firestore.Blob
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
import gr.agiosnektarios.village.core.model.HomePin
import gr.agiosnektarios.village.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import gr.agiosnektarios.village.core.firestore.SERVER_ACK_MS
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch

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
     * The payload names `role` explicitly, and the rules permit it — but only
     * as `USER`. There is no server on this plan to assign it instead, so a
     * resident writes their own role on the way in and the rules refuse any
     * value but that one. Promotion to ADMIN is also a client write, gated on
     * an `adminClaims` document that can only exist if the passphrase matched;
     * see `isSelfElevation()` in the rules.
     */
    suspend fun createProfile(
        userId: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        address: String,
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val payload = mapOf(
                "firstName" to firstName.trim(),
                "lastName" to lastName.trim(),
                "nameLower" to "${firstName.trim()} ${lastName.trim()}".lowercase(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "address" to address.trim(),
                "role" to Role.USER.id,
                "disabled" to false,
                "notificationPrefs" to NotificationPrefs().toMap(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            // merge(): a Google sign-in may have raced a partially created doc.
            // Unbounded, unlike every *edit* in this file, and for the same
            // reason as AuthRepository: you cannot create an account without a
            // server, so by the time this runs there demonstrably is one. What
            // the wait buys is the rules' answer. Bounded, a rejection arriving
            // at four seconds and one millisecond would be swallowed, the
            // resident would be told nothing was wrong, and every subsequent
            // write would fail with a permission error nothing explains.
            users.document(userId)
                .set(payload, com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
    }

    suspend fun updateProfile(
        userId: String,
        firstName: String,
        lastName: String,
        phone: String,
        address: String,
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update(
                mapOf(
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "nameLower" to "${firstName.trim()} ${lastName.trim()}".lowercase(),
                    "phone" to phone.trim(),
                    "address" to address.trim(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
        }
    }

    /**
     * Sets or clears the resident's own house pin.
     *
     * Separate from [updateProfile] because it is set from the map rather than
     * from the form, and because clearing it has to be possible without
     * retyping a name.
     */
    suspend fun updateHome(
        userId: String,
        lat: Double?,
        lng: Double?,
        place: String,
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val doc = homeDoc(userId)
            val task = if (lat == null || lng == null) {
                // Cleared, not blanked. A document that exists with null
                // coordinates is a house someone has to reason about; a
                // document that is gone is a house nobody pinned.
                doc.delete()
            } else {
                doc.set(
                    mapOf(
                        "lat" to lat,
                        "lng" to lng,
                        "place" to place.trim().take(80),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            // Bounded, like every write in this file — which was not true
            // when this comment was first written, and cost every other one of
            // them a spinner that never stops. Somebody pins their house while
            // standing outside it, which is the one place in the village with
            // no signal and the whole reason the feature exists; the write is
            // already on disk and queued by the time this returns.
            withTimeoutOrNull(SERVER_ACK_MS) { task.await() }
        }
    }

    private fun homeDoc(userId: String) = users.document(userId)
        .collection(Collections.PRIVATE)
        .document(Collections.HOME)

    private fun contactDoc(userId: String) = users.document(userId)
        .collection(Collections.PRIVATE)
        .document(Collections.CONTACT)

    /** The resident's own telephone number. Nobody else's is readable. */
    fun observePhone(userId: String): Flow<String> = contactDoc(userId).asFlow()
        .map { it?.getString("phone").orEmpty() }
        .catch { emit("") }

    suspend fun updatePhone(userId: String, phone: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            contactDoc(userId).set(
                mapOf(
                    "phone" to phone.trim().take(20),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
        }
    }

    /**
     * Moves a telephone number off the profile, once, on the owner's device.
     *
     * The number used to live on the profile document, which every signed-in
     * resident can read — so the village's whole directory of mobile numbers
     * was legible to anyone who could sign in, to support one button most days
     * nobody presses. It lives privately now, and is published to neighbours
     * only if its owner says so and only while the village has that switched
     * on.
     *
     * Existing profiles still carry the old field, and a server we do not have
     * cannot clear them, so each resident's own device does it: copy across,
     * then blank the profile copy. Blank rather than removed, because the
     * field stays in the rules' allowlist for exactly as long as some
     * documents still have it, and an empty string is what "no number here"
     * has always looked like to the reader.
     */
    suspend fun migratePhoneToPrivate(userId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val profile = users.document(userId).get().await()
            val legacy = profile.getString("phone").orEmpty()
            if (legacy.isBlank()) return@runCatchingUnit

            val already = contactDoc(userId).get().await().getString("phone").orEmpty()
            if (already.isBlank()) {
                contactDoc(userId).set(
                    mapOf(
                        "phone" to legacy.take(20),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            users.document(userId).update("phone", "").await()
        }
    }

    /**
     * The resident's own house pin.
     *
     * Only ever called for the signed-in user, and the rules would reject it
     * for anyone else — see [HomePin] for why it is not on the profile.
     */
    fun observeHome(userId: String): Flow<HomePin?> =
        homeDoc(userId).asFlow().map { it.toObjectSafe<HomePin>() }

    /** Sets or clears the resident's picture. Null removes it. */
    suspend fun updateAvatar(userId: String, bytes: ByteArray?): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId)
                .update(
                    "avatar", bytes?.let(Blob::fromBytes),
                    "updatedAt", FieldValue.serverTimestamp(),
                )
                .let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
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
                ).let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
            }
        }

    /** Tokens are a set: adding is idempotent, and stale ones are pruned server-side. */
    suspend fun addFcmToken(userId: String, token: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update("fcmTokens", FieldValue.arrayUnion(token))
                .let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
        }
    }

    suspend fun removeFcmToken(userId: String, token: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId).update("fcmTokens", FieldValue.arrayRemove(token))
                .let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
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
