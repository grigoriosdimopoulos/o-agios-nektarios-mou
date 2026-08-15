package gr.agiosnektarios.village.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.geo.IssueCluster
import gr.agiosnektarios.village.core.model.BlockSummary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SOURCE_BLOCKS = "village-blocks"
private const val SOURCE_PINS = "village-pins"
private const val LAYER_BLOCK_FILL = "village-block-fill"
private const val LAYER_BLOCK_LINE = "village-block-line"
private const val LAYER_BLOCK_LABEL = "village-block-label"
private const val LAYER_PINS = "village-pin-symbols"

internal fun GeoPoint.toLatLng() = LatLng(lat, lng)
internal fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)
internal fun GeoBounds.toLatLngBounds(): LatLngBounds =
    LatLngBounds.from(north, east, south, west)

/**
 * The village map, on MapLibre with keyless OpenStreetMap tiles.
 *
 * Everything is drawn as GeoJSON sources feeding style layers rather than as
 * individual marker objects: one source holds every pin, so a hundred reports
 * cost one upload and the map handles collision and culling itself. The trade
 * against the old per-marker composables is that pins are bitmaps — see
 * [MapPins] — and taps are resolved by querying rendered features.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun VillageMap(
    clusters: List<IssueCluster>,
    blocks: List<BlockSummary>,
    showBlocks: Boolean,
    pendingPin: GeoPoint?,
    darkTheme: Boolean,
    greekLabels: Boolean,
    onMapTap: (GeoPoint) -> Unit,
    onClusterTap: (String) -> Unit,
    onBlockTap: (String) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    focusBounds: GeoBounds? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Callbacks are captured once by the map listeners, so keep them fresh
    // without tearing the map down on every recomposition.
    val currentMapTap by rememberUpdatedState(onMapTap)
    val currentClusterTap by rememberUpdatedState(onClusterTap)
    val currentBlockTap by rememberUpdatedState(onBlockTap)
    val currentZoomChanged by rememberUpdatedState(onZoomChanged)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val state = remember { VillageMapState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                state.map = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(VillageConfig.CENTER.toLatLng())
                    .zoom(VillageConfig.DEFAULT_ZOOM.toDouble())
                    .build()
                map.setMinZoomPreference(VillageConfig.MIN_ZOOM.toDouble())
                map.setMaxZoomPreference(VillageConfig.MAX_ZOOM.toDouble())
                // Fence the camera to the village so the map cannot be panned
                // to the other side of the country by accident.
                map.setLatLngBoundsForCameraTarget(VillageConfig.BOUNDS.toLatLngBounds())
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false

                map.addOnCameraIdleListener {
                    currentZoomChanged(map.cameraPosition.zoom.toFloat())
                }

                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val pin = map.queryRenderedFeatures(screen, LAYER_PINS).firstOrNull()
                    val block = map.queryRenderedFeatures(screen, LAYER_BLOCK_FILL).firstOrNull()
                    when {
                        pin?.getStringProperty("clusterId") != null ->
                            currentClusterTap(pin.getStringProperty("clusterId"))
                        block?.getStringProperty("blockId") != null ->
                            currentBlockTap(block.getStringProperty("blockId"))
                        else -> currentMapTap(point.toGeoPoint())
                    }
                    true
                }

                state.applyStyle(mapView, darkTheme)
            }
            mapView
        },
        update = {
            state.styleDark = darkTheme
            state.render(
                clusters = clusters,
                blocks = blocks,
                showBlocks = showBlocks,
                pendingPin = pendingPin,
                greekLabels = greekLabels,
            )
            focusBounds?.let { state.focus(it) }
        },
    )
}

/**
 * Mutable map plumbing, kept out of composition.
 *
 * MapLibre's style loads asynchronously and cannot be touched before it does,
 * so renders that arrive early are stashed and replayed once the style is
 * ready — otherwise the first frame of data is silently dropped.
 */
private class VillageMapState {
    var map: MapLibreMap? = null
    var styleDark: Boolean = false
    private var style: Style? = null
    private var pending: (() -> Unit)? = null
    private val registeredBadges = mutableSetOf<String>()

    /** Captured when the style loads; badge bitmaps are sized in device pixels. */
    private var metrics: android.util.DisplayMetrics? = null

