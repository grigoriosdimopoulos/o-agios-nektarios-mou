package gr.agiosnektarios.village.core

import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint

/**
 * Where the village is, and how far the map lets you roam.
 *
 * Άγιος Νεκτάριος Αττικής: a settlement in the Vilia municipal unit of
 * Mandra-Eidyllia, West Attica, at roughly 640 m altitude, with 46 permanent
 * residents at the 2021 census. OpenStreetMap tags it `place=village` at
 * 38.16304 N, 23.28997 E — not to be confused with the several churches and
 * monasteries of the same name elsewhere in Attica.
 *
 * The numbers below are surveyed rather than estimated. [BUILT_UP] is the
 * bounding box of every residential, living_street and service way
 * OpenStreetMap records in the settlement: 1240 m north-south by 1139 m
 * east-west, which matches the 372.3 stremmas the 1970 building cooperative
 * was founded on. `assets/village_blocks.json` divides exactly that rectangle.
 *
 * Nothing else in the app hardcodes a coordinate: this file and that asset are
 * the only places the geography lives.
 */
object VillageConfig {

    /**
     * The built-up settlement, as mapped.
     *
     * Kept separate from [BOUNDS] so the two meanings never drift: this is
     * where the village *is*, while [BOUNDS] is how far the camera may travel.
     */
    val BUILT_UP: GeoBounds = GeoBounds(
        south = 38.158395,
        west = 23.285657,
        north = 38.169553,
        east = 23.298671,
    )

    /**
     * The camera fence: the settlement plus a 250 m margin, so the roads in and
     * the ground immediately around the village stay reachable while the map
     * still cannot be dragged off to another part of Attica.
     */
    val BOUNDS: GeoBounds = BUILT_UP.expanded(
        latDegrees = 250.0 / 111_130.0,
        lngDegrees = 250.0 / 87_530.0,
    )

    val CENTER: GeoPoint = BOUNDS.center

    /**
     * How high the village sits, in metres.
     *
     * Used when asking a weather provider for a forecast: without it the
     * provider answers for the mean height of its own grid cell, and around
     * Kithairon one cell holds both the Vilia plain and the ridge above the
     * settlement. A few hundred metres of error there is a couple of degrees on
     * the temperature and a wrong answer about whether it is snowing.
     */
    const val ELEVATION_METRES = 640

    /**
     * Zoom levels chosen against the real size of the place.
     *
     * The fenced area is about 1.7 km across, so on a typical phone [MIN_ZOOM]
     * of 14.5 already shows more than all of it — zooming out further would
     * only add land the camera is not allowed to visit. [DEFAULT_ZOOM] frames
     * roughly a kilometre, which is the whole settlement with its edges.
     */
    const val DEFAULT_ZOOM = 15.5f
    const val MIN_ZOOM = 14.5f
    const val MAX_ZOOM = 20f

    /** Zoom used when the camera flies to a single report. */
    const val FOCUS_ZOOM = 18f

    /**
     * A pin dropped outside this margin around [BOUNDS] is rejected on submit.
     *
     * ~130 m. Small, because the village is small: a report a kilometre up the
     * mountain is a mistake, not a report.
     */
    const val OUT_OF_BOUNDS_TOLERANCE_DEGREES = 0.0012

    fun isInsideVillage(point: GeoPoint): Boolean =
        point in BOUNDS.expanded(OUT_OF_BOUNDS_TOLERANCE_DEGREES)

    /** Style URLs for the keyless OpenStreetMap tiles the map renders. */
    const val MAP_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
}
