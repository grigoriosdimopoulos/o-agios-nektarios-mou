package gr.agiosnektarios.village.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.IssueRow
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.ui.components.isGreekLocale

/**
 * The village map: reports as pins, neighbourhoods as tinted polygons with
 * open-issue counters, and a "drop a pin here" flow for filing a new one.
 */
@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    onOpenIssue: (String) -> Unit,
    onCreateIssueAt: (Double, Double) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val greek = isGreekLocale()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(VillageConfig.CENTER, VillageConfig.DEFAULT_ZOOM)
    }

    // Clustering is a function of zoom, so the view model needs to know about
    // camera changes — but only about the zoom, not every pan.
    LaunchedEffect(cameraPositionState.position.zoom) {
        viewModel.onZoomChanged(cameraPositionState.position.zoom)
    }

    var showFilters by remember { mutableStateOf(false) }

    val mapProperties = remember(darkTheme) {
        MapProperties(
            latLngBoundsForCameraTarget = VillageConfig.BOUNDS,
            minZoomPreference = VillageConfig.MIN_ZOOM,
            maxZoomPreference = VillageConfig.MAX_ZOOM,
            mapStyleOptions = if (darkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            } else {
                null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                // The app's own FAB owns this corner.
                myLocationButtonEnabled = false,
            ),
            onMapClick = { point ->
                if (state.placingPin) viewModel.onMapTapped(point) else viewModel.dismissSheets()
            },
        ) {
            if (state.showBlocks) {
                state.blocks.forEach { summary ->
                    val tint = summary.dominantCategory?.tint
                        ?: MaterialTheme.colorScheme.primary
                    Polygon(
                        points = summary.block.polygon,
                        // Fill opacity scales with activity, so a busy
                        // neighbourhood is visible before you read any number.
                        fillColor = tint.copy(
                            alpha = if (summary.openCount > 0) 0.13f else 0.04f,
                        ),
                        strokeColor = tint.copy(alpha = 0.5f),
                        strokeWidth = 3f,
                        clickable = true,
                        onClick = { viewModel.selectBlock(summary.block.id) },
                    )
                    MarkerComposable(
                        keys = arrayOf(summary.block.id, summary.openCount),
                        state = rememberUpdatedMarkerState(position = summary.block.centroid),
                        onClick = {
                            viewModel.selectBlock(summary.block.id)
                            true
                        },
                    ) {
                        BlockBadge(
                            summary = summary,
                            label = summary.block.localizedName(greek),
                        )
                    }
                }
            }

            state.clusters.forEach { cluster ->
                MarkerComposable(
                    // The key set is what tells maps-compose to redraw the
                    // marker bitmap; without the size it would keep a stale one.
                    keys = arrayOf(cluster.id, cluster.size, cluster.openCount),
                    state = rememberUpdatedMarkerState(position = cluster.position),
                    onClick = {
                        if (cluster.isSingle) {
                            onOpenIssue(cluster.representative.id)
                        } else {
                            viewModel.selectCluster(cluster)
                        }
                        true
                    },
                ) {
                    if (cluster.isSingle) {
                        IssuePin(
                            category = cluster.representative.category,
                            open = cluster.representative.isOpen,
                        )
                    } else {
                        ClusterPin(cluster = cluster)
                    }
                }
            }

            state.pendingPin?.let { pin ->
                MarkerComposable(
                    keys = arrayOf("pending", pin.latitude, pin.longitude),
                    state = rememberUpdatedMarkerState(position = pin),
                ) {
                    IssuePin(category = IssueCategory.OTHER, open = true)
                }
            }
        }

        MapOverlay(
            state = state,
            onToggleFilters = { showFilters = true },
            onToggleBlocks = viewModel::toggleBlocksLayer,
            onStartPlacing = viewModel::startPlacingPin,
            onCancelPlacing = viewModel::cancelPlacingPin,
            onConfirmPlacement = { point -> onCreateIssueAt(point.latitude, point.longitude) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showFilters) {
        FilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onToggleCategory = viewModel::toggleCategory,
            onToggleStatus = viewModel::toggleStatus,
            onClear = viewModel::clearFilters,
        )
    }

    state.selectedCluster?.let { cluster ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheets, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.map_grouped_issues, cluster.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.issue_group_hint, cluster.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(cluster.issues, key = { it.id }) { issue ->
                        IssueRow(
                            issue = issue,
                            onClick = {
                                viewModel.dismissSheets()
                                onOpenIssue(issue.id)
                            },
                        )
                    }
                }
            }
        }
    }

    state.selectedBlock?.let { summary ->
        val sheetState = rememberModalBottomSheetState()
        LaunchedEffect(summary.block.id) {
            // Frame the neighbourhood while its sheet is open, so the numbers in
            // the sheet and the area on the map refer to the same thing.
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(summary.block.bounds, 96),
            )
        }
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheets, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    text = summary.block.localizedName(greek),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.block_issue_count, summary.openCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                val blockIssues = state.clusters
                    .flatMap { it.issues }
                    .filter { issue ->
                        gr.agiosnektarios.village.core.geo.isPointInPolygon(
                            LatLng(issue.lat, issue.lng),
                            summary.block.polygon,
                        )
                    }
                if (blockIssues.isEmpty()) {
                    EmptyState(emoji = "🌤️", title = stringResource(R.string.issues_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(blockIssues, key = { it.id }) { issue ->
                            IssueRow(
                                issue = issue,
                                onClick = {
                                    viewModel.dismissSheets()
                                    onOpenIssue(issue.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Floating controls layered over the map surface. */
@Composable
private fun MapOverlay(
    state: MapUiState,
    onToggleFilters: () -> Unit,
    onToggleBlocks: () -> Unit,
    onStartPlacing: () -> Unit,
    onCancelPlacing: () -> Unit,
    onConfirmPlacement: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalIconButton(onClick = onToggleFilters) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = stringResource(R.string.map_filter),
                    tint = if (state.filters.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            FilledTonalIconButton(onClick = onToggleBlocks) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = stringResource(R.string.map_blocks_layer),
                    tint = if (state.showBlocks) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Placement mode replaces the FAB with an instruction banner plus a
        // confirm action, so there is never an ambiguous "what does tapping the
        // map do right now" state.
        AnimatedVisibility(
            visible = state.placingPin,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.inverseSurface,
                        MaterialTheme.shapes.large,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.map_tap_to_place),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.clickableNoRipple(onCancelPlacing),
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                val pin = state.pendingPin
                when {
                    !state.placingPin -> onStartPlacing()
                    pin != null -> onConfirmPlacement(pin)
                    else -> Unit
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(
                text = if (state.placingPin && state.pendingPin != null) {
                    stringResource(R.string.action_continue)
                } else {
                    stringResource(R.string.map_add_issue)
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: MapUiState,
    onDismiss: () -> Unit,
    onToggleCategory: (IssueCategory) -> Unit,
    onToggleStatus: (IssueStatus) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.map_filters_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.map_filter_clear),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableNoRipple(onClear),
                )
            }

            Text(
                text = stringResource(R.string.map_filter_statuses),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IssueStatus.entries.toList()) { status ->
                    StatusChip(
                        status = status,
                        selected = status in state.filters.statuses,
                        onClick = { onToggleStatus(status) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.map_filter_categories),
                style = MaterialTheme.typography.titleSmall,
            )
            // A plain Column rather than a LazyColumn: the category list is
            // fixed and short, and nesting a vertical scroller inside the
            // sheet's own scroller is what makes bottom sheets feel broken.
            IssueCategory.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = category in state.filters.categories,
                            onClick = { onToggleCategory(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Tap target without the ripple, for text and icons layered over the map. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
