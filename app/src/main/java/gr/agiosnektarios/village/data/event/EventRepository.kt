package gr.agiosnektarios.village.data.event

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.orNullOnError
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.EventKind
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.VillageEvent
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The village calendar.
 *
 * Ordered by start time in Firestore rather than in memory, because unlike the
 * contacts this list does grow — a year of liturgies and rubbish days is a few
 * hundred documents — and `startAt` alone is a single-field index that exists
 * without being declared.
 */
@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val events get() = firestore.collection(Collections.EVENTS)

    /**
     * Everything from a little before now onward.
     *
     * The window starts in the recent past on purpose: an event that finished
     * this morning is still the answer to "was that today or tomorrow", and a
     * calendar that erases the day as it passes is disorienting.
     */
    fun observeUpcoming(limit: Long = 60): Flow<List<VillageEvent>> {
        val from = Date(System.currentTimeMillis() - VillageEvent.DAY_MS)
        return events
            .whereGreaterThanOrEqualTo("startAt", from)
            .orderBy("startAt", Query.Direction.ASCENDING)
            .limit(limit)
            .asFlow()
            .map { it.toObjectsSafe<VillageEvent>() }
            .orEmptyOnError("events")
    }

    fun observeEvent(eventId: String): Flow<VillageEvent?> =
        events.document(eventId).asFlow()
            .map { it.toObjectSafe<VillageEvent>() }
            .orNullOnError("event/$eventId")

    suspend fun save(
        id: String?,
        title: String,
        description: String,
        place: String,
        kind: EventKind,
        startAt: Long,
        endAt: Long?,
        allDay: Boolean,
        author: UserProfile,
    ): Result<String> = withContext(io) {
        val cleanTitle = title.trim().take(VillageEvent.MAX_TITLE)
        if (cleanTitle.isBlank() || startAt <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid event"))
        }
        runCatching {
            val document = if (id.isNullOrBlank()) events.document() else events.document(id)
            val fields = mutableMapOf<String, Any?>(
                "title" to cleanTitle,
                "description" to description.trim().take(VillageEvent.MAX_DESCRIPTION),
                "place" to place.trim().take(VillageEvent.MAX_PLACE),
                "kind" to kind.name,
                "startAt" to Date(startAt),
                "endAt" to endAt?.let(::Date),
                "allDay" to allDay,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (id.isNullOrBlank()) {
                fields["authorId"] = author.id
                fields["authorName"] = author.displayName
                // An organiser is coming to their own work day. Seeding the list
                // with them is not a courtesy, it is the difference between an
                // empty list that reads as "nobody" and one that reads as "one
                // so far".
                fields["attendees"] = mapOf(author.id to author.displayName)
                fields["createdAt"] = FieldValue.serverTimestamp()
                document.set(fields).await()
            } else {
                document.update(fields).await()
            }
            document.id
        }
    }

    /**
     * Says you are coming, or takes it back.
     *
     * Written as a single nested key — `attendees.<uid>` — rather than as a
     * whole-map replacement. That matters twice over: two neighbours tapping in
     * the same second cannot overwrite each other, and the security rules can
     * insist the write touches nobody's key but the caller's, which is what
     * stops one resident signing another up.
     *
     * The name is stored beside the uid rather than looked up, so a calendar
     * row does not have to fetch forty user documents to render six names. The
     * cost is that someone who later renames themselves keeps the old name
     * against events they had already joined.
     */
    suspend fun setAttending(
        eventId: String,
        user: UserProfile,
        attending: Boolean,
    ): Result<Unit> = withContext(io) {
        runCatching {
            events.document(eventId).update(
                mapOf(
                    "attendees." + user.id to if (attending) {
                        user.displayName.take(VillageEvent.MAX_ATTENDEE_NAME)
                    } else {
                        FieldValue.delete()
                    },
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Unit
        }
    }

    suspend fun delete(eventId: String): Result<Unit> = withContext(io) {
        runCatching {
            events.document(eventId).delete().await()
            Unit
        }
    }
}
