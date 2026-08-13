package gr.agiosnektarios.village.core.geo

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

/** Great-circle distance in metres between two points. */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2).pow(2) + sin(dLng / 2).pow(2) * cos(lat1) * cos(lat2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * Standard base-32 geohash.
 *
 * Precision 8 (~38 m cells) is what reports are stored with: fine enough to
 * narrow a "what is near here" query to a handful of documents, coarse enough
 * that a prefix query stays cheap. Shorter prefixes are derived by truncation.
 */
fun geohash(lat: Double, lng: Double, precision: Int = 8): String {
    require(precision in 1..12) { "geohash precision must be 1..12" }
    var latRange = -90.0 to 90.0
    var lngRange = -180.0 to 180.0
    val hash = StringBuilder(precision)
    var isEven = true
    var bit = 0
    var index = 0

    while (hash.length < precision) {
        if (isEven) {
            val mid = (lngRange.first + lngRange.second) / 2
            if (lng > mid) {
                index = index * 2 + 1
                lngRange = mid to lngRange.second
            } else {
                index *= 2
                lngRange = lngRange.first to mid
            }
        } else {
            val mid = (latRange.first + latRange.second) / 2
            if (lat > mid) {
                index = index * 2 + 1
                latRange = mid to latRange.second
            } else {
                index *= 2
                latRange = latRange.first to mid
            }
        }
        isEven = !isEven
        if (bit < 4) {
            bit++
        } else {
            hash.append(BASE32[index])
            bit = 0
            index = 0
        }
    }
    return hash.toString()
}

/**
 * Ray-casting point-in-polygon test on the flat lat/lng plane.
 *
 * At village scale the error from ignoring the earth's curvature is far below
 * the precision of a hand-placed pin, and this keeps block assignment
 * dependency-free and testable on the JVM.
 */
fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects = (pi.latitude > point.latitude) != (pj.latitude > point.latitude) &&
            point.longitude < (pj.longitude - pi.longitude) *
            (point.latitude - pi.latitude) / (pj.latitude - pi.latitude) + pi.longitude
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

/** Metres-per-degree of longitude shrinks with latitude; used for cheap bounding boxes. */
fun metersToLatDegrees(meters: Double): Double = meters / 111_320.0

fun metersToLngDegrees(meters: Double, atLatitude: Double): Double {
    val scale = cos(Math.toRadians(atLatitude)).coerceAtLeast(0.000001)
    return meters / (111_320.0 * scale)
}

/** Cheap pre-filter before the exact [distanceMeters] check. */
fun isRoughlyWithin(a: LatLng, b: LatLng, meters: Double): Boolean =
    abs(a.latitude - b.latitude) <= metersToLatDegrees(meters) &&
        abs(a.longitude - b.longitude) <= metersToLngDegrees(meters, a.latitude)
