package gr.agiosnektarios.village.ui.map

import android.annotation.SuppressLint
import android.graphics.RectF
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
import gr.agiosnektarios.village.core.MapBasemap
import gr.agiosnektarios.village.core.MapStyles
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
import org.maplibre.android.style.layers.Property
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
private const val ROADS_ASSET = "village_roads.json"
private const val SOURCE_ROADS = "village-roads"
private const val LAYER_ROAD_CASING = "village-road-casing"
private const val LAYER_ROAD_LINE = "village-road-line"
private const val LAYER_ROAD_LABEL = "village-road-label"

/**
 * Half-width, in pixels, of the box a road tap is matched against.
 *
 * A street line is between one and nine pixels wide depending on zoom; a
 * fingertip covers about forty. Without slop, naming a street would mean
 * hitting a hairline exactly.
 */
private const val ROAD_TAP_SLOP = 22f

/**
 * The bundled road network, parsed once for the life of the process.
 *
 * Read from assets rather than fetched: it is the same fifty kilobytes every
 * time, it must be there before the first frame, and the village's streets
 * should not depend on having signal.
 *
 * Geometry only. The asset carries a `wayId` per road and, with one exception,
 * no names — see its header for why guessing them went wrong three times. Names
 * arrive separately, from the residents, and are merged in by [withNames].
 */
private val roadsGeoJson: (android.content.Context) -> FeatureCollection = run {
    var cached: FeatureCollection? = null
    { context ->
        cached ?: synchronized(ROADS_ASSET) {
            cached ?: runCatching {
                context.assets.open(ROADS_ASSET).bufferedReader().use { it.readText() }
                    .let(FeatureCollection::fromJson)
            }.getOrElse { FeatureCollection.fromFeatures(emptyList()) }.also { cached = it }
        }
    }
}

/**
 * The road network with the village's own street names written onto it.
 *
 * Features are rebuilt rather than mutated: MapLibre's `Feature` is shared with
 * the cached collection above, so setting a property on one would leave the
 * previous set of names on the next caller who asked for a clean copy.
 *
 * A name already in the asset — OpenStreetMap's own — wins over a proposal, so
 * a resident cannot rename the trunk road that the rest of the country knows
 * under a different name.
 */
