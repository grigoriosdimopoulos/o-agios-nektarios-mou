package gr.agiosnektarios.village.core.geo

import gr.agiosnektarios.village.core.model.VillageBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Distances are checked against values worked out separately, not read back
 * out of the implementation. At this latitude a degree of longitude is about
 * 87.5 km and a degree of latitude about 111.3 km, so the numbers below are
 * arithmetic anybody can repeat.
 */
class PlaceNamesTest {

    private val centre = GeoPoint(38.1640, 23.2900)

    /** A 200 m east-west segment through the centre point's latitude. */
    private val eastWest = PlaceNames.Way(
        wayId = "ew",
        points = listOf(
            GeoPoint(38.1640, 23.2889),
            GeoPoint(38.1640, 23.2911),
        ),
    )

    private val block = VillageBlock(
        id = "centre",
        nameEl = "Κέντρο",
        nameEn = "Centre",
        polygon = listOf(
            GeoPoint(38.1630, 23.2880),
            GeoPoint(38.1630, 23.2920),
            GeoPoint(38.1650, 23.2920),
            GeoPoint(38.1650, 23.2880),
        ),
    )

    @Test
    fun `a point on the line is on the street`() {
        assertEquals(0.0, PlaceNames.distanceToWay(centre, eastWest.points), 0.5)
    }

    /**
     * 0.0002 degrees of latitude is 22 m. The perpendicular distance to an
     * east-west line is exactly that, and getting the two axes' scales the
     * wrong way round would give 17 m instead.
     */
    @Test
    fun `perpendicular distance uses the right scale for each axis`() {
        val north = GeoPoint(38.1642, 23.2900)
        assertEquals(22.3, PlaceNames.distanceToWay(north, eastWest.points), 1.0)
    }

    /** Past the end of a segment the answer is the distance to its endpoint. */
    @Test
    fun `beyond the end it measures to the end`() {
        val past = GeoPoint(38.1640, 23.2930)
        val expected = distanceMeters(past, GeoPoint(38.1640, 23.2911))
        assertEquals(expected, PlaceNames.distanceToWay(past, eastWest.points), 1.0)
    }

    /**
     * OpenStreetMap really does contain ways with two identical consecutive
     * points. A zero-length segment must not divide by zero.
     */
    @Test
    fun `a degenerate segment does not blow up`() {
        val degenerate = listOf(centre, centre)
        assertEquals(0.0, PlaceNames.distanceToWay(centre, degenerate), 0.001)
        assertTrue(PlaceNames.distanceToWay(GeoPoint(38.1650, 23.2900), degenerate) > 100.0)
    }

    @Test
    fun `names the street and the neighbourhood`() {
        val place = PlaceNames.describe(
            point = centre,
            ways = listOf(eastWest),
            namesByWay = mapOf("ew" to "Οδός Ελατιάς"),
            blocks = listOf(block),
        )
        assertEquals("Οδός Ελατιάς", place.streetName)
        assertEquals("Κέντρο", place.block?.nameEl)
        assertTrue(place.isKnown)
    }

    /**
     * Eighty-one of this village's eighty-two ways have no name yet, so the
     * ordinary case is a neighbourhood and nothing else — and that has to be a
     * useful answer rather than an empty one.
     */
    @Test
    fun `an unnamed street still leaves the neighbourhood`() {
        val place = PlaceNames.describe(
            point = centre,
            ways = listOf(eastWest),
            namesByWay = emptyMap(),
            blocks = listOf(block),
        )
        assertNull(place.streetName)
        assertEquals("Κέντρο", place.block?.nameEl)
        assertTrue(place.isKnown)
    }

    /** A named street too far away must not be claimed. */
    @Test
    fun `a distant street is not this street`() {
        val far = GeoPoint(38.1646, 23.2900) // ~67 m north of the line
        val place = PlaceNames.describe(
            point = far,
            ways = listOf(eastWest),
            namesByWay = mapOf("ew" to "Οδός Ελατιάς"),
            blocks = listOf(block),
        )
        assertNull(place.streetName)
        assertEquals("Κέντρο", place.block?.nameEl)
    }

    /** Outside every polygon and every street, there is nothing honest to say. */
    @Test
    fun `outside the village it admits it knows nothing`() {
        val place = PlaceNames.describe(
            point = GeoPoint(38.2000, 23.4000),
            ways = listOf(eastWest),
            namesByWay = mapOf("ew" to "Οδός Ελατιάς"),
            blocks = listOf(block),
        )
        assertNull(place.streetName)
        assertNull(place.block)
        assertTrue(!place.isKnown)
    }

    /** The nearest named street wins, not the first one in the list. */
    @Test
    fun `picks the nearest named way`() {
        val nearer = PlaceNames.Way(
            wayId = "near",
            points = listOf(GeoPoint(38.16405, 23.2889), GeoPoint(38.16405, 23.2911)),
        )
        val place = PlaceNames.describe(
            point = GeoPoint(38.1641, 23.2900),
            ways = listOf(eastWest, nearer),
            namesByWay = mapOf("ew" to "Μακριά", "near" to "Κοντά"),
            blocks = listOf(block),
        )
        assertEquals("Κοντά", place.streetName)
    }
}
