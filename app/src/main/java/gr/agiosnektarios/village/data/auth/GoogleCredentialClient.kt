package gr.agiosnektarios.village.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import gr.agiosnektarios.village.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Raised when the resident dismissed the Google sheet — not an error worth showing. */
class GoogleSignInCancelled(val elapsedMs: Long? = null) :
    Exception("Google sign-in cancelled")

/**
 * Below this, a "cancellation" was not a person.
 *
 * Play services reports the same [GetCredentialCancellationException] whether
 * somebody swiped the sheet away or it closed the flow itself because this
 * app's certificate is not authorised for the OAuth client. The app treated
 * both as a deliberate dismissal and stayed silent — which is right for the
 * first and, for the second, produced a button that appeared to do nothing at
 * all. That is exactly how it was reported, twice.
 *
 * Nobody dismisses a sheet in under a second, because nobody has seen it yet.
 */
private const val HUMAN_DISMISS_FLOOR_MS = 900L

/**
 * Raised when Credential Manager has nothing to offer: usually no Google account
 * on the device, or the app's signing certificate is not registered against the
 * Firebase project's OAuth client.
 */
class GoogleSignInUnavailable(cause: Throwable?, val elapsedMs: Long? = null) :
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

        // Credential Manager hosts a bottom sheet, so it needs the Activity —
        // not whatever `LocalContext.current` happens to hand back, which in a
        // themed Compose tree is a ContextWrapper around it. Given the wrong
        // one it cannot show its UI, and the symptom is the worst kind: the
        // call neither returns nor throws, the button sits disabled, and
        // nothing is ever reported.
        val activity = activityContext.findActivity()
            ?: throw GoogleSignInUnavailable(
                IllegalStateException("no Activity behind ${activityContext.javaClass.simpleName}")
            )
        val credentialManager = CredentialManager.create(activity)

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val startedAt = SystemClock.elapsedRealtime()
        val response = try {
            // A backstop, not a deadline. Choosing an account is a human
            // action and three minutes is far longer than anyone needs; what
            // this rules out is the case above, where the call never comes
            // back at all and the screen stays dead with nothing to report.
            withTimeout(SHEET_TIMEOUT_MS) {
                credentialManager.getCredential(activity, request)
            }
        } catch (timeout: TimeoutCancellationException) {
            throw GoogleSignInUnavailable(
                IllegalStateException("Credential Manager did not answer in 3 minutes")
            )
        } catch (cancelled: GetCredentialCancellationException) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < HUMAN_DISMISS_FLOOR_MS) {
                throw GoogleSignInUnavailable(
                    IllegalStateException(
                        "closed itself immediately — this app is probably not" +
                            " authorised for the OAuth client"
                    ),
                    elapsed,
                )
            }
            throw GoogleSignInCancelled(elapsed)
        } catch (missing: NoCredentialException) {
            throw GoogleSignInUnavailable(missing, SystemClock.elapsedRealtime() - startedAt)
        } catch (other: GetCredentialException) {
            // Everything else Credential Manager can raise —
            // GetCredentialUnknownException, provider configuration failures,
            // the custom exceptions Play services wraps its own errors in.
            //
            // These used to fall through to the generic handler, which shows
            // `localizedMessage`. Several of them carry a blank one, so a
            // misconfigured OAuth client produced an empty red line and no
            // information at all. The type is the part worth reading.
            throw GoogleSignInUnavailable(other, SystemClock.elapsedRealtime() - startedAt)
        }

        val credential = response.credential
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type ${credential.type}"
        }
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private companion object {
        const val SHEET_TIMEOUT_MS = 180_000L
    }
}

/** Walks out of the ContextWrapper chain Compose hands back, to the Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
