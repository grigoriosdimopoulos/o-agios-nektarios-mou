package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * What the residents call a particular street, at `streetNames/{wayId}`.
 *
 * The document id is the OpenStreetMap way id carried in
 * `assets/village_roads.json`, so a name attaches to a specific piece of
 * geometry and survives the asset being regenerated from OSM.
 *
 * This collection exists because no reachable public dataset names the streets
 * of this village — OpenStreetMap has one named way in it, and the state's
 * valuation map lists names without a machine-readable link to geometry.
 * Matching those names to ways by position was tried three times and was wrong
 * every time; the settlement grid is rotated about 73 degrees, so there is no
 * consistent north-to-south or west-to-east order to match against.
 *
 * The people who live on a street do not have that problem. So the app asks
 * them, and [confirmedBy] is how a name earns confidence: one resident proposes,
 * others tap to agree. A name nobody has confirmed still renders — a plausible
 * name from a neighbour beats a blank line — but the count is visible, and a
 * moderator can correct anything.
 */
data class StreetName(
    /** The OSM way id. Same value as the `wayId` property on the map feature. */
    @DocumentId val wayId: String = "",
    val name: String = "",
    val proposedById: String = "",
    val proposedByName: String = "",
    /**
     * Residents who have agreed this name is right, the proposer included.
     *
     * A list rather than a counter so nobody can confirm twice and so the rules
     * can police additions: a resident may add their own uid and no other.
     */
    val confirmedBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val confirmations: Int get() = confirmedBy.size

    fun isConfirmedBy(userId: String?): Boolean = userId != null && userId in confirmedBy

    /** The proposer may correct their own entry; moderators may correct any. */
    fun canEdit(viewer: UserProfile?): Boolean =
        viewer != null && (viewer.id == proposedById || viewer.canModerate)

    companion object {
        /** Long enough for "Λεωφόρος Κιθαιρώνος", short enough to render on a line. */
        const val MAX_LENGTH = 60

        /** Matches the cap the rules put on `proposedByName`. */
        const val MAX_AUTHOR = 80
    }
}
