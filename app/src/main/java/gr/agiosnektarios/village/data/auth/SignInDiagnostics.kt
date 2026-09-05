package gr.agiosnektarios.village.data.auth

import android.content.Context
import android.os.Build
import androidx.credentials.exceptions.GetCredentialException
import gr.agiosnektarios.village.BuildConfig
import gr.agiosnektarios.village.R

/**
 * Everything needed to explain a failed Google sign-in, in one copyable block.
 *
 * Written after three rounds of narrowing this down by relaying console
 * screenshots. Each round cost a release and ruled out one guess, because the
 * app reported a single sentence and the person holding the phone had no way to
 * see which of a dozen facts was the wrong one.
 *
 * So this gathers all of them at once: which application this is, which
 * certificate signed it, which project and OAuth client it asked, what Play
 * services answered and how fast. Whatever the cause turns out to be, the
 * answer is in here — that is the point of collecting the lot rather than the
 * field currently under suspicion.
 *
 * Nothing here is secret. The package name is on the store page, the
 * certificate is in every copy of the app, and the client and project ids ship
 * inside the APK. The API keys are deliberately left out: they add nothing to
 * a diagnosis and there is no reason to put them in a chat window.
 */
object SignInDiagnostics {

    fun report(context: Context, failure: Throwable?, elapsedMs: Long?): String {
        val lines = mutableListOf<String>()

        lines += "package: ${context.packageName}"
        lines += "sha1:    ${SigningFingerprint.sha1(context) ?: "—"}"
        lines += "build:   ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})"
        lines += "device:  Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})" +
            " · ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "play:    ${playServicesVersion(context)}"

        // What the app asked for, read from the resources the google-services
        // plugin generated — so this is the config actually compiled into the
        // build in the reader's hand, not the file somebody meant to ship.
        lines += "project: ${string(context, R.string.project_id)}"
        lines += "appid:   ${string(context, R.string.google_app_id)}"
        lines += "client:  ${string(context, R.string.default_web_client_id)}"

        lines += "result:  ${describe(failure)}"
        if (elapsedMs != null) lines += "after:   ${elapsedMs}ms"

        return lines.joinToString("\n")
    }

    private fun describe(failure: Throwable?): String {
        if (failure == null) return "no error recorded"
        val cause = (failure as? GoogleSignInUnavailable)?.cause ?: failure
        return buildString {
            append(cause::class.java.simpleName)
            // GetCredentialException carries a `type` that names the real
            // reason — the message is often empty, and the type is the field
            // that distinguishes "no account on the phone" from "this app is
            // not authorised", which read identically otherwise.
            (cause as? GetCredentialException)?.let {
                append(" · type=").append(it.type)
                it.errorMessage?.takeIf { m -> m.isNotBlank() }
                    ?.let { m -> append(" · ").append(m) }
            } ?: cause.message?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
    }

    private fun playServicesVersion(context: Context): String = runCatching {
        context.packageManager
            .getPackageInfo("com.google.android.gms", 0)
            .versionName ?: "—"
    }.getOrElse { "not installed" }

    private fun string(context: Context, id: Int): String =
        runCatching { context.getString(id) }.getOrElse { "—" }
}
