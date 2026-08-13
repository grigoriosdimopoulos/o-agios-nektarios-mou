package gr.agiosnektarios.village.core.model

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * A named neighbourhood ("block") of the village, drawn on the map as a polygon
 * carrying a badge with the number of open reports inside it.
 *
 * Geometry is loaded from `assets/village_blocks.json`; the live counters come
 * from the issues themselves so the asset stays a pure map of the village.
 */
data class VillageBlock(
    val id: String,
    val nameEl: String,
    val nameEn: String,
    val polygon: List<LatLng>,
) {
    val centroid: LatLng by lazy {
        if (polygon.isEmpty()) {
            LatLng(0.0, 0.0)
        } else {
            LatLng(
                polygon.sumOf { it.latitude } / polygon.size,
                polygon.sumOf { it.longitude } / polygon.size,
            )
        }
    }

    val bounds: LatLngBounds by lazy {
        LatLngBounds.builder().apply { polygon.forEach { include(it) } }.build()
    }

    fun localizedName(isGreek: Boolean): String = if (isGreek) nameEl else nameEn
}

/** A block plus the report tallies currently inside it. */
data class BlockSummary(
    val block: VillageBlock,
    val openCount: Int,
    val totalCount: Int,
    /** Category with the most open reports here; drives the badge colour. */
    val dominantCategory: IssueCategory?,
)
