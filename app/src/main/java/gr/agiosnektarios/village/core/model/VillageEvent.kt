package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Something happening in the village, at `events/{id}`.
 *
 * The calendar exists for a reason the reports list cannot serve: most of what
 * a village of forty-six people needs to coordinate is not a fault. It is the
 * liturgy, the πανηγύρι, the Saturday somebody has decided to clear the forest
 * track, the day the μπλε κάδοι are actually emptied. Today that information
 * travels by telephone and by whoever happens to be at the καφενείο, which
 * means it reaches the people who are already in the loop.
 *
 * [attendees] is what makes a work day work. "Clearing the track on Saturday"
 * with nobody's name against it is a wish; the same line with six names against
 * it is an arrangement, and the sixth name is there because the fifth was.
 */
data class VillageEvent(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    /** When it starts, epoch millis. Required — an event without one is a note. */
    val startAt: Date? = null,
    /** Optional end. Null means "no stated end", not "instantaneous". */
    val endAt: Date? = null,
    /**
     * Whether the time of day is meaningful.
     *
     * A feast day is all-day; a liturgy at seven in the morning is not. Storing
     * the distinction avoids the usual fudge of writing 00:00 and then showing
     * "midnight" to everyone.
     */
    val allDay: Boolean = false,
    val kind: String = EventKind.OTHER.name,
    /** Where, in words. Free text: most of these places have no address. */
    val place: String = "",
    /**
     * Residents who said they are coming, as uid to display name.
     *
     * A map rather than two parallel arrays, and the reason is the security
     * rules rather than taste. With arrays, "I am coming" is a write that adds
     * one entry to each — and no rule can check that the name added belongs to
     * the uid added, so anyone could put a neighbour's name on a list. With a
     * map the whole thing is one condition: the write may touch exactly one
     * key, and that key must be the caller's own uid.
     *
     * It also happens to be safe against two people tapping at the same moment,
     * because Firestore updates a single nested key without reading the rest.
     */
    val attendees: Map<String, String> = emptyMap(),
    val authorId: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val eventKind: EventKind get() = EventKind.fromId(kind)

    val start: Long get() = startAt?.time ?: 0L

    val end: Long get() = endAt?.time ?: start

    /**
     * Whether the event is over.
     *
     * An all-day event is over at the end of its day, not at its start — a
     * feast on the ninth should not fall off the list at one minute past
     * midnight on the ninth.
     */
    fun isPast(now: Long): Boolean =
        if (allDay) now > (endAt?.time ?: start) + DAY_MS else now > maxOf(end, start + HOUR_MS)

    fun isAttending(userId: String): Boolean = userId.isNotBlank() && attendees.containsKey(userId)

    /** Names, in a stable order — a map has none of its own. */
    val attendeeNames: List<String> get() = attendees.values.sorted()

    companion object {
        const val MAX_TITLE = 90
        const val MAX_DESCRIPTION = 1200
        const val MAX_PLACE = 80
        const val MAX_ATTENDEE_NAME = 80
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val HOUR_MS = 60 * 60 * 1000L
    }
}

/**
 * What kind of thing it is.
 *
 * Kept short. Every extra category is a decision forced on whoever is typing
 * the event in, and the only ones that earn their place are the ones that
 * change how the row should read.
 */
enum class EventKind {
    /** Liturgy, vespers, a feast at the chapel. */
    CHURCH,

    /** The πανηγύρι and anything else the village turns up to together. */
    FESTIVAL,

    /** Clearing, planting, repairing — the ones with a list of names. */
    WORK_DAY,

    /** The residents' association, the assembly, a visit from the municipality. */
    MEETING,

    /** Rubbish collection, water cuts, the mobile surgery — things with a schedule. */
    SERVICE,

    OTHER,
    ;

    /** Whether "I'll be there" is a useful thing to offer on this kind. */
    val takesAttendance: Boolean
        get() = this == WORK_DAY || this == MEETING || this == FESTIVAL

    companion object {
        fun fromId(id: String?): EventKind = entries.firstOrNull { it.name == id } ?: OTHER
    }
}
