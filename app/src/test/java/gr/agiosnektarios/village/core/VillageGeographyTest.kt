package gr.agiosnektarios.village.core

import gr.agiosnektarios.village.core.geo.GeoPoint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing that made the app open on the wrong village: the map
 * configuration and the neighbourhood asset are two files that describe the
 * same place, and nothing stopped them describing different ones.
 *
 * The asset is read off the filesystem rather than through
 * `VillageBlockRepository`, which needs an Android `Context` for `assets`. The
 * point here is not the parsing — it is that the shipped numbers agree.
 */
class VillageGeographyTest {

    private val asset = File("src/main/assets/village_blocks.json")

    private val coordinates: List<GeoPoint> by lazy {
        val text = asset.readText()
        // The file is a fixed shape this project generates, so a pair of
        // regexes is honest here — a JSON parser would only be ceremony, and
        // org.json is not on the JVM test classpath anyway.
        val lats = Regex("\"lat\"\\s*:\\s*(-?[0-9.]+)").findAll(text)
            .map { it.groupValues[1].toDouble() }.toList()
        val lngs = Regex("\"lng\"\\s*:\\s*(-?[0-9.]+)").findAll(text)
            .map { it.groupValues[1].toDouble() }.toList()
        assertEquals("every lat should be paired with an lng", lats.size, lngs.size)
        lats.zip(lngs) { lat, lng -> GeoPoint(lat, lng) }
    }

    @Test
    fun `neighbourhood asset exists and has vertices`() {
        assertTrue("missing ${asset.path}", asset.isFile)
        assertTrue("no coordinates parsed", coordinates.isNotEmpty())
    }

    @Test
    fun `every neighbourhood vertex lies inside the camera bounds`() {
        val stray = coordinates.filterNot { it in VillageConfig.BOUNDS }
        assertTrue(
            "these vertices fall outside VillageConfig.BOUNDS, so the blocks would " +
                "draw somewhere the camera cannot go: $stray",
            stray.isEmpty(),
        )
    }

    @Test
    fun `the village centre sits inside the built-up area`() {
        assertTrue(
            "CENTER ${VillageConfig.CENTER} is outside BUILT_UP ${VillageConfig.BUILT_UP}, " +
                "so the map would open looking at empty ground",
            VillageConfig.CENTER in VillageConfig.BUILT_UP,
        )
    }

    @Test
    fun `the camera bounds contain the built-up area`() {
        val built = VillageConfig.BUILT_UP
        val fence = VillageConfig.BOUNDS
        assertTrue(
            "the camera fence $fence does not contain the settlement $built",
            GeoPoint(built.south, built.west) in fence &&
                GeoPoint(built.north, built.east) in fence,
        )
    }

    @Test
    fun `block ids are unique`() {
        val ids = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(asset.readText())
            .map { it.groupValues[1] }.toList()
        assertEquals("duplicate block ids", ids.size, ids.distinct().size)
    }

    /**
     * The settlement is small — 372.3 stremmas as founded. A future edit that
     * accidentally reintroduces a placeholder spanning half of Attica should
     * fail here rather than ship.
     */
    @Test
    fun `the mapped area stays the size of a village`() {
        val b = VillageConfig.BOUNDS
        val northSouthMetres = (b.north - b.south) * 111_130
        val eastWestMetres = (b.east - b.west) * 87_530
        assertTrue("village is $northSouthMetres m tall", northSouthMetres in 500.0..4_000.0)
        assertTrue("village is $eastWestMetres m wide", eastWestMetres in 500.0..4_000.0)
    }
}
