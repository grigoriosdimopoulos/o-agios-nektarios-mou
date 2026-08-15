package gr.agiosnektarios.village.core

import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint

/**
 * Where the village is, and how far the map lets you roam.
 *
 * These are approximate placeholder coordinates for Agios Nektarios, Attica.
 * Replace [CENTER] and the span constants with the surveyed extent of the
 * settlement — and `assets/village_blocks.json` with the real neighbourhood
 * outlines — before shipping. Nothing else in the app hardcodes a coordinate,
 * so this file plus that asset are the only places the geography lives.
 */
object VillageConfig {

    val CENTER = GeoPoint(37.8480, 23.9200)

    /** Half-height / half-width of the camera bounds, in degrees. */
    private const val LAT_SPAN = 0.0125
    private const val LNG_SPAN = 0.0160

    val BOUNDS: GeoBounds = GeoBounds(
        south = CENTER.lat - LAT_SPAN,
        west = CENTER.lng - LNG_SPAN,
        north = CENTER.lat + LAT_SPAN,
        east = CENTER.lng + LNG_SPAN,
    )

    const val DEFAULT_ZOOM = 15.5f
    const val MIN_ZOOM = 13.5f
    const val MAX_ZOOM = 20f

    /** Zoom used when the camera flies to a single report. */
    const val FOCUS_ZOOM = 18f

    /** A pin dropped outside this margin around [BOUNDS] is rejected on submit. */
    const val OUT_OF_BOUNDS_TOLERANCE_DEGREES = 0.004

    fun isInsideVillage(point: GeoPoint): Boolean =
        point in BOUNDS.expanded(OUT_OF_BOUNDS_TOLERANCE_DEGREES)

    /** Style URLs for the keyless OpenStreetMap tiles the map renders. */
    const val MAP_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
}
