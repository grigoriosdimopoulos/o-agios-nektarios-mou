package gr.agiosnektarios.village.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * The package name and SHA-1 of the certificate that actually signed *this*
 * build, read at runtime.
 *
 * Google sign-in fails when that pair is not registered against the Firebase
 * project's OAuth client, and the failure looks identical to having no Google
 * account on the phone. Both times this app hit it, the answer took a round
 * trip: which certificate is this build carrying, and is that the one in the
 * console? The phone knows. It can say so.
 *
 * With Play App Signing, the certificate here is Google's, not the upload key —
 * which is exactly the distinction that makes this bug confusing, and exactly
 * why printing the real one settles it.
 *
 * Nothing secret is exposed: a signing certificate is public and sits inside
 * every copy of the APK.
 */
object SigningFingerprint {

    fun describe(context: Context): String =
        "${context.packageName}\n${sha1(context) ?: "—"}"

    fun sha1(context: Context): String? = runCatching {
        val signatures = signatures(context) ?: return null
        val cert = signatures.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-1")
            .digest(cert.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signatures(context: Context): Array<Signature>? {
        val pm = context.packageManager
        val name = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return null
            // hasMultipleSigners decides which of the two accessors is populated;
            // reading the wrong one returns null and the fingerprint silently
            // becomes a dash.
            if (signing.hasMultipleSigners()) signing.apkContentsSigners
            else signing.signingCertificateHistory
        } else {
            // API 26-27 predate GET_SIGNING_CERTIFICATES. minSdk is 26.
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures
        }
    }
}
