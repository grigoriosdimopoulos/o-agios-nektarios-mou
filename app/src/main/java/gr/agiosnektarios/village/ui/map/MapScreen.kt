package gr.agiosnektarios.village.ui.map

import gr.agiosnektarios.village.ui.theme.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.ContactKind
import gr.agiosnektarios.village.core.model.NationalContacts
import gr.agiosnektarios.village.core.model.StreetName
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.GlassSurface
import gr.agiosnektarios.village.ui.components.IssueRow
import gr.agiosnektarios.village.ui.issue.QuickReportSheet
import gr.agiosnektarios.village.ui.alert.AlertViewModel
import gr.agiosnektarios.village.ui.alert.EmergencyBanner
import gr.agiosnektarios.village.ui.alert.OutageCard
import gr.agiosnektarios.village.ui.alert.UrgentButton
import gr.agiosnektarios.village.ui.issue.QuickReportViewModel
import gr.agiosnektarios.village.ui.weather.DateLine
import gr.agiosnektarios.village.ui.weather.WeatherChip
import gr.agiosnektarios.village.ui.weather.WeatherOverlay
import gr.agiosnektarios.village.ui.weather.WeatherSheet
import gr.agiosnektarios.village.ui.weather.WeatherViewModel
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.SecondaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.components.StatusChip
import gr.agiosnektarios.village.core.MapBasemap
import gr.agiosnektarios.village.core.weather.FireRisk
import gr.agiosnektarios.village.ui.components.isGreekLocale
import gr.agiosnektarios.village.ui.components.pressable
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.theme.LocalIsDarkTheme
import gr.agiosnektarios.village.data.media.CaptureFile
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import gr.agiosnektarios.village.ui.theme.primaryInk
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * The village map: reports as pins, neighbourhoods as tinted polygons with
 * open-issue counters, and a "drop a pin here" flow for filing a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenIssue: (String) -> Unit,
    onCreateIssueAt: (Double, Double) -> Unit,
    onOpenContacts: () -> Unit,
    /** Null opens on the question; a kind name opens on that kind's screen. */
    onRaiseAlert: (String?) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    quickReport: QuickReportViewModel = hiltViewModel(),
    weatherViewModel: WeatherViewModel = hiltViewModel(),
    alertViewModel: AlertViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = LocalIsDarkTheme.current
    val greek = isGreekLocale()
    var showFilters by remember { mutableStateOf(false) }
    val quick by quickReport.uiState.collectAsStateWithLifecycle()
    var showQuickReport by remember { mutableStateOf(false) }
    // True while the resident is choosing a point *for a quick report already
    // in progress*, as opposed to starting a full one from scratch.
    var awaitingPin by remember { mutableStateOf(false) }
    // Counts reports, not sheet openings.
    //
    // The camera is opened once per report. Everything inside the sheet's
    // `if` is disposed when the sheet hides, so an effect keyed on the sheet's
    // visibility re-ran on the way back from picking a point on the map and
    // threw the resident straight into the camera again. This only changes
    // when a genuinely new report starts.
    var reportSession by remember { mutableIntStateOf(0) }
    // The report the drawer is centred on, lit on the map beneath it.
    var focusedIssueId by remember { mutableStateOf<String?>(null) }

    val weather by weatherViewModel.uiState.collectAsStateWithLifecycle()
    // Asked for again every time the map comes back to the front.
    //
    // The view model fetched once in `init` and nothing ever asked again, so a
    // phone that stayed open showed the reading it had picked up at launch for
    // as long as the process lived — which made the repository's half-hour
    // staleness rule inert and its comment about being "safe to call on every
    // resume" true of nothing, because nothing called it. The repository still
    // decides whether the radio is worth waking.
    LifecycleResumeEffect(Unit) {
        weatherViewModel.refresh()
        viewModel.locate()
        onPauseOrDispose { }
    }
    var showWeather by remember { mutableStateOf(false) }
    // Measured by the drawer rather than assumed, so the button and the street
    // hint clear it at every text size.
    var peekHeight by remember { mutableStateOf(MapSheetDefaults.peekHeight) }
    val alerts by alertViewModel.active.collectAsStateWithLifecycle()

    // The camera is only pushed at the map when a neighbourhood is opened;
    // otherwise the map owns its own position and nothing here fights it.
    val focusBounds = state.selectedBlock?.block?.bounds

    Box(modifier = Modifier.fillMaxSize()) {
        VillageMap(
            modifier = Modifier.fillMaxSize(),
            clusters = state.clusters,
            blocks = state.blocks,
            showBlocks = state.showBlocks,
            pendingPin = state.pendingPin,
            darkTheme = darkTheme,
            basemap = state.basemap,
            greekLabels = greek,
            streetNames = state.streetNames,
            focusedIssueId = focusedIssueId.takeUnless { state.placingPin },
            myPosition = state.myPosition,
            homePosition = state.homePosition,
            allowRoadTaps = !state.placingPin,
            focusBounds = focusBounds,
            onZoomChanged = viewModel::onZoomChanged,
            onMapTap = { point ->
                if (state.placingPin) viewModel.onMapTapped(point) else viewModel.dismissSheets()
            },
            onClusterTap = { clusterId ->
                val cluster = state.clusters.firstOrNull { it.id == clusterId }
                when {
                    cluster == null -> Unit
                    cluster.isSingle -> onOpenIssue(cluster.representative.id)
                    else -> viewModel.selectCluster(cluster)
                }
            },
            onBlockTap = viewModel::selectBlock,
            onRoadTap = viewModel::selectStreet,
        )

        // The weather, drawn over the map and under everything else.
        //
        // Off while a pin is being placed: at that moment the map is an
        // instrument being aimed, and rain across it is in the way. Off, too,
        // when the reading is not recent — animated rain is a statement about
        // this minute, and drawing a cached one is a lie told convincingly.
        val sky = weather.snapshot
        if (weather.animateOnMap && weather.fresh && sky != null && !state.placingPin) {
            WeatherOverlay(snapshot = sky, dark = darkTheme, modifier = Modifier.fillMaxSize())
        }

        // The reports, on a pane pulled up over the map. Hidden while a pin is
        // being placed, when the map itself is the thing being used.
        if (!state.placingPin) {
            MapSheet(
                issues = state.clusters.flatMap { it.issues }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt?.time ?: 0L },
                onOpenIssue = onOpenIssue,
                onFocusedIssue = { focusedIssueId = it },
                onPeekHeight = { peekHeight = it },
                leading = {
                    val outages = alerts.alerts.filter {
                        it.alertKind.severity == gr.agiosnektarios.village.core.model
                            .AlertSeverity.OUTAGE
                    }
                    if (outages.isNotEmpty()) {
                        item(key = "alert-heading") {
                            Text(
                                text = stringResource(R.string.alert_active),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(outages, key = { "alert-${it.id}" }) { alert ->
                        OutageCard(
                            alert = alert,
                            userId = alerts.userId,
                            canResolve = alerts.canModerate ||
                                alert.raisedById == alerts.userId,
                            onConfirm = { alertViewModel.toggleConfirmed(alert) },
                            onResolve = { alertViewModel.resolve(alert) },
                        )
                    }
                },
                subtitle = {
                    weather.snapshot?.let { DateLine(it.observedAt) }
                },
                trailing = {
                    WeatherChip(state = weather, onClick = { showWeather = true })
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // What is wrong right now, and the way to say so — both above the map
        // and both on the screen the app opens on, because neither is any use
        // three taps deep.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(0.72f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UrgentButton(onClick = { onRaiseAlert(null) })
            EmergencyBanner(alerts = alerts.alerts, onOpen = { onRaiseAlert(it.kind) })
        }

        MapOverlay(
            state = state,
            onToggleFilters = { showFilters = true },
            onToggleBlocks = viewModel::toggleBlocksLayer,
            onSelectBasemap = viewModel::setBasemap,
            onCancelPlacing = {
                viewModel.cancelPlacingPin()
                // Backing out of the map returns the resident to the report
                // they were writing. Without this the flag stayed set and the
                // photo was thrown away by the next tap of the button, which
                // is the loss this whole path exists to prevent.
                if (awaitingPin) {
                    awaitingPin = false
                    showQuickReport = true
                }
            },
            onConfirmPlacement = { point ->
                if (awaitingPin) {
                    awaitingPin = false
                    quickReport.setPosition(point)
                    viewModel.cancelPlacingPin()
                    showQuickReport = true
                } else {
                    onCreateIssueAt(point.lat, point.lng)
                }
            },
            onDismissStreetHint = viewModel::dismissStreetHint,
            onStartQuickReport = {
                quickReport.start()
                reportSession++
                showQuickReport = true
            },
            peekHeight = peekHeight,
            weatherOnMap = weather.animateOnMap,
            onToggleWeather = { weatherViewModel.setAnimateOnMap(!weather.animateOnMap) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Camera, permission and the effect that fires them live ABOVE the sheet's
    // `if`, not inside it.
    //
    // Keying an effect only suppresses re-runs while the effect stays in the
    // composition. Everything inside `if (showQuickReport)` is disposed the
    // moment the sheet hides, so a LaunchedEffect declared in there was a
    // brand-new node on the way back from the map and ran again whatever its
    // key said — reopening the camera, and re-asking for location permission,
    // every single time the resident went to place a pin. Hoisted, it is the
    // same node for the life of the screen, and the key does what keys do.
    val context = LocalContext.current
    var captureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var launchedForSession by remember { mutableIntStateOf(0) }

    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) captureUri?.let(quickReport::onPhotoTaken) }

    fun openCamera() {
        val uri = CaptureFile.create(context)
        captureUri = uri
        camera.launch(uri)
    }

    // Location is asked for at the moment it is used, not at launch: a
    // permission prompt on first open, before the app has explained anything,
    // is the fastest way to have it denied for good.
    val locationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { quickReport.locate() }

    LaunchedEffect(reportSession) {
        if (reportSession > launchedForSession) {
            launchedForSession = reportSession
            // Location is asked for; the camera is NOT opened.
            //
            // It used to launch straight into the camera app, on the theory
            // that the photo is the fastest way in. In practice the resident
            // taps "new report" and is thrown into a viewfinder with no
            // explanation — they have not been told what the app wants, and
            // there is no way back that is not a cancel. The sheet opens
            // first, with the photo frame as an obvious thing to tap.
            locationPermission.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    if (showWeather) {
        val uriHandler = LocalUriHandler.current
        WeatherSheet(
            state = weather,
            onDismiss = { showWeather = false },
            // 199, from the constant the contacts screen renders, so there is
            // one place the number lives rather than two that can disagree.
            onCallFireService = {
                val fire = NationalContacts.all.first { it.kind == ContactKind.EMERGENCY &&
                    it.nameRes == R.string.contact_fire }
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:${fire.number}"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onOpenContacts = {
                showWeather = false
                onOpenContacts()
            },
            onOpenOfficialMap = {
                runCatching { uriHandler.openUri(FireRisk.OFFICIAL_MAP_URL) }
            },
            onToggleMapWeather = weatherViewModel::setAnimateOnMap,
            onRefresh = { weatherViewModel.refresh(force = true) },
        )
    }

    if (showQuickReport) {
        // Filed: close the sheet and open what was just written, so the
        // resident sees their report exist rather than being returned to a map
        // and left to wonder.
        LaunchedEffect(quick.savedIssueId) {
            quick.savedIssueId?.let { id ->
                showQuickReport = false
                onOpenIssue(id)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showQuickReport = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            QuickReportSheet(
                state = quick,
                onTextChange = quickReport::onTextChange,
                onRetakePhoto = { openCamera() },
                onRetryLocation = quickReport::locate,
                onPickOnMap = {
                    // Hide the sheet, do not discard it. The photo lives in
                    // the view model, and the resident gets it back with the
                    // point they chose — before this, the one escape from a
                    // failed GPS fix silently threw their picture away.
                    showQuickReport = false
                    awaitingPin = true
                    viewModel.startPlacingPin()
                },
                onSubmit = quickReport::submit,
                onCategoryChange = quickReport::onCategoryChange,
            )
        }
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
            Column(modifier = Modifier.padding(horizontal = Space.page).padding(bottom = 24.dp)) {
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
        // Framing the neighbourhood is driven by focusBounds above, so the
        // numbers in this sheet and the area on the map always agree.
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheets, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = Space.page).padding(bottom = 24.dp)) {
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
                            GeoPoint(issue.lat, issue.lng),
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

    state.selectedStreet?.let { street ->
        StreetNameSheet(
            street = street,
            onDismiss = viewModel::dismissStreetSheet,
            onSubmit = { name -> viewModel.nameStreet(street.wayId, name) },
            onToggleConfirm = { confirmed ->
                viewModel.toggleStreetConfirmation(street.wayId, confirmed)
            },
        )
    }
}

/**
 * Where the village's street names come from.
 *
 * No public dataset names the streets of this settlement — OpenStreetMap has
 * one named way in it, and matching the state valuation map's list to geometry
 * by position produced wrong answers three times running. The residents know,
 * so this asks them, and shows how many neighbours have agreed so a name that
 * one person guessed is distinguishable from one the street actually goes by.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreetNameSheet(
    street: SelectedStreet,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onToggleConfirm: (Boolean) -> Unit,
) {
    val existing = street.existing
    var draft by remember(street.wayId, existing?.name) {
        mutableStateOf(existing?.name.orEmpty())
    }
    // Renaming is a deliberate act, not something you fall into by tapping a
    // field: an established name shows as text until someone asks to change it.
    var editing by remember(street.wayId) { mutableStateOf(existing == null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.street_name_unknown else R.string.street_name_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.street_name_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (editing) {
                VillageTextField(
                    value = draft,
                    onValueChange = { draft = it.take(StreetName.MAX_LENGTH) },
                    label = stringResource(R.string.street_name_field),
                    placeholder = stringResource(R.string.street_name_placeholder),
                    imeAction = ImeAction.Done,
                )
                PrimaryButton(
                    text = stringResource(R.string.street_name_save),
                    onClick = { onSubmit(draft) },
                    enabled = draft.isNotBlank() && draft != existing?.name,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing != null) {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { draft = existing.name; editing = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (existing != null) {
                Text(text = existing.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = pluralStringResource(
                        R.plurals.street_name_confirmations,
                        existing.confirmations,
                        existing.confirmations,
                        existing.proposedByName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = stringResource(
                        if (street.confirmedByMe) {
                            R.string.street_name_confirmed
                        } else {
                            R.string.street_name_confirm
                        },
                    ),
                    onClick = { onToggleConfirm(!street.confirmedByMe) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Anyone may propose a correction; the rules decide whether the
                // write lands, and the button says so rather than pretending.
                SecondaryButton(
                    text = stringResource(R.string.street_name_change),
                    onClick = { editing = true },
                    enabled = street.canEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Floating controls layered over the map surface. */
@Composable
private fun MapOverlay(
    state: MapUiState,
    peekHeight: Dp,
    weatherOnMap: Boolean,
    onToggleWeather: () -> Unit,
    onToggleFilters: () -> Unit,
    onToggleBlocks: () -> Unit,
    onSelectBasemap: (MapBasemap) -> Unit,
    onCancelPlacing: () -> Unit,
    onConfirmPlacement: (GeoPoint) -> Unit,
    onDismissStreetHint: () -> Unit,
    onStartQuickReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // One glass column with hairline dividers rather than three floating
        // pucks. Grouping controls that belong together into a single piece of
        // material — and letting the map show through it — is the difference
        // between "buttons placed on a map" and "a map with controls".
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .width(48.dp),
            shape = RoundedCornerShape(16.dp),
            alpha = 0.92f,
            // No top hairline: that separator exists to divide chrome from the
            // content it is flush against. On a rounded thing floating over a
            // map it reads as a stray line.
            edge = false,
        ) {
            Column {
                MapControl(
                    icon = Icons.Filled.FilterList,
                    contentDescription = stringResource(R.string.map_filter),
                    active = state.filters.isActive,
                    onClick = onToggleFilters,
                )
                ControlDivider()
                BasemapButton(current = state.basemap, onSelect = onSelectBasemap)
                ControlDivider()
                MapControl(
                    icon = Icons.Filled.Layers,
                    contentDescription = stringResource(R.string.map_blocks_layer),
                    active = state.showBlocks,
                    onClick = onToggleBlocks,
                )
                ControlDivider()
                // The weather layer, beside the other layer switches.
                //
                // It used to live only at the bottom of the weather sheet,
                // behind the chip in the drawer — three deliberate steps from
                // the map, which is three too many for something whose whole
                // point is to be looked at. This is where a resident already
                // goes to turn the neighbourhoods on and off.
                MapControl(
                    icon = Icons.Filled.Air,
                    contentDescription = stringResource(R.string.weather_animate),
                    active = weatherOnMap,
                    onClick = onToggleWeather,
                )
            }
        }

        // The village's streets carry no names in any public dataset, so the
        // map ships with none and the only way to get one is to tap a road.
        // A feature nobody can find is the same as a feature that is not there.
        AnimatedVisibility(
            visible = state.showStreetHint,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            // Above the drawer, not underneath it.
            //
            // This used to clear only the navigation bar, which put it inside
            // the sheet's peek strip and directly on top of the first report —
            // the first thing a new resident sees is a tip covering the
            // content it is a tip about. It also sits above the FAB's column
            // rather than beside it, so the two cannot collide at any text
            // size.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Space.page, end = Space.page)
                .padding(
                    bottom = BottomBarDefaults.contentPadding() + peekHeight + 88.dp,
                ),
        ) {
            StreetNamingHint(onDismiss = onDismissStreetHint)
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
                    .padding(horizontal = Space.page, vertical = 12.dp),
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
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickableNoRipple(onCancelPlacing),
                )
            }
        }

        ExtendedFloatingActionButton(
            // The common case is someone standing at the problem, so this
            // opens the camera rather than an empty form. Placing a pin by
            // hand is still there, one tap inside the sheet, for reporting
            // something you are not standing next to.
            onClick = {
                val pin = state.pendingPin
                when {
                    state.placingPin && pin != null -> onConfirmPlacement(pin)
                    state.placingPin -> Unit
                    else -> onStartQuickReport()
                }
            },
            // Clears the navigation bar *and* the sheet's peek strip. The
            // bar is drawn over the map rather than beside it, and the sheet
            // now rests 96dp above that — with only the bar accounted for the
            // FAB sat squarely on top of the sheet's own count row.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(
                    bottom = BottomBarDefaults.contentPadding() +
                        if (state.placingPin) 0.dp else peekHeight,
                ),
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
                .padding(horizontal = Space.page)
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
                    color = MaterialTheme.colorScheme.primaryInk,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickableNoRipple(onClear)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
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

/**
 * Switches what the map draws underneath: streets, aerial imagery, or terrain.
 *
 * A menu rather than a cycle button, because three states behind one icon means
 * tapping twice to see what the third one is.
 */
@Composable
private fun BasemapButton(current: MapBasemap, onSelect: (MapBasemap) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        // Same shape as its neighbours in the cluster: the basemap switcher is
        // one control among three, not a puck that happens to sit near them.
        MapControl(
            icon = when (current) {
                MapBasemap.STREETS -> Icons.Filled.Map
                MapBasemap.SATELLITE -> Icons.Filled.Satellite
                MapBasemap.TERRAIN -> Icons.Filled.Terrain
            },
            contentDescription = stringResource(R.string.map_basemap),
            active = false,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MapBasemap.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (option) {
                                    MapBasemap.STREETS -> R.string.map_basemap_streets
                                    MapBasemap.SATELLITE -> R.string.map_basemap_satellite
                                    MapBasemap.TERRAIN -> R.string.map_basemap_terrain
                                },
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (option) {
                                MapBasemap.STREETS -> Icons.Filled.Map
                                MapBasemap.SATELLITE -> Icons.Filled.Satellite
                                MapBasemap.TERRAIN -> Icons.Filled.Terrain
                            },
                            contentDescription = null,
                            tint = if (option == current) {
                                MaterialTheme.colorScheme.primaryInk
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The one affordance that makes street naming discoverable.
 *
 * Extracted and internal so it can be rendered on its own — chrome that only
 * exists on top of a live MapView is chrome nobody ever looks at closely.
 */
@Composable
internal fun StreetNamingHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        alpha = 0.94f,
        edge = false,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.street_hint),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_dismiss),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickableNoRipple(onDismiss)
                    .padding(6.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One control in the map's glass cluster. */
@Composable
internal fun MapControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickableNoRipple(onClick)
            .pressable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) {
                MaterialTheme.colorScheme.primaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

/** The hairline between grouped controls, as an inset rule rather than a full one. */
@Composable
internal fun ControlDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 10.dp),
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
