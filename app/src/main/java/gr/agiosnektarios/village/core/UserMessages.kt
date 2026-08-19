package gr.agiosnektarios.village.core

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.R
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turning a Firebase failure into a sentence a resident can read.
 *
 * Every screen used to show `error.localizedMessage`, which for the Firestore
 * SDK is a fixed English string built for developers: a villager tapping
 * "create" on a new conversation was shown
 * `PERMISSION_DENIED: Missing or insufficient permissions.` in an otherwise
 * entirely Greek app. That is not a translation problem so much as an audience
 * one — the message names an internal status code and offers nothing the
 * person holding the phone can act on.
 *
 * The mapping is deliberately coarse. There are twenty-odd status codes and
 * only a handful of genuinely different situations behind them: you are not
 * allowed, you are not connected, your session has lapsed, or something else
 * went wrong. Anything finer would be inventing distinctions the person cannot
 * use.
 */
@Singleton
class UserMessages @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Null in, null out, so `errorMessage = messages.of(...)` reads naturally. */
    fun of(error: Throwable?): String? {
        if (error == null) return null
        return context.getString(resourceFor(error))
    }

    private fun resourceFor(error: Throwable): Int = when {
        error is FirebaseFirestoreException -> when (error.code) {
            // Almost always a rules deployment that has not happened yet, which
            // is a thing an administrator fixes and a resident cannot. The
            // wording says so without naming Firestore.
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_not_allowed
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> R.string.error_signed_out
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> R.string.error_no_network
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> R.string.error_busy
            else -> R.string.error_generic
        }
        error is IOException -> R.string.error_no_network
        else -> R.string.error_generic
    }
}
