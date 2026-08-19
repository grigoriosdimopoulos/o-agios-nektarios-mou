package gr.agiosnektarios.village.data.village

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.geo.PlaceNames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The village's eighty-two ways, as geometry.
 *
 * The map already draws these through MapLibre, which parses the asset itself.
 * This is a second, much smaller reading of the same file for everything that
 * is not the map — chiefly working out which street a point is on, which the
 * map's copy cannot answer because it lives inside a rendering library.
 *
 * Loaded once and kept. The file is 82 line strings; holding them costs less
 * than the string that describes them.
 */
@Singleton
class RoadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    @Volatile
    private var cached: List<PlaceNames.Way>? = null

    suspend fun ways(): List<PlaceNames.Way> {
        cached?.let { return it }
        return withContext(io) {
            val loaded = runCatching { parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }
                .onFailure { Log.w(TAG, "Could not read $ASSET", it) }
                .getOrDefault(emptyList())
            cached = loaded
            loaded
        }
    }

    private fun parse(body: String): List<PlaceNames.Way> {
        val root = Json.parseToJsonElement(body).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            val obj = feature.jsonObject
            val wayId = obj["properties"]?.jsonObject?.get("wayId")?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val geometry = obj["geometry"]?.jsonObject ?: return@mapNotNull null
            if (geometry["type"]?.jsonPrimitive?.content != "LineString") return@mapNotNull null
            val points = geometry["coordinates"]?.jsonArray?.mapNotNull { pair ->
                val coords = pair.jsonArray
                // GeoJSON is longitude first, which is the opposite of every
                // other coordinate in this app and the classic way to end up
                // with a village in the Indian Ocean.
                val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                GeoPoint(lat, lng)
            }.orEmpty()
            if (points.isEmpty()) null else PlaceNames.Way(wayId, points)
        }
    }

    private companion object {
        const val ASSET = "village_roads.json"
        const val TAG = "Roads"
    }
}
