package gr.agiosnektarios.village.core.geo

/**
 * A coordinate, owned by this app rather than by a mapping SDK.
 *
 * The clustering, the neighbourhood geometry and the repositories all speak
 * this type, so the map library stays an implementation detail of the map
 * screen — swapping Google Maps for MapLibre touched no business logic, and the
 * geo tests run on a plain JVM with no Android dependency at all.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
)

/**
 * An axis-aligned rectangle. Deliberately minimal: the app only ever needs to
 * frame a neighbourhood and to fence the camera in.
 */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    operator fun contains(point: GeoPoint): Boolean =
        point.lat in south..north && point.lng in west..east

    /** Grows the rectangle by [degrees] on every side. */
    fun expanded(degrees: Double): GeoBounds = expanded(degrees, degrees)

    /**
     * Grows the rectangle by a different amount on each axis.
     *
     * A degree of longitude is shorter than a degree of latitude everywhere but
     * the equator — at the village's 38°N it is about 79% as long — so a margin
     * meant to be an even distance in metres is not an even number of degrees.
     */
    fun expanded(latDegrees: Double, lngDegrees: Double): GeoBounds =
        GeoBounds(
            south = south - latDegrees,
            west = west - lngDegrees,
            north = north + latDegrees,
            east = east + lngDegrees,
        )

    companion object {
        /** Smallest rectangle containing every point; null for an empty list. */
        fun around(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            return GeoBounds(
                south = points.minOf { it.lat },
                west = points.minOf { it.lng },
                north = points.maxOf { it.lat },
                east = points.maxOf { it.lng },
            )
        }
    }
}
