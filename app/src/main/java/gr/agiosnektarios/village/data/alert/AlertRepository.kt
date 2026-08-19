package gr.agiosnektarios.village.data.alert

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.model.AlertKind
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageAlert
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * What is wrong in the village right now.
 *
 * Queried by `raisedAt` alone — a single-field range, so no composite index —
 * and filtered for liveness in memory. A village raises a handful of these a
 * year, so "the last fortnight" is a page of nothing much.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val alerts get() = firestore.collection(Collections.ALERTS)

    /**
     * Everything still live, worst first.
     *
     * The window is generous and the liveness test is local, because an alert
     * that has expired must disappear from the screen the moment it expires —
     * and a Firestore query cannot re-run itself as the clock moves.
     */
    fun observeActive(): Flow<List<VillageAlert>> =
        alerts
            .whereGreaterThanOrEqualTo("raisedAt", Date(System.currentTimeMillis() - WINDOW_MS))
            .orderBy("raisedAt", Query.Direction.DESCENDING)
            .limit(40)
            .asFlow()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                snapshot.toObjectsSafe<VillageAlert>()
                    .filter { it.isActive(now) }
                    .sortedWith(
                        compareBy(
                            { it.alertKind.severity.ordinal },
                            { -(it.raisedAt?.time ?: 0L) },
                        ),
                    )
            }
            .orEmptyOnError("alerts")

    suspend fun raise(
        kind: AlertKind,
        note: String,
        position: GeoPoint?,
        placeLabel: String,
        author: UserProfile,
    ): Result<String> = withContext(io) {
        runCatching {
            val document = alerts.document()
            document.set(
                mapOf(
                    "kind" to kind.name,
                    "note" to note.trim().take(VillageAlert.MAX_NOTE),
                    "lat" to position?.lat,
                    "lng" to position?.lng,
                    "placeLabel" to placeLabel.take(VillageAlert.MAX_NAME),
                    "raisedById" to author.id,
                    "raisedByName" to author.displayName.take(VillageAlert.MAX_NAME),
                    // Whoever raises it has it, so the count starts at one
                    // rather than at "nobody has said yet" under the person
                    // who just said it.
                    "confirmedBy" to listOf(author.id),
                    "confirmedNames" to listOf(author.displayName.take(VillageAlert.MAX_NAME)),
                    "raisedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            document.id
        }
    }

    /**
     * "I have it too", or taking it back.
     *
     * Array union rather than read-modify-write, so six neighbours discovering
     * the power is out in the same minute cannot overwrite one another. Names
     * ride in a parallel array purely for display; the ids are what count, and
     * the rules police the ids.
     */
    suspend fun setConfirmed(
        alertId: String,
        user: UserProfile,
        confirmed: Boolean,
    ): Result<Unit> = withContext(io) {
        val name = user.displayName.take(VillageAlert.MAX_NAME)
        runCatching {
            alerts.document(alertId).update(
                mapOf(
                    "confirmedBy" to if (confirmed) {
                        FieldValue.arrayUnion(user.id)
                    } else {
                        FieldValue.arrayRemove(user.id)
                    },
                    "confirmedNames" to if (confirmed) {
                        FieldValue.arrayUnion(name)
                    } else {
                        FieldValue.arrayRemove(name)
                    },
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Unit
        }
    }

    /** Marks it over. The person who raised it, or a moderator. */
    suspend fun resolve(alertId: String, userId: String): Result<Unit> = withContext(io) {
        runCatching {
            alerts.document(alertId).update(
                mapOf(
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "resolvedById" to userId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Unit
        }
    }

    private companion object {
        /** Long enough to cover the longest-lived kind, with room to spare. */
        const val WINDOW_MS = 14L * 24 * 60 * 60 * 1000
    }
}
