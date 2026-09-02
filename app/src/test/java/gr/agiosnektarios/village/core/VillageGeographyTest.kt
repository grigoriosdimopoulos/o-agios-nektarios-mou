package gr.agiosnektarios.village.core

import gr.agiosnektarios.village.core.geo.GeoPoint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing that made the app open on the wrong village: the map
 * configuration and the geometry asset are two files that describe the same
 * place, and nothing stopped them describing different ones.
 *
 * It used to check the neighbourhood polygons. Those are gone, so it checks the
 * road network instead — the same guard over the only geometry left, rather
 * than a deleted test and an asset nobody is checking any more.
 *
 * The asset is read off the filesystem rather than through `RoadRepository`,
 * which needs an Android `Context` for `assets`. The point here is not the
 * parsing — it is that the shipped numbers agree.
 */
class VillageGeographyTest {

    private val asset = File("src/main/assets/village_roads.json")

    /**
     * Every vertex in the road network, as GeoJSON [lng, lat] pairs.
     *
     * GeoJSON puts longitude first. Reading them the other way round would put
     * this village in the Indian Ocean and every assertion below would pass or
     * fail for the wrong reason, so the order is spelled out here rather than
     * assumed.
     */
    private val coordinates: List<GeoPoint> by lazy {
        val text = asset.readText()
        // The file is a fixed shape this project generates, so a regex is
        // honest here — org.json is not on the JVM test classpath.
        val pairs = Regex("\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*]")
            .findAll(text)
            .map { GeoPoint(it.groupValues[2].toDouble(), it.groupValues[1].toDouble()) }
            .toList()
        assertTrue("no coordinate pairs parsed from ${asset.path}", pairs.isNotEmpty())
        pairs
    }

    @Test
    fun `road asset exists and has vertices`() {
        assertTrue("missing ${asset.path}", asset.isFile)
        assertTrue("no coordinates parsed", coordinates.isNotEmpty())
    }

    /**
     * The road network has to *be* this village — not every metre of it inside
     * the camera fence.
     *
     * Demanding that was the first thing I wrote and it failed honestly: the
     * road in from Vilia runs east out of the settlement, and 200-odd of its
     * vertices are legitimately outside BOUNDS because BOUNDS is where the
     * camera may travel, not where tarmac stops. What actually needs guarding
     * is the thing that once shipped broken — an asset describing somewhere
     * else — so this checks the network is centred on the village and that most
     * of it is inside the fence.
     */
    @Test
    fun `the road network is this village`() {
        val inside = coordinates.count { it in VillageConfig.BOUNDS }
        assertTrue(
            "only $inside of ${coordinates.size} road vertices are inside " +
                "VillageConfig.BOUNDS — this asset is probably another place",
            inside > coordinates.size / 2,
        )
        val centre = GeoPoint(
            coordinates.sumOf { it.lat } / coordinates.size,
            coordinates.sumOf { it.lng } / coordinates.size,
        )
        assertTrue(
            "the road network is centred on $centre, outside " +
                "VillageConfig.BUILT_UP ${VillageConfig.BUILT_UP}",
            centre in VillageConfig.BUILT_UP,
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
    fun `way ids are unique`() {
        val ids = Regex("\"wayId\"\\s*:\\s*\"([^\"]+)\"").findAll(asset.readText())
            .map { it.groupValues[1] }.toList()
        assertTrue("no wayId found in ${asset.path}", ids.isNotEmpty())
        assertEquals("duplicate way ids", ids.size, ids.distinct().size)
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
