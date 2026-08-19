package gr.agiosnektarios.village.data.village

import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.geo.PlaceNames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Turns a coordinate into "Οδός Ελατιάς, Κέντρο".
 *
 * The three sources it needs are already in the app and were never brought
 * together: the road geometry that ships as an asset, the street names the
 * residents supply through the map, and the neighbourhood polygons. Doing it
 * here rather than in each view model means a report, an alert and a house all
 * describe the same spot the same way.
 */
@Singleton
class PlaceNamer @Inject constructor(
    private val roads: RoadRepository,
    private val streetNames: StreetNameRepository,
    private val blocks: VillageBlockRepository,
) {
    suspend fun describe(point: GeoPoint): PlaceNames.Place = PlaceNames.describe(
        point = point,
        ways = roads.ways(),
        // A snapshot rather than a subscription: naming a place is a one-off
        // question asked at the moment something is filed, and the answer is
        // stored with it. A street renamed afterwards does not silently move
        // last year's reports.
        namesByWay = streetNames.observeNamesByWay().first(),
        blocks = blocks.blocks(),
    )
}
