package gr.agiosnektarios.village.core.model

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
 * Vote and comment totals are denormalised counters maintained by Cloud
 * Functions; clients only ever write their own vote document.
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
    val photoUrls: List<String> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    /** upvotes - downvotes, denormalised so Firestore can order by popularity. */
    val score: Int = 0,
    val commentCount: Int = 0,
    val resolutionNote: String = "",
    val resolvedById: String = "",
    val resolvedByName: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val category: IssueCategory get() = IssueCategory.fromId(categoryId)
    val status: IssueStatus get() = IssueStatus.fromId(statusId)
    val isOpen: Boolean get() = !status.isTerminal

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
    val authorPhotoUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Date? = null,
) {
    fun canDelete(viewer: UserProfile?): Boolean =
        viewer != null && (viewer.id == authorId || viewer.canModerate)
}
