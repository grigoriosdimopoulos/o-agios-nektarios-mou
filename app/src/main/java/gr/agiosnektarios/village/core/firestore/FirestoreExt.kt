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

private const val TAG = "Firestore"

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
 * Deserialises a query snapshot, dropping documents that fail to map instead of
 * failing the whole page. One malformed document — a field written by a newer
 * build, say — should never blank out the map.
 */
inline fun <reified T : Any> QuerySnapshot.toObjectsSafe(): List<T> =
    documents.mapNotNull { document ->
        runCatching { document.toObject(T::class.java) }
            .onFailure { Log.w(TAG, "Skipping unreadable document ${document.reference.path}", it) }
            .getOrNull()
    }

inline fun <reified T : Any> DocumentSnapshot?.toObjectSafe(): T? {
    val snapshot = this ?: return null
    if (!snapshot.exists()) return null
    return runCatching { snapshot.toObject(T::class.java) }
        .onFailure { Log.w(TAG, "Unreadable document ${snapshot.reference.path}", it) }
        .getOrNull()
}
