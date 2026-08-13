package gr.agiosnektarios.village.data.admin

import com.google.firebase.functions.FirebaseFunctions
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.model.Role
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Privileged operations.
 *
 * Every one of these is a callable Cloud Function rather than a direct write,
 * because they need the Admin SDK (deleting an auth account, minting a password
 * reset link, setting a custom claim) and because the authorisation check has
 * to happen somewhere a client cannot skip. The UI hides these actions from
 * non-admins; the functions are what actually enforce it.
 */
@Singleton
class AdminRepository @Inject constructor(
    private val functions: FirebaseFunctions,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun setRole(userId: String, role: Role): Result<Unit> =
        call("adminSetRole", mapOf("userId" to userId, "role" to role.id))

    suspend fun setDisabled(userId: String, disabled: Boolean): Result<Unit> =
        call("adminSetDisabled", mapOf("userId" to userId, "disabled" to disabled))

    suspend fun renameUser(userId: String, firstName: String, lastName: String): Result<Unit> =
        call(
            "adminRenameUser",
            mapOf(
                "userId" to userId,
                "firstName" to firstName.trim(),
                "lastName" to lastName.trim(),
            ),
        )

    /** Sends the resident a reset email; the admin never sees or sets the password. */
    suspend fun sendPasswordReset(userId: String): Result<Unit> =
        call("adminSendPasswordReset", mapOf("userId" to userId))

    /** Removes the auth account, the profile, and everything the resident posted. */
    suspend fun deleteUser(userId: String): Result<Unit> =
        call("adminDeleteUser", mapOf("userId" to userId))

    /** Self-service account closure, available to every resident. */
    suspend fun deleteMyAccount(): Result<Unit> = call("deleteMyAccount", emptyMap())

    private suspend fun call(name: String, data: Map<String, Any>): Result<Unit> =
        withContext(io) {
            runCatching { functions.getHttpsCallable(name).call(data).await() }.map { }
        }
}
