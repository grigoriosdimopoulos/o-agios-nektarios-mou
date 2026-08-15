package gr.agiosnektarios.village.core.firestore

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

/**
 * Not private: the deserialisation helpers below are `inline` and public, so
 * anything they reference has to be visible at every call site too.
 */
const val FIRESTORE_TAG = "Firestore"

/** Live query results. The listener is torn down when the collector goes away. */
fun Query.asFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration: ListenerRegistration =
        addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            when {
                error != null -> close(error)
                snapshot != null -> trySend(snapshot)
            }
        }
    awaitClose { registration.remove() }
}

/** Live document. Emits `null` while the document does not exist. */
fun DocumentReference.asFlow(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration: ListenerRegistration =
        addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            when {
                error != null -> close(error)
                else -> trySend(snapshot)
            }
        }
    awaitClose { registration.remove() }
}

/**
 * Degrades a live list to empty when its listener fails.
 *
 * A snapshot listener reports failure by ending the flow with an exception —
 * a missing index, a rule that says no, a revoked account. Collected in a
 * `viewModelScope` with nothing to catch it, that exception is not an error
 * message: it is an uncaught exception on the main scope, and it kills the
 * process. A badge counting unread chats must never be able to do that.
 *
 * So the guarantee lives here rather than at each call site. Every list this
 * app observes is a screen's worth of content, and every one of them has a
 * sensible empty rendering; the failure belongs in the log, where it can be
 * diagnosed, not in a crash. Reads degrade silently — writes still surface
 * their errors to the user, which is where correctness actually matters.
 */
fun <T> Flow<List<T>>.orEmptyOnError(query: String): Flow<List<T>> = catch { error ->
    Log.w(FIRESTORE_TAG, "Live query '$query' failed; rendering it empty", error)
    emit(emptyList())
}

/** [orEmptyOnError] for a single live document. */
fun <T> Flow<T?>.orNullOnError(query: String): Flow<T?> = catch { error ->
    Log.w(FIRESTORE_TAG, "Live document '$query' failed; rendering it absent", error)
    emit(null)
}

/**
 * Deserialises a query snapshot, dropping documents that fail to map instead of
 * failing the whole page. One malformed document — a field written by a newer
 * build, say — should never blank out the map.
 */
inline fun <reified T : Any> QuerySnapshot.toObjectsSafe(): List<T> =
    documents.mapNotNull { document ->
        runCatching { document.toObject(T::class.java) }
            .onFailure { Log.w(FIRESTORE_TAG, "Skipping unreadable document ${document.reference.path}", it) }
            .getOrNull()
    }

inline fun <reified T : Any> DocumentSnapshot?.toObjectSafe(): T? {
    val snapshot = this ?: return null
    if (!snapshot.exists()) return null
    return runCatching { snapshot.toObject(T::class.java) }
        .onFailure { Log.w(FIRESTORE_TAG, "Unreadable document ${snapshot.reference.path}", it) }
        .getOrNull()
}
