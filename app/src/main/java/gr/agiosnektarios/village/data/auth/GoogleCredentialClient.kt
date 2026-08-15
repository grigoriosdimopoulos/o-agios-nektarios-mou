package gr.agiosnektarios.village.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import gr.agiosnektarios.village.R
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when the resident dismissed the Google sheet — not an error worth showing. */
class GoogleSignInCancelled : Exception("Google sign-in cancelled")

/**
 * Raised when Credential Manager has nothing to offer: usually no Google account
 * on the device, or the app's signing certificate is not registered against the
 * Firebase project's OAuth client.
 */
class GoogleSignInUnavailable(cause: Throwable?) :
    Exception("No Google account available to sign in with", cause)

/**
 * Obtains a Google ID token through Credential Manager.
 *
 * Uses [GetSignInWithGoogleOption], which is the flow behind an explicit
 * "Sign in with Google" *button*: it always presents the account chooser. The
 * obvious-looking alternative, `GetGoogleIdOption`, is the seamless/one-tap
 * flow — it only returns accounts already authorised for this app, so on a
 * fresh install it fails with "No credentials available" no matter how many
 * Google accounts the phone has.
 */
@Singleton
class GoogleCredentialClient @Inject constructor() {

    suspend fun requestIdToken(activityContext: Context): Result<String> = runCatching {
        // Referenced directly rather than looked up by name: the resource is
        // generated from google-services.json, and resolving it via
        // getIdentifier(…, context.packageName) breaks on the debug variant,
        // whose applicationId carries a .debug suffix the resource table does
        // not use. A missing OAuth client now fails the build, not the user.
        val serverClientId = activityContext.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(activityContext)

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (cancelled: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (missing: NoCredentialException) {
            throw GoogleSignInUnavailable(missing)
        }

        val credential = response.credential
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type ${credential.type}"
        }
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
