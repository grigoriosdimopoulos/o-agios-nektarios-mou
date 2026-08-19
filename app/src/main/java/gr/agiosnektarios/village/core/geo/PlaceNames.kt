package gr.agiosnektarios.village.core.geo

import gr.agiosnektarios.village.core.model.VillageBlock
import kotlin.math.max
import kotlin.math.min

/**
 * Saying where something is, in the village's own words.
 *
 * Every report in this app describes its location only because the person
 * typing it happened to say so — "ο στύλος στην κεντρική". The coordinates were
 * there all along and nothing turned them into a sentence, so a report read
 * from the list gave no idea where it was, and a stranger reading one gave no
 * idea at all.
 *
 * There is no public dataset to ask. OpenStreetMap names exactly one of this
 * settlement's eighty-two ways, so the answer is assembled from what the
 * village itself has: the neighbourhood polygons that ship with the app, and
 * whatever street names the residents have supplied through the map. When the
 * nearest street has no name yet, the neighbourhood alone is still a great deal
 * more than nothing.
 */
object PlaceNames {

    /**
     * How close a point has to be to a way before it counts as "on" it.
     *
     * Wide enough that a report pinned on a house is still attributed to the
     * road it faces, narrow enough that it does not reach across a block. The
     * built-up area is about 1.2 km across with roads roughly every 60 m.
     */
    const val ON_STREET_METRES = 35.0

    /** A road, reduced to what naming a place needs. */
    data class Way(val wayId: String, val points: List<GeoPoint>)

    /**
     * The nearest named way and the containing neighbourhood.
     *
     * Both halves are optional and the caller decides how to phrase what came
     * back, because the phrasing is different in Greek and English and belongs
     * in resources rather than here.
     */
    data class Place(
        val streetName: String?,
        val block: VillageBlock?,
        val metresToStreet: Double?,
    ) {
        val isKnown: Boolean get() = streetName != null || block != null
    }

    fun describe(
        point: GeoPoint,
        ways: List<Way>,
        namesByWay: Map<String, String>,
        blocks: List<VillageBlock>,
    ): Place {
        val block = blocks.firstOrNull { isPointInPolygon(point, it.polygon) }

        var bestName: String? = null
        var bestDistance = Double.MAX_VALUE
        for (way in ways) {
            val name = namesByWay[way.wayId]?.takeIf { it.isNotBlank() } ?: continue
            val distance = distanceToWay(point, way.points)
            if (distance < bestDistance) {
                bestDistance = distance
                bestName = name
            }
        }

        return if (bestName != null && bestDistance <= ON_STREET_METRES) {
            Place(bestName, block, bestDistance)
        } else {
            Place(null, block, null)
        }
    }

    /** Shortest distance from a point to any segment of a way, in metres. */
    fun distanceToWay(point: GeoPoint, way: List<GeoPoint>): Double {
        if (way.isEmpty()) return Double.MAX_VALUE
        if (way.size == 1) return distanceMeters(point, way.first())
        var best = Double.MAX_VALUE
        for (i in 0 until way.size - 1) {
            best = min(best, distanceToSegment(point, way[i], way[i + 1]))
        }
        return best
    }

    /**
     * Point-to-segment distance, done in a local flat frame.
     *
     * Projecting onto a plane scaled by the cosine of the latitude is accurate
     * to well under a metre over the couple of hundred metres any of these
     * segments spans, and it avoids the trigonometry a spherical cross-track
     * calculation would need for no gain at this size. Degenerate segments —
     * two identical points, which OpenStreetMap does contain — fall back to the
     * point distance rather than dividing by zero.
     */
    private fun distanceToSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val latScale = 111_320.0
        val lngScale = metersPerLngDegree(point.lat)

        val px = (point.lng - start.lng) * lngScale
        val py = (point.lat - start.lat) * latScale
        val ex = (end.lng - start.lng) * lngScale
        val ey = (end.lat - start.lat) * latScale

        val lengthSquared = ex * ex + ey * ey
        if (lengthSquared <= 1e-9) return distanceMeters(point, start)

        val t = ((px * ex + py * ey) / lengthSquared).coerceIn(0.0, 1.0)
        val dx = px - t * ex
        val dy = py - t * ey
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun metersPerLngDegree(latitude: Double): Double =
        111_320.0 * kotlin.math.cos(Math.toRadians(latitude))

    /** Kept for callers that only need to know whether a point is in the village. */
    fun clampToVillage(point: GeoPoint, bounds: GeoBounds): GeoPoint = GeoPoint(
        lat = max(bounds.south, min(bounds.north, point.lat)),
        lng = max(bounds.west, min(bounds.east, point.lng)),
    )
}

/**
 * "Οδός Ελατιάς, Κέντρο", or the neighbourhood alone, or nothing at all.
 *
 * Greek only, and deliberately. This string is written onto the document and
 * read by whoever opens it, so it has to be one text rather than something each
 * phone renders in its own language — and the village's streets and
 * neighbourhoods have Greek names. An English "Centre" would be a translation
 * of a proper noun.
 */
fun PlaceNames.Place?.label(): String =
    listOfNotNull(this?.streetName, this?.block?.nameEl).joinToString(", ")
