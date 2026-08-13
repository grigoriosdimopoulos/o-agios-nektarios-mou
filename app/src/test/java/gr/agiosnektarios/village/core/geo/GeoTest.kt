package gr.agiosnektarios.village.core.geo

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `geohash matches the reference encoding`() {
        // The canonical worked example from the geohash specification.
        assertEquals("ezs42", geohash(42.6, -5.6, precision = 5))
    }

    @Test
    fun `geohash length follows the requested precision`() {
        assertEquals(8, geohash(37.848, 23.920).length)
        assertEquals(5, geohash(37.848, 23.920, precision = 5).length)
    }

    @Test
    fun `nearby points share a geohash prefix`() {
        // Two pins roughly 30 m apart must land in the same coarse cell, which
        // is what makes the prefix query in findSimilarNearby work at all.
        val a = geohash(37.8480, 23.9200, precision = 5)
        val b = geohash(37.8483, 23.9202, precision = 5)
        assertEquals(a, b)
    }

    @Test
    fun `distance between identical points is zero`() {
        val point = LatLng(37.848, 23.920)
        assertEquals(0.0, distanceMeters(point, point), 0.001)
    }

    @Test
    fun `distance matches a known separation`() {
        // One degree of latitude is ~111 km anywhere on the globe.
        val distance = distanceMeters(LatLng(37.0, 23.0), LatLng(38.0, 23.0))
        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = LatLng(37.8480, 23.9200)
        val b = LatLng(37.8510, 23.9260)
        assertEquals(distanceMeters(a, b), distanceMeters(b, a), 0.0001)
    }

    @Test
    fun `point inside a square polygon is detected`() {
        val square = listOf(
            LatLng(37.840, 23.910),
            LatLng(37.840, 23.930),
            LatLng(37.860, 23.930),
            LatLng(37.860, 23.910),
        )
        assertTrue(isPointInPolygon(LatLng(37.850, 23.920), square))
        assertFalse(isPointInPolygon(LatLng(37.870, 23.920), square))
        assertFalse(isPointInPolygon(LatLng(37.850, 23.940), square))
    }

    @Test
    fun `degenerate polygons contain nothing`() {
        val line = listOf(LatLng(37.84, 23.91), LatLng(37.86, 23.93))
        assertFalse(isPointInPolygon(LatLng(37.85, 23.92), line))
        assertFalse(isPointInPolygon(LatLng(37.85, 23.92), emptyList()))
    }

    @Test
    fun `metre conversions shrink with latitude`() {
        // A degree of longitude is narrower the further from the equator, which
        // is why the bounding-box pre-filter has to take latitude into account.
        val atEquator = metersToLngDegrees(100.0, atLatitude = 0.0)
        val atVillage = metersToLngDegrees(100.0, atLatitude = 37.85)
        assertTrue(atVillage > atEquator)
    }

    @Test
    fun `rough containment agrees with exact distance for clear cases`() {
        val center = LatLng(37.8480, 23.9200)
        val near = LatLng(37.8481, 23.9201)
        val far = LatLng(37.8600, 23.9400)

        assertTrue(isRoughlyWithin(center, near, meters = 50.0))
        assertFalse(isRoughlyWithin(center, far, meters = 50.0))
    }
}
