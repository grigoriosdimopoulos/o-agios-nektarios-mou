package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A reported problem or suggestion, stored at `issues/{issueId}`.
 *
 * Enum-valued fields are persisted as their stable string ids rather than as
 * enums so a document written by a newer build never fails to deserialise on an
 * older one — unknown ids degrade to [IssueCategory.OTHER] / [IssueStatus.OPEN].
 *
 * Vote and comment totals are denormalised counters the client maintains under
 * rules that police them; nobody writes anyone else's vote document.
 */
data class Issue(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = IssueCategory.OTHER.id,
    val statusId: String = IssueStatus.OPEN.id,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** Geohash prefix used for the cheap "same area" query before precise filtering. */
    val geohash: String = "",
    val blockId: String = "",
    /**
     * Where this is, in the village's own words: "Οδός Ελατιάς, Κέντρο".
     *
     * Worked out from the coordinates when the report is filed, and then left
     * alone. Every report in this app used to describe its location only
     * because the author happened to type it into the title, so a list of
     * reports told you what had happened and never where — on an app whose
     * other half is a map.
     *
     * Frozen at filing time rather than recomputed on read: a street the
     * village names next spring should not silently rewrite where last
     * autumn's reports say they were.
     */
    val placeLabel: String = "",
    /**
     * When this was sent to the municipality, and under what number.
     *
     * The app could record a pothole, gather twelve neighbours behind it and
     * then do nothing with it, because most of what gets reported here is the
     * δήμος's job and there was no way for a report to leave. Twelve people
     * agreeing is worth nothing until somebody forwards it; these two fields
     * are how the village records that somebody did, so the next person does
     * not send it again and everyone can see how long it has been sitting.
     */
    val reportedToCouncilAt: Date? = null,
    val councilReference: String = "",
    /**
     * How many photos live in `issues/{id}/photos`.
     *
     * Denormalised so a card can say "3 photos" without reading them: the
     * photos themselves are hundreds of kilobytes each and are fetched only
     * when someone opens the report.
     */
    val photoCount: Int = 0,
    /**
     * A postage-stamp copy of the first photo, at most a few kilobytes.
     *
     * This one is inline because the list and the map already read every issue
     * document, and a card with no picture at all reads as a bug. Anything
     * bigger than a stamp would make that same read enormous — see
     * [gr.agiosnektarios.village.data.media.ImageSpec.ISSUE_THUMBNAIL].
     */
    val thumbnail: Blob? = null,
    val authorId: String = "",
    val authorName: String = "",
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    /** upvotes - downvotes, denormalised so Firestore can order by popularity. */
    val score: Int = 0,
    val commentCount: Int = 0,
    /**
     * Who said they would deal with this, if anyone.
     *
     * The status field tracks what has happened to a report; this tracks who
     * is doing it, and in a village of forty-six people that is the part that
     * actually moves things. "Someone should clear that" is how a problem sits
     * for a year. "Ο Δημήτρης το ανέλαβε" is how it gets cleared, and the app
     * was recording the first and not the second.
     *
     * Any resident may take a report, and the person holding it may let it go
     * again — this is a claim, not an assignment handed down by anyone.
     */
    val assigneeId: String = "",
    val assigneeName: String = "",
    val assignedAt: Date? = null,
    val resolutionNote: String = "",
    val resolvedById: String = "",
    val resolvedByName: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val category: IssueCategory get() = IssueCategory.fromId(categoryId)
    val status: IssueStatus get() = IssueStatus.fromId(statusId)
    val isOpen: Boolean get() = !status.isTerminal

    val isTaken: Boolean get() = assigneeId.isNotBlank()

    fun isTakenBy(viewer: UserProfile?): Boolean =
        viewer != null && assigneeId == viewer.id

    /** Anyone may take an open report; only the holder or a moderator lets it go. */
    fun canTake(viewer: UserProfile?): Boolean =
        viewer != null && isOpen && !isTaken

    fun canRelease(viewer: UserProfile?): Boolean =
        viewer != null && isTaken && (assigneeId == viewer.id || viewer.canModerate)

    fun canEdit(viewer: UserProfile?): Boolean =
        viewer != null && (viewer.id == authorId || viewer.canModerate)

    fun canDelete(viewer: UserProfile?): Boolean = canEdit(viewer)

    /** Only the author and moderators may resolve or decline a report. */
    fun canChangeStatus(viewer: UserProfile?): Boolean = canEdit(viewer)
}

/**
 * A single resident's vote, at `issues/{issueId}/votes/{uid}`.
 *
 * [userId] duplicates the document id on purpose: a collection-group query
 * cannot filter on document id, and removing every vote a departing resident
 * ever cast needs exactly that query.
 */
data class Vote(
    @DocumentId val id: String = "",
    val userId: String = "",
    /** +1 for an upvote, -1 for a downvote. Absent document means no vote. */
    val value: Int = 0,
    @ServerTimestamp val createdAt: Date? = null,
)

/** A comment on an issue, at `issues/{issueId}/comments/{commentId}`. */
data class Comment(
    @DocumentId val id: String = "",
    val issueId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Date? = null,
) {
    fun canDelete(viewer: UserProfile?): Boolean =
        viewer != null && (viewer.id == authorId || viewer.canModerate)
}

/**
 * One full-size photo attached to a report, at `issues/{issueId}/photos/{id}`.
 *
 * A document of its own rather than a field on the report, because the map and
 * the issue list read every report in the village and must not pay for pictures
 * nobody has asked to see. Opening a report is what fetches these.
 */
data class IssuePhoto(
    @DocumentId val id: String = "",
    /** JPEG bytes. Firestore stores them natively, so nothing is base64-inflated. */
    val data: Blob? = null,
    val width: Int = 0,
    val height: Int = 0,
    val authorId: String = "",
    @ServerTimestamp val createdAt: Date? = null,
) {
    val bytes: ByteArray? get() = data?.toBytes()
}