    fun applyStyle(mapView: MapView, dark: Boolean) {
        val map = map ?: return
        val url = if (dark) VillageConfig.MAP_STYLE_DARK else VillageConfig.MAP_STYLE_LIGHT
        metrics = mapView.resources.displayMetrics
        map.setStyle(Style.Builder().fromUri(url)) { loaded ->
            style = loaded
            registeredBadges.clear()

            MapPins.allPins(mapView.resources.displayMetrics).forEach { (id, bitmap) ->
                loaded.addImage(id, bitmap)
            }

            loaded.addSource(GeoJsonSource(SOURCE_BLOCKS))
            loaded.addSource(GeoJsonSource(SOURCE_PINS))

            loaded.addLayer(
                FillLayer(LAYER_BLOCK_FILL, SOURCE_BLOCKS).withProperties(
                    PropertyFactory.fillColor(Expression.get("fill")),
                    PropertyFactory.fillOpacity(Expression.get("opacity")),
                ),
            )
            loaded.addLayer(
                LineLayer(LAYER_BLOCK_LINE, SOURCE_BLOCKS).withProperties(
                    PropertyFactory.lineColor(Expression.get("fill")),
                    PropertyFactory.lineWidth(1.6f),
                    PropertyFactory.lineOpacity(0.7f),
                ),
            )
            loaded.addLayer(
                SymbolLayer(LAYER_BLOCK_LABEL, SOURCE_BLOCKS).withProperties(
                    PropertyFactory.iconImage(Expression.get("badge")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                    PropertyFactory.textColor(if (dark) "#E4E6E4" else "#17211E"),
                    PropertyFactory.textHaloColor(if (dark) "#10151A" else "#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.4f),
                ),
            )
            // Pins last so they sit above the neighbourhood shading.
            loaded.addLayer(
                SymbolLayer(LAYER_PINS, SOURCE_PINS).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.textField(Expression.get("count")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textOffset(arrayOf(0.9f, -0.9f)),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textHaloColor("#00000066"),
                    PropertyFactory.textHaloWidth(1.2f),
                    PropertyFactory.textAllowOverlap(true),
                ),
            )

            pending?.invoke()
            pending = null
        }
    }

    fun render(
        clusters: List<IssueCluster>,
        blocks: List<BlockSummary>,
        showBlocks: Boolean,
        pendingPin: GeoPoint?,
        greekLabels: Boolean,
    ) {
        val work = {
            val loaded = style
            if (loaded != null) {
                renderBlocks(loaded, blocks, showBlocks, greekLabels)
                renderPins(loaded, clusters, pendingPin)
            }
        }
        if (style != null) work() else pending = work
    }

    private fun renderBlocks(
        style: Style,
        blocks: List<BlockSummary>,
        show: Boolean,
        greekLabels: Boolean,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_BLOCKS) ?: return
        if (!show) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val features = blocks.map { summary ->
            val tint = (summary.dominantCategory?.tint?.toArgb()) ?: 0xFF1F6F5C.toInt()
            val badgeId = MapPins.blockBadgeId(summary.block.id, summary.openCount)
            val density = metrics
            if (density != null && registeredBadges.add(badgeId)) {
                style.addImage(
                    badgeId,
                    MapPins.blockBadge(
                        density,
                        summary.openCount,
                        if (summary.openCount > 0) tint else 0xFF77808B.toInt(),
                    ),
                )
            }
            val ring = listOf(
                summary.block.polygon.map { Point.fromLngLat(it.lng, it.lat) } +
                    listOfNotNull(
                        summary.block.polygon.firstOrNull()
                            ?.let { Point.fromLngLat(it.lng, it.lat) },
                    ),
            )
            Feature.fromGeometry(Polygon.fromLngLats(ring)).apply {
                addStringProperty("blockId", summary.block.id)
                addStringProperty("name", summary.block.localizedName(greekLabels))
                addStringProperty("badge", badgeId)
                addStringProperty("fill", colorToHex(tint))
                // Fill opacity scales with activity, so a busy neighbourhood is
                // visible before you read any number.
                addNumberProperty("opacity", if (summary.openCount > 0) 0.13 else 0.04)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun renderPins(style: Style, clusters: List<IssueCluster>, pendingPin: GeoPoint?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_PINS) ?: return
        val features = buildList {
            clusters.forEach { cluster ->
                val representative = cluster.representative
                val category = cluster.category ?: representative.category
                add(
                    Feature.fromGeometry(
                        Point.fromLngLat(cluster.position.lng, cluster.position.lat),
                    ).apply {
                        addStringProperty("clusterId", cluster.id)
                        addStringProperty(
                            "icon",
                            MapPins.iconId(category, open = cluster.openCount > 0),
                        )
                        addStringProperty(
                            "count",
                            if (cluster.size > 1) cluster.size.toString() else "",
                        )
                    },
                )
            }
            pendingPin?.let {
                add(
                    Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).apply {
                        addStringProperty("icon", MapPins.PENDING_ICON)
                        addStringProperty("count", "")
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun focus(bounds: GeoBounds) {
        val map = map ?: return
        runCatching {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.toLatLngBounds(), 96),
            )
        }
    }

    private fun colorToHex(argb: Int): String =
        String.format("#%06X", 0xFFFFFF and argb)
}
