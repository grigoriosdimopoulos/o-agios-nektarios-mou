package gr.agiosnektarios.village.core

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

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

    val CENTER = LatLng(37.8480, 23.9200)

    /** Half-height / half-width of the camera bounds, in degrees. */
    private const val LAT_SPAN = 0.0125
    private const val LNG_SPAN = 0.0160

    val BOUNDS: LatLngBounds = LatLngBounds(
        LatLng(CENTER.latitude - LAT_SPAN, CENTER.longitude - LNG_SPAN),
        LatLng(CENTER.latitude + LAT_SPAN, CENTER.longitude + LNG_SPAN),
    )

    const val DEFAULT_ZOOM = 15.5f
    const val MIN_ZOOM = 13.5f
    const val MAX_ZOOM = 20f

    /** Zoom used when the camera flies to a single report. */
    const val FOCUS_ZOOM = 18f

    /** A pin dropped outside this margin around [BOUNDS] is rejected on submit. */
    const val OUT_OF_BOUNDS_TOLERANCE_DEGREES = 0.004

    fun isInsideVillage(point: LatLng): Boolean {
        val t = OUT_OF_BOUNDS_TOLERANCE_DEGREES
        return point.latitude >= BOUNDS.southwest.latitude - t &&
            point.latitude <= BOUNDS.northeast.latitude + t &&
            point.longitude >= BOUNDS.southwest.longitude - t &&
            point.longitude <= BOUNDS.northeast.longitude + t
    }
}
