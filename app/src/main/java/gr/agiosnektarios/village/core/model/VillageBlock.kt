package gr.agiosnektarios.village.core.model

import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint

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
    val polygon: List<GeoPoint>,
) {
    val centroid: GeoPoint by lazy {
        if (polygon.isEmpty()) {
            GeoPoint(0.0, 0.0)
        } else {
            GeoPoint(
                polygon.sumOf { it.lat } / polygon.size,
                polygon.sumOf { it.lng } / polygon.size,
            )
        }
    }

    val bounds: GeoBounds by lazy {
        GeoBounds.around(polygon) ?: GeoBounds(0.0, 0.0, 0.0, 0.0)
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
