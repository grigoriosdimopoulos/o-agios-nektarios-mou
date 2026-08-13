package gr.agiosnektarios.village.core.geo

import com.google.android.gms.maps.model.LatLng
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import kotlin.math.cos
import kotlin.math.pow

/**
 * A pin on the map. A cluster of one renders as a plain marker; anything larger
 * renders as a counter bubble that expands into a list when tapped.
 */
data class IssueCluster(
    val id: String,
    val position: LatLng,
    val issues: List<Issue>,
) {
    val size: Int get() = issues.size
    val isSingle: Boolean get() = issues.size == 1

    /** Null when the cluster mixes categories, which only happens when zoomed out. */
    val category: IssueCategory? =
        issues.map { it.category }.distinct().singleOrNull()

    val openCount: Int get() = issues.count { it.isOpen }

    /** The most-discussed report leads the cluster's preview card. */
    val representative: Issue =
        issues.maxByOrNull { it.score * 3 + it.commentCount } ?: issues.first()
}

/**
 * Groups nearby reports into map pins.
 *
 * Two rules combine here:
 *
 * 1. *Same problem* — reports of the same category within [SAME_PROBLEM_METERS]
 *    are one real-world problem reported by several neighbours, so they always
 *    merge no matter how far in you zoom.
 * 2. *Legibility* — beyond that, the merge radius is derived from the current
 *    zoom so pins never overlap on screen. Zoomed far out, categories stop
 *    mattering and everything within the radius merges into one counter.
 *
 * The algorithm is a single greedy pass over reports sorted by recency, which
 * keeps clustering stable as new reports arrive: an existing cluster keeps its
 * identity instead of being reshuffled.
 */
object IssueClustering {

    /** Reports of one category closer than this are treated as the same problem. */
    const val SAME_PROBLEM_METERS = 45.0

    /** Below this zoom the map is too small to keep categories apart. */
    private const val MIXED_CATEGORY_ZOOM = 15f

    /** Roughly the on-screen radius of a marker, in pixels, at any zoom. */
    private const val MARKER_PIXEL_RADIUS = 44.0

    fun cluster(issues: List<Issue>, zoom: Float): List<IssueCluster> {
        if (issues.isEmpty()) return emptyList()

        val mergeCategories = zoom < MIXED_CATEGORY_ZOOM
        val radius = mergeRadiusMeters(zoom, issues.first().lat)

        val buckets = mutableListOf<MutableCluster>()
        // Oldest first: the first report of a problem anchors the cluster.
        val ordered = issues.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }

        for (issue in ordered) {
            val point = LatLng(issue.lat, issue.lng)
            val host = buckets.firstOrNull { bucket ->
                val categoriesMatch = mergeCategories || bucket.categoryId == issue.categoryId
                if (!categoriesMatch) return@firstOrNull false
                val limit = if (mergeCategories) radius else maxOf(radius, SAME_PROBLEM_METERS)
                isRoughlyWithin(bucket.center(), point, limit) &&
                    distanceMeters(bucket.center(), point) <= limit
            }
            if (host != null) host.add(issue) else buckets += MutableCluster(issue)
        }

        return buckets.map { bucket ->
            IssueCluster(
                // Anchoring the id on the oldest member keeps marker state (and
                // therefore the drop animation) stable across recompositions.
                id = bucket.issues.first().id,
                position = bucket.center(),
                issues = bucket.issues.sortedByDescending { it.createdAt?.time ?: 0L },
            )
        }
    }

    /**
     * Screen-space radius converted to metres at the given zoom, using the Web
     * Mercator ground resolution.
     */
    private fun mergeRadiusMeters(zoom: Float, latitude: Double): Double {
        val metersPerPixel = 156_543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom.toDouble())
        return (MARKER_PIXEL_RADIUS * metersPerPixel).coerceIn(10.0, 1_500.0)
    }

    private class MutableCluster(first: Issue) {
        val issues = mutableListOf(first)
        val categoryId = first.categoryId
        private var latSum = first.lat
        private var lngSum = first.lng

        fun add(issue: Issue) {
            issues += issue
            latSum += issue.lat
            lngSum += issue.lng
        }

        fun center() = LatLng(latSum / issues.size, lngSum / issues.size)
    }
}
