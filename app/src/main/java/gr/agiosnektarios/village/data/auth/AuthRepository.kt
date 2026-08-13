package gr.agiosnektarios.village.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.userProfileChangeRequest
import gr.agiosnektarios.village.core.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Everything that touches Firebase Auth credentials.
 *
 * Profile *data* lives in Firestore and belongs to `UserRepository`; this class
 * only owns identity: who is signed in, and how they prove it.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    /** Emits on every sign-in and sign-out, including token refreshes. */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        withContext(io) {
            runCatching {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                requireNotNull(result.user) { "Sign-in returned no user" }
            }
        }

    suspend fun signUp(email: String, password: String, displayName: String): Result<FirebaseUser> =
        withContext(io) {
            runCatching {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = requireNotNull(result.user) { "Sign-up returned no user" }
                user.updateProfile(
                    userProfileChangeRequest { this.displayName = displayName },
                ).await()
                // Best effort: a bounced verification mail must not fail the signup.
                runCatching { user.sendEmailVerification().await() }
                user
            }
        }

    /**
     * Exchanges a Google ID token (obtained through Credential Manager) for a
     * Firebase session. Returns whether this was the account's first sign-in,
     * so the caller knows to collect the extra village details.
     */
    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInResult> =
        withContext(io) {
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = requireNotNull(result.user) { "Google sign-in returned no user" }
                GoogleSignInResult(
                    user = user,
                    isNewAccount = result.additionalUserInfo?.isNewUser == true,
                )
            }
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(io) {
        runCatching { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    /**
     * Changing a password requires a recent login, so the current password is
     * used to re-authenticate first rather than surfacing a confusing
     * `requires-recent-login` failure to the user.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        withContext(io) {
            runCatching {
                val user = requireNotNull(auth.currentUser) { "Not signed in" }
                val email = requireNotNull(user.email) { "Account has no email" }
                user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
                user.updatePassword(newPassword).await()
            }
        }

    suspend fun updateDisplayName(name: String): Result<Unit> = withContext(io) {
        runCatching {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        }
    }

    suspend fun updatePhotoUrl(url: String): Result<Unit> = withContext(io) {
        runCatching {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.updateProfile(
                userProfileChangeRequest { photoUri = android.net.Uri.parse(url) },
            ).await()
        }
    }

    fun signOut() = auth.signOut()

    /** True when the account signed in with Google and has no password to change. */
    fun isGoogleOnlyAccount(): Boolean {
        val providers = auth.currentUser?.providerData?.map { it.providerId }.orEmpty()
        return providers.contains(GoogleAuthProvider.PROVIDER_ID) &&
            !providers.contains(EmailAuthProvider.PROVIDER_ID)
    }
}

data class GoogleSignInResult(
    val user: FirebaseUser,
    val isNewAccount: Boolean,
)
