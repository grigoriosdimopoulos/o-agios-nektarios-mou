package gr.agiosnektarios.village.data.admin

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.runCatchingUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Administration, without a server.
 *
 * The app runs on the Spark plan, so there is no Admin SDK anywhere: these are
 * ordinary Firestore writes that the security rules permit only for a user
 * whose own profile carries `role == "ADMIN"`. That check is a server-side
 * `get()` inside the rules, so hiding the buttons is a courtesy and the rules
 * are the actual enforcement.
 *
 * Two things genuinely cannot be done this way, and neither is faked:
 *
 *  * **Deleting the login.** Only the Admin SDK can delete somebody else's
 *    Firebase Auth account. [deleteUser] removes the resident's profile and
 *    everything they wrote; the login survives, and signing in with it lands
 *    on the "complete your profile" screen as a brand new resident.
 *  * **Custom claims.** Roles live on the user document instead, which is why
 *    the rules read them with `get()`.
 */
@Singleton
class AdminRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val users get() = firestore.collection(Collections.USERS)

    suspend fun setRole(userId: String, role: Role): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId)
                .update("role", role.id, "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
    }

    /**
     * Suspends an account at the application level.
     *
     * `isActiveResident()` in the rules fails for a suspended resident, so every
     * read and write is refused server-side, and [SessionRepository] signs them
     * out on sight. Their login still exists — that is the part that needs the
     * Admin SDK — but it can no longer see or touch anything.
     */
    suspend fun setDisabled(userId: String, disabled: Boolean): Result<Unit> = withContext(io) {
        runCatchingUnit {
            users.document(userId)
                .update("disabled", disabled, "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
    }

    suspend fun renameUser(userId: String, firstName: String, lastName: String): Result<Unit> =
        withContext(io) {
            runCatchingUnit {
                val first = firstName.trim()
                val last = lastName.trim()
                users.document(userId).update(
                    mapOf(
                        "firstName" to first,
                        "lastName" to last,
                        "nameLower" to "$first $last".lowercase(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }

    /**
     * Emails the resident a reset link.
     *
     * `sendPasswordResetEmail` is deliberately callable by anyone for any
     * address — it is the same call the "forgot password" screen makes — so
     * this needs no privilege at all. An administrator never learns or chooses
     * the new password, which is the property worth keeping.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            require(email.isNotBlank()) { "This resident has no email address on file." }
            auth.sendPasswordResetEmail(email.trim()).await()
        }
    }

    /** Removes a resident's profile and everything they wrote. */
    suspend fun deleteUser(userId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            purgeContentOf(userId)
            users.document(userId).delete().await()
        }
    }

    /**
     * Self-service account closure.
     *
     * Unlike the administrator path this one *can* remove the login, because
     * a signed-in user is always allowed to delete their own auth account. The
     * account goes last, so a failure part way leaves someone who can still
     * sign in and retry rather than an unreachable orphan.
     */
    suspend fun deleteMyAccount(): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            purgeContentOf(user.uid)
            users.document(user.uid).delete().await()
            user.delete().await()
        }
    }

    /**
     * Deletes the reports and comments a resident wrote.
     *
     * Their votes are left behind: a vote is a single document under someone
     * else's report, and removing them would mean rewriting tallies across the
     * village from a client. A stale vote costs a few bytes and skews no count,
     * so it is the better thing to leave.
     */
    private suspend fun purgeContentOf(userId: String) {
        val reports = firestore.collection(Collections.ISSUES)
            .whereEqualTo("authorId", userId)
            .get()
            .await()

        for (report in reports.documents) {
            deleteAll(report.reference.collection(Collections.VOTES))
            deleteAll(report.reference.collection(Collections.COMMENTS))
            report.reference.delete().await()
        }

        // Conversations survive for the other members; only this resident's
        // membership goes, so nobody loses their own history.
        val chats = firestore.collection(Collections.CHATS)
            .whereArrayContains("memberIds", userId)
            .get()
            .await()
        for (chat in chats.documents) {
            chat.reference.update(
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(userId),
                    "unreadCounts.$userId" to FieldValue.delete(),
                ),
            ).await()
        }
    }

    private suspend fun deleteAll(collection: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val page = collection.limit(BATCH_LIMIT).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (page.size() < BATCH_LIMIT) return
        }
    }

    private companion object {
        const val BATCH_LIMIT = 300L
    }
}
