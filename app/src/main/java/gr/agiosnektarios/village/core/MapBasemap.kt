package gr.agiosnektarios.village.core

/**
 * What the map draws underneath the village's own pins and outlines.
 *
 * Every one of these is keyless and free, because the whole app is. That rules
 * out the usual satellite and terrain providers, which want a billing account
 * before they hand over a token, and it means the two raster options here come
 * with attribution obligations rather than a contract — see [attribution],
 * which is rendered on the map and must not be removed.
 */
enum class MapBasemap(val id: String) {
    /**
     * The default. Vector tiles, so labels stay sharp and the style can follow
     * the app between light and dark.
     */
    STREETS("STREETS"),

    /** Aerial imagery. Useful for "the tree is behind that building". */
    SATELLITE("SATELLITE"),

    /** Contours and paths, for the tracks up the hillside around the village. */
    TERRAIN("TERRAIN"),
    ;

    companion object {
        fun fromId(id: String?): MapBasemap = entries.firstOrNull { it.id == id } ?: STREETS
    }
}

/**
 * MapLibre style documents for the raster basemaps.
 *
 * Written out as JSON rather than assembled from the SDK's builders because a
 * raster style is three objects and a style URL the SDK can fetch is exactly
 * what these providers do not offer.
 */
object MapStyles {
    /** Vector styles, hosted and keyless, which the SDK can fetch by URL. */
    const val STREETS_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val STREETS_DARK = "https://tiles.openfreemap.org/styles/dark"

    private const val SATELLITE_ATTRIBUTION =
        "© Esri, Maxar, Earthstar Geographics, and the GIS User Community"

    private const val TERRAIN_ATTRIBUTION =
        "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)"

    /**
     * Esri's World Imagery, which is served without a key.
     *
     * Note the tile template: Esri orders its path `{z}/{y}/{x}`, not the
     * `{z}/{x}/{y}` almost everything else uses. Getting that backwards yields
     * a map of the wrong part of the world rather than an error.
     */
    val SATELLITE: String = rasterStyle(
        tiles = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "World_Imagery/MapServer/tile/{z}/{y}/{x}",
        attribution = SATELLITE_ATTRIBUTION,
        maxZoom = 19,
    )

    /**
     * OpenTopoMap. Community-run and free, under a fair-use expectation that a
     * village of forty-six people sits comfortably inside.
     */
    val TERRAIN: String = rasterStyle(
        tiles = "https://tile.opentopomap.org/{z}/{x}/{y}.png",
        attribution = TERRAIN_ATTRIBUTION,
        maxZoom = 17,
    )

    fun streets(dark: Boolean): String = if (dark) STREETS_DARK else STREETS_LIGHT

    /**
     * Where MapLibre fetches letterforms for any text it has to draw.
     *
     * A style with no `glyphs` cannot render a symbol layer's text at all — it
     * fails silently, leaving the labels simply absent. That is not obvious
     * from the outside, and it is why the village's street names were invisible
     * on the satellite and terrain maps: the layer was there, the names were
     * there, and there was no font to draw them with.
     *
     * OpenFreeMap serves these, and Noto Sans covers Greek.
     */
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

    /** A fontstack the glyph endpoint above actually serves. */
    const val LABEL_FONT = "Noto Sans Regular"

    private fun rasterStyle(tiles: String, attribution: String, maxZoom: Int): String = """
        {
          "version": 8,
          "glyphs": "$GLYPHS",
          "sources": {
            "basemap": {
              "type": "raster",
              "tiles": ["$tiles"],
              "tileSize": 256,
              "maxzoom": $maxZoom,
              "attribution": "$attribution"
            }
          },
          "layers": [
            { "id": "basemap", "type": "raster", "source": "basemap" }
          ]
        }
    """.trimIndent()
}
