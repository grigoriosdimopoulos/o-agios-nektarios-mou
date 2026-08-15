package gr.agiosnektarios.village.data.village

import android.content.Context
import gr.agiosnektarios.village.core.geo.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.geo.isPointInPolygon
import gr.agiosnektarios.village.core.model.BlockSummary
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.VillageBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The village's neighbourhood outlines, read once from
 * `assets/village_blocks.json`.
 *
 * Geometry is a shipped asset rather than Firestore data: it changes when the
 * map of the village changes, not when residents use the app, and keeping it
 * local means the map draws instantly and offline.
 */
@Singleton
class VillageBlockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    @Volatile
    private var cached: List<VillageBlock>? = null

    suspend fun blocks(): List<VillageBlock> {
        cached?.let { return it }
        return withContext(io) {
            val parsed = runCatching { parseAsset() }.getOrDefault(emptyList())
            cached = parsed
            parsed
        }
    }

    private fun parseAsset(): List<VillageBlock> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("blocks")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val polygonJson = obj.getJSONArray("polygon")
                val polygon = buildList {
                    for (j in 0 until polygonJson.length()) {
                        val point = polygonJson.getJSONObject(j)
                        add(GeoPoint(point.getDouble("lat"), point.getDouble("lng")))
                    }
                }
                add(
                    VillageBlock(
                        id = obj.getString("id"),
                        nameEl = obj.getString("nameEl"),
                        nameEn = obj.getString("nameEn"),
                        polygon = polygon,
                    ),
                )
            }
        }
    }

    /** The neighbourhood containing [point], or null if the pin fell outside all of them. */
    suspend fun blockAt(point: GeoPoint): VillageBlock? =
        blocks().firstOrNull { isPointInPolygon(point, it.polygon) }

    /**
     * Tallies reports per neighbourhood for the map badges.
     *
     * Counting is done from the issue's coordinates rather than its stored
     * `blockId` so that redrawing the neighbourhood outlines re-buckets old
     * reports correctly without a data migration.
     */
    suspend fun summarize(issues: List<Issue>): List<BlockSummary> {
        val all = blocks()
        if (all.isEmpty()) return emptyList()

        val open = mutableMapOf<String, Int>()
        val total = mutableMapOf<String, Int>()
        val categories = mutableMapOf<String, MutableMap<String, Int>>()

        for (issue in issues) {
            val block = all.firstOrNull { isPointInPolygon(GeoPoint(issue.lat, issue.lng), it.polygon) }
                ?: continue
            total[block.id] = (total[block.id] ?: 0) + 1
            if (issue.isOpen) {
                open[block.id] = (open[block.id] ?: 0) + 1
                val perCategory = categories.getOrPut(block.id) { mutableMapOf() }
                perCategory[issue.categoryId] = (perCategory[issue.categoryId] ?: 0) + 1
            }
        }

        return all.map { block ->
            BlockSummary(
                block = block,
                openCount = open[block.id] ?: 0,
                totalCount = total[block.id] ?: 0,
                dominantCategory = categories[block.id]
                    ?.maxByOrNull { it.value }
                    ?.key
                    ?.let { gr.agiosnektarios.village.core.model.IssueCategory.fromId(it) },
            )
        }
    }

    private companion object {
        const val ASSET_NAME = "village_blocks.json"
    }
}