private fun FeatureCollection.withNames(names: Map<String, String>): FeatureCollection {
    val source = features() ?: return this
    return FeatureCollection.fromFeatures(
        source.map { feature ->
            val wayId = feature.getStringProperty("wayId")
            val osmName = feature.getStringProperty("name")
            val resident = wayId?.let(names::get)
            val copy = Feature.fromGeometry(feature.geometry())
            feature.properties()?.entrySet()?.forEach { (key, value) ->
                copy.addProperty(key, value)
            }
            if (osmName.isNullOrBlank() && !resident.isNullOrBlank()) {
                copy.addStringProperty("name", resident)
            }
            copy
        },
    )
}

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
    basemap: MapBasemap,
    greekLabels: Boolean,
    streetNames: Map<String, String>,
    /** False while a pin is being placed, when every tap belongs to the pin. */
    allowRoadTaps: Boolean,
    onMapTap: (GeoPoint) -> Unit,
    onClusterTap: (String) -> Unit,
    onBlockTap: (String) -> Unit,
    onRoadTap: (String) -> Unit,
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
    val currentRoadTap by rememberUpdatedState(onRoadTap)
    val currentZoomChanged by rememberUpdatedState(onZoomChanged)
    // Read inside the click listener, which is registered once, so it has to
    // track the latest value rather than the one captured at registration.
    val roadTapsState by rememberUpdatedState(allowRoadTaps)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val state = remember { VillageMapState() }

    DisposableEffect(lifecycleOwner) {
        // Destroyed exactly once, by whichever comes first: leaving the
        // composition, or the activity going away. MapView.onDestroy() is not
        // idempotent, and calling it twice takes the process down.
        var destroyed = false
        fun destroyOnce() {
            if (!destroyed) {
                destroyed = true
                mapView.onDestroy()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyOnce()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyOnce()
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
                    // A road line is a few pixels wide, and a fingertip is not.
                    // Querying a box around the touch rather than the point
                    // itself is the difference between a tappable street and one
                    // that only responds if you hit it exactly.
                    val roadBox = RectF(
                        screen.x - ROAD_TAP_SLOP,
                        screen.y - ROAD_TAP_SLOP,
                        screen.x + ROAD_TAP_SLOP,
                        screen.y + ROAD_TAP_SLOP,
                    )
                    val road = if (roadTapsState) {
                        map.queryRenderedFeatures(roadBox, LAYER_ROAD_LINE)
                            .firstOrNull { it.getStringProperty("wayId") != null }
                    } else {
                        // While a report is being placed, every tap on the map
                        // is the pin going down. Naming a street has to wait.
                        null
                    }
                    val block = map.queryRenderedFeatures(screen, LAYER_BLOCK_FILL).firstOrNull()
                    when {
                        pin?.getStringProperty("clusterId") != null ->
                            currentClusterTap(pin.getStringProperty("clusterId"))
                        // Roads are checked before neighbourhoods because the
                        // shading covers the whole village: a road always sits
                        // inside a block, so block-first would swallow every
                        // road tap there is.
                        road != null -> currentRoadTap(road.getStringProperty("wayId"))
                        block?.getStringProperty("blockId") != null ->
                            currentBlockTap(block.getStringProperty("blockId"))
                        else -> currentMapTap(point.toGeoPoint())
                    }
                    true
                }

                state.applyStyle(mapView, darkTheme, basemap)
            }
            mapView
        },
        update = { view ->
            // Switching between the light and dark village means loading a
            // different tile style, which throws away every source and layer
            // with it — so the style is re-applied, not merely recorded, and
            // the render below runs against whatever it rebuilds.
            if (state.styleDark != darkTheme || state.styleBasemap != basemap) {
                state.applyStyle(view, darkTheme, basemap)
            }
            state.render(
                clusters = clusters,
                blocks = blocks,
                showBlocks = showBlocks,
                pendingPin = pendingPin,
                greekLabels = greekLabels,
                streetNames = streetNames,
            )
            // Guarded: without this, every recomposition restarts the fly-to
            // and the camera never settles while a neighbourhood is open.
            if (focusBounds != null && focusBounds != state.lastFocus) {
                state.lastFocus = focusBounds
                state.focus(focusBounds)
            } else if (focusBounds == null) {
                state.lastFocus = null
            }
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

    /** Which style is loaded, or null before the first one has been applied. */
    var styleDark: Boolean? = null
        private set

    var styleBasemap: MapBasemap? = null
        private set

    /** The bounds the camera was last sent to, so it is not sent again. */
    var lastFocus: GeoBounds? = null

    private var style: Style? = null
    private var pending: (() -> Unit)? = null
    private val registeredBadges = mutableSetOf<String>()

    /** Captured when the style loads; badge bitmaps are sized in device pixels. */
    private var metrics: android.util.DisplayMetrics? = null

    /** The unnamed road geometry, kept so names can be re-merged onto it. */
    private var roads: FeatureCollection? = null

    fun applyStyle(mapView: MapView, dark: Boolean, basemap: MapBasemap) {
        val map = map ?: return
        styleDark = dark
        styleBasemap = basemap
        // The outgoing style's sources, layers and images die with it, so
        // nothing may be added to it while the replacement loads. Dropping the
        // reference makes render() queue its work instead of writing into a
        // style that is on its way out.
        style = null
        invalidateRender()
        metrics = mapView.resources.displayMetrics

        val builder = when (basemap) {
            // The satellite and terrain styles are raster documents this app
            // writes, because neither provider offers a style URL to fetch.
            MapBasemap.SATELLITE -> Style.Builder().fromJson(MapStyles.SATELLITE)
            MapBasemap.TERRAIN -> Style.Builder().fromJson(MapStyles.TERRAIN)
            MapBasemap.STREETS -> Style.Builder().fromUri(MapStyles.streets(dark))
        }

        map.setStyle(builder) { loaded ->
            style = loaded
            registeredBadges.clear()

            MapPins.allPins(mapView.resources.displayMetrics).forEach { (id, bitmap) ->
                loaded.addImage(id, bitmap)
            }

            loaded.addSource(GeoJsonSource(SOURCE_BLOCKS))
            loaded.addSource(GeoJsonSource(SOURCE_PINS))

            // Neighbourhood shading goes *under* the street network rather than
            // over it. Painted on top it washed the roads out — which is the
            // one thing a village map has to show — so it is inserted beneath
            // the first road or label layer the basemap defines.
            val blockFill = FillLayer(LAYER_BLOCK_FILL, SOURCE_BLOCKS).withProperties(
                PropertyFactory.fillColor(Expression.get("fill")),
                PropertyFactory.fillOpacity(Expression.get("opacity")),
            )
            val anchor = loaded.firstRoadOrLabelLayerId()
            if (anchor != null) loaded.addLayerBelow(blockFill, anchor) else loaded.addLayer(blockFill)
            loaded.addLayer(
                LineLayer(LAYER_BLOCK_LINE, SOURCE_BLOCKS).withProperties(
                    PropertyFactory.lineColor(Expression.get("fill")),
                    PropertyFactory.lineWidth(2.4f),
                    PropertyFactory.lineOpacity(0.9f),
                ),
            )
            loaded.addLayer(
                SymbolLayer(LAYER_BLOCK_LABEL, SOURCE_BLOCKS).withProperties(
                    PropertyFactory.iconImage(Expression.get("badge")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(arrayOf(MapStyles.LABEL_FONT)),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                    PropertyFactory.textColor(if (dark) "#E4E6E4" else "#17211E"),
                    PropertyFactory.textHaloColor(if (dark) "#10151A" else "#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.4f),
                ),
            )
            // The village's own copy of the street network, drawn over whatever
            // basemap is showing. On satellite and terrain there are no road
            // lines at all underneath, and finding a pothole on aerial imagery
            // without them is guesswork.
            roads = roadsGeoJson(mapView.context)
            loaded.addSource(GeoJsonSource(SOURCE_ROADS, roads))
            loaded.addLayer(
                LineLayer(LAYER_ROAD_CASING, SOURCE_ROADS).withProperties(
                    // A dark casing under a light line is what makes a road
                    // legible over pale tarmac and dark vegetation alike.
                    PropertyFactory.lineColor(if (dark) "#05080B" else "#39414B"),
                    // Opaque on every basemap.
                    //
                    // This used to drop to 30% over the street basemap, on the
                    // reasoning that the basemap already draws these roads and
                    // the overlay should not shout over it. What it actually
                    // produced was a white line at 55% on a cream map: the
                    // village's road network, the one thing this overlay
                    // exists for, was invisible on the basemap the app opens
                    // with. It is the same geometry as the basemap's, so drawn
                    // solid it replaces that look rather than doubling it.
                    PropertyFactory.lineOpacity(1f),
                    PropertyFactory.lineWidth(roadWidth(outer = true)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                LineLayer(LAYER_ROAD_LINE, SOURCE_ROADS).withProperties(
                    PropertyFactory.lineColor(
                        Expression.match(
                            Expression.get("kind"),
                            Expression.literal(if (dark) "#8FA3B8" else "#FFFFFF"),
                            Expression.stop("main", if (dark) "#E8B54D" else "#F6C453"),
                            Expression.stop("track", if (dark) "#6E7A86" else "#D9C7A3"),
                        ),
                    ),
                    PropertyFactory.lineOpacity(1f),
                    PropertyFactory.lineWidth(roadWidth(outer = false)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    // Dashed only for tracks, so an unsurfaced path is
                    // recognisable as one before you read anything.
                    PropertyFactory.lineDasharray(
                        Expression.match(
                            Expression.get("kind"),
                            Expression.literal(arrayOf(1f, 0f)),
                            Expression.stop("track", arrayOf(2.2f, 1.6f)),
                        ),
                    ),
                ),
            )
            loaded.addLayer(
                SymbolLayer(LAYER_ROAD_LABEL, SOURCE_ROADS).withProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                    // Named explicitly: the default fontstack is not one the
                    // glyph endpoint serves, and a missing font renders nothing
                    // rather than falling back to something.
                    PropertyFactory.textFont(arrayOf(MapStyles.LABEL_FONT)),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor(if (dark) "#F2F4F2" else "#14201C"),
                    PropertyFactory.textHaloColor(if (dark) "#0B1014" else "#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.8f),
                    // Shown on every basemap, including the street one. The
                    // whole reason these names are bundled is that no basemap
                    // has them: OpenStreetMap names one way in this village, so
                    // hiding this layer over the street map hid every street
                    // name the app knows.
                    PropertyFactory.textOpacity(1.0f),
                    PropertyFactory.symbolSpacing(220f),
                ),
            )

            // Pins last so they sit above the neighbourhood shading.
            loaded.addLayer(
                SymbolLayer(LAYER_PINS, SOURCE_PINS).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.textField(Expression.get("count")),
                    PropertyFactory.textFont(arrayOf(MapStyles.LABEL_FONT)),
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

    /**
     * What was last uploaded to the map, so identical work is not repeated.
     *
     * `AndroidView`'s update block runs on *every* recomposition, and this one
     * rebuilds a Feature per report and per neighbourhood and hands the lot to
     * the renderer. Doing that on a recomposition caused by something unrelated
     * — a snackbar, a theme value, a badge — is a full geometry upload for no
     * change at all, which is exactly what a map stuttering under your finger
     * feels like.
     */
    private var lastRender: Int? = null

    fun render(
        clusters: List<IssueCluster>,
        blocks: List<BlockSummary>,
        showBlocks: Boolean,
        pendingPin: GeoPoint?,
        greekLabels: Boolean,
        streetNames: Map<String, String>,
    ) {
        // Cheap identity of the inputs. Clusters and summaries are rebuilt as
        // new instances each time the view model emits, so their contents —
        // not their references — decide whether anything actually moved.
        val signature = listOf(
            clusters.map { it.id to it.size },
            blocks.map { it.block.id to it.openCount },
            showBlocks, pendingPin, greekLabels, streetNames,
        ).hashCode()
        if (signature == lastRender && style != null) return
        lastRender = signature

        val work = {
            val loaded = style
            if (loaded != null) {
                renderBlocks(loaded, blocks, showBlocks, greekLabels)
                renderRoadNames(loaded, streetNames)
                renderPins(loaded, clusters, pendingPin)
            }
        }
        if (style != null) work() else pending = work
    }

    /**
     * Re-uploads the road network with the current set of resident-supplied
     * names.
     *
     * Rebuilding 82 line features is not free, which is why it runs behind the
     * same render signature as everything else rather than on every frame: it
     * happens when a name changes, and not otherwise.
     */
    private fun renderRoadNames(style: Style, names: Map<String, String>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ROADS) ?: return
        val roads = roads ?: return
        source.setGeoJson(if (names.isEmpty()) roads else roads.withNames(names))
    }

    /** Reloading a style discards its sources, so the last upload is void too. */
    fun invalidateRender() {
        lastRender = null
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
                // visible before you read any number — but a quiet one still has
                // to be visible at all. At 4% it was not, which made turning the
                // layer on and off look like a dead button in a village that has
                // not been reported in yet.
                addNumberProperty("opacity", if (summary.openCount > 0) 0.22 else 0.10)
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

    /**
     * Road width that grows with zoom, so the network reads at village scale
     * without becoming a smear when you zoom out to the whole settlement.
     */
    private fun roadWidth(outer: Boolean): Expression {
        val pad = if (outer) 2.6f else 0f
        // Width by class as well as zoom. A village where the lane you live on
        // and the goat track up the hill are drawn identically tells you
        // nothing; the hierarchy is most of the information.
        fun byClass(main: Float, street: Float, track: Float): Expression =
            Expression.match(
                Expression.get("kind"),
                Expression.literal(street + pad),
                Expression.stop("main", main + pad),
                Expression.stop("track", track + pad),
            )
        return Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            Expression.stop(14f, byClass(2.0f, 1.1f, 0.7f)),
            Expression.stop(17f, byClass(6.0f, 3.6f, 1.8f)),
            Expression.stop(20f, byClass(15f, 9f, 4f)),
        )
    }

    /**
     * The layer to slide the neighbourhood shading underneath.
     *
     * Style layer ids are the basemap author's, not ours, so this matches on
     * convention rather than a known name: roads first, then any label layer,
     * and null on a raster basemap that has neither — where shading on top is
     * the only option and there are no road lines to hide anyway.
     */
    private fun Style.firstRoadOrLabelLayerId(): String? {
        val ids = layers.map { it.id }
        return ids.firstOrNull { it.startsWith("road") || it.contains("highway") }
            ?: ids.firstOrNull { it.contains("label") }
    }
}
