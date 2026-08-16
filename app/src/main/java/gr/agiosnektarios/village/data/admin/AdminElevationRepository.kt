package gr.agiosnektarios.village.data.admin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.model.Role
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Why an elevation attempt did not result in administrator rights. */
sealed class ElevationFailure(message: String) : Exception(message) {
    /** The passphrase did not match. Indistinguishable from "not configured". */
    data object Rejected : ElevationFailure("Wrong passphrase")

    data class Failed(val reason: Throwable) : ElevationFailure(reason.message.orEmpty())
}

/**
 * Grants administrator rights to whoever can produce the village's admin
 * passphrase.
 *
 * The passphrase is never in the app and never readable by it. It lives in one
 * Firestore document that no client may read — but which the security rules
 * *can* read, because `get()` inside a rule is not subject to the rules. So the
 * check happens on the server, in the only place that can be trusted:
 *
 *   1. The app writes `adminClaims/{uid}` containing the typed passphrase.
 *      The rule compares it against the stored one and rejects the write
 *      outright if it differs. Nothing is revealed either way — a wrong
 *      passphrase and an unconfigured village fail identically.
 *   2. That accepted document is now proof the passphrase was known, so the
 *      rule on `users/{uid}` allows raising one's own role to ADMIN while it
 *      exists.
 *   3. The claim is deleted immediately afterwards, so the passphrase does not
 *      sit in the database in plain text any longer than the moment it takes
 *      to use it.
 *
 * **What this is and is not.** The passphrase is the administrator credential
 * for the village: anyone who learns it can take administrator rights, and
 * demoting them does not change that — they can simply do it again. Revoking
 * someone means changing the passphrase in the console, not demoting them.
 * There is also no rate limiting a client can enforce, so the passphrase has to
 * be long enough that guessing it over the network is hopeless; `docs/SETUP.md`
 * says so where it explains how to set one.
 */
@Singleton
class AdminElevationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun elevate(userId: String, passphrase: String): Result<Unit> = withContext(io) {
        val claim = firestore.collection(Collections.ADMIN_CLAIMS).document(userId)

        // Step one. A rejection here is the passphrase being wrong; it is the
        // only signal, and it is deliberately the same signal as a village
        // whose passphrase was never configured.
        val proved = runCatching {
            claim.set(
                mapOf(
                    "secret" to passphrase,
                    "userId" to userId,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        if (proved.isFailure) return@withContext Result.failure(ElevationFailure.Rejected)

        // Step two. Permitted only while the claim above exists.
        val raised = runCatching {
            firestore.collection(Collections.USERS).document(userId).update(
                mapOf(
                    "role" to Role.ADMIN.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }

        // Step three, regardless of the outcome: the passphrase must not linger.
        runCatching { claim.delete().await() }

        raised.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(ElevationFailure.Failed(it)) },
        )
    }
}
