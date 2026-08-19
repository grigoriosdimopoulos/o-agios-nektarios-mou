package gr.agiosnektarios.village.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.agiosnektarios.village.core.MapBasemap
import gr.agiosnektarios.village.core.geo.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.geo.IssueCluster
import gr.agiosnektarios.village.core.geo.IssueClustering
import gr.agiosnektarios.village.core.model.BlockSummary
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.StreetName
import gr.agiosnektarios.village.core.model.HomePin
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.settings.AppSettings
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.village.StreetNameRepository
import gr.agiosnektarios.village.data.village.VillageBlockRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.data.settings.FeatureRepository

data class MapFilters(
    val categories: Set<IssueCategory> = emptySet(),
    val statuses: Set<IssueStatus> = emptySet(),
) {
    val isActive: Boolean get() = categories.isNotEmpty() || statuses.isNotEmpty()

    /** An empty selection means "everything", which is what a fresh map shows. */
    fun matches(issue: Issue): Boolean =
        (categories.isEmpty() || issue.category in categories) &&
            (statuses.isEmpty() || issue.status in statuses)
}

data class MapUiState(
    val clusters: List<IssueCluster> = emptyList(),
    val blocks: List<BlockSummary> = emptyList(),
    val filters: MapFilters = MapFilters(),
    val showBlocks: Boolean = true,
    val basemap: MapBasemap = MapBasemap.STREETS,
    val loading: Boolean = true,
    val placingPin: Boolean = false,
    val pendingPin: GeoPoint? = null,
    val selectedCluster: IssueCluster? = null,
    val selectedBlock: BlockSummary? = null,
    /** Way id to street name, for the map's labels. */
    val streetNames: Map<String, String> = emptyMap(),
    /** The street whose naming sheet is open, if any. */
    val selectedStreet: SelectedStreet? = null,
    /** Whether to offer the "tap a street to name it" hint over the map. */
    val showStreetHint: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Where the phone is, when it knows and when it is inside the village.
     *
     * Held in the map's own state rather than asked for by the map, because a
     * position is a fact about the session and the map is only one of the
     * things that wants it.
     */
    val myPosition: GeoPoint? = null,
    /** The signed-in resident's own house pin, if they have set one. */
    val homePosition: GeoPoint? = null,
)

/**
 * A road the resident has tapped, and what the village currently calls it.
 *
 * [existing] is null for a street nobody has named yet, which is the common
 * case in this village and the reason the whole feature exists.
 */
data class SelectedStreet(
    val wayId: String,
    val existing: StreetName?,
    val canEdit: Boolean,
    val confirmedByMe: Boolean,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val featureRepository: FeatureRepository,
    private val issueRepository: IssueRepository,
    private val blockRepository: VillageBlockRepository,
    private val settingsRepository: SettingsRepository,
    private val streetNameRepository: StreetNameRepository,
    private val sessionRepository: SessionRepository,
    private val locationProvider: gr.agiosnektarios.village.data.location.LocationProvider,
) : ViewModel() {

    /** Kept out of [MapUiState] so camera movement never re-renders the sheets. */
    private val zoom = MutableStateFlow(gr.agiosnektarios.village.core.VillageConfig.DEFAULT_ZOOM)

    private val filters = MutableStateFlow(MapFilters())
    private val interaction = MutableStateFlow(MapInteraction())

    /** Write failures — naming a street is the first write this screen makes. */
    private val errors = MutableStateFlow<String?>(null)

    private val issues: StateFlow<List<Issue>> = issueRepository.observeIssues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Neighbourhood tallies are computed off the *unfiltered* set on purpose: a
     * block badge should say how many open reports the neighbourhood really
     * has, not how many survive the current filter.
     */
    private val blockSummaries: StateFlow<List<BlockSummary>> = issues
        .map { blockRepository.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val streets: StateFlow<List<StreetName>> =
        streetNameRepository.observeStreetNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The signed-in resident, held as state rather than collected.
     *
     * Naming a street has to stamp the author's uid on the write — the rules
     * refuse anything else — and that happens in a click handler, which needs
     * the value now rather than a flow to subscribe to.
     */
    /** What the village uses, for the chrome that is not always drawn. */
    val features: StateFlow<FeatureFlags> = featureRepository.flags

    private data class Viewer(val profile: UserProfile? = null, val home: HomePin? = null)

    // Profile and house pin travel together because they arrive from two
    // documents but describe one person; see [HomePin] for why they are not
    // one document.
    private val viewer: StateFlow<Viewer> =
        combine(sessionRepository.profile, sessionRepository.home, ::Viewer)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Viewer())

    /**
     * Everything that is not a report, folded into one flow.
     *
     * `combine` takes five sources in its typed overload and the map already
     * uses all five, so the rest travel together rather than as an untyped
     * vararg combine that loses every parameter name.
     */
    private data class MapContext(
        val interaction: MapInteraction,
        val settings: AppSettings,
        val streets: List<StreetName>,
        val viewer: Viewer,
        val error: String?,
    )

    val uiState: StateFlow<MapUiState> = combine(
        issues,
        blockSummaries,
        filters,
        zoom,
        combine(
            interaction,
            settingsRepository.settings,
            streets,
            viewer,
            errors,
            ::MapContext,
        ),
    ) { allIssues, blocks, activeFilters, currentZoom, context ->
        val visible = allIssues.filter(activeFilters::matches)
        val clusters = IssueClustering.cluster(visible, currentZoom)
        val interactionState = context.interaction
        val byWay = context.streets.associateBy { it.wayId }
        MapUiState(
            clusters = clusters,
            blocks = blocks,
            filters = activeFilters,
            showBlocks = context.settings.showBlocksLayer,
            basemap = context.settings.basemap,
            loading = false,
            placingPin = interactionState.placingPin,
            pendingPin = interactionState.pendingPin,
            myPosition = interactionState.myPosition,
            homePosition = context.viewer.home?.position,
            // Re-resolved from the live list so an open sheet updates when a
            // report inside it changes, instead of showing a frozen snapshot.
            selectedCluster = interactionState.selectedClusterId?.let { id ->
                clusters.firstOrNull { it.id == id }
            },
            selectedBlock = interactionState.selectedBlockId?.let { id ->
                blocks.firstOrNull { it.block.id == id }
            },
            streetNames = byWay.values
                .filter { it.name.isNotBlank() }
                .associate { it.wayId to it.name },
            // Same treatment as the sheets above: resolved live, so a name a
            // neighbour confirms while the sheet is open updates under it.
            selectedStreet = interactionState.selectedWayId?.let { wayId ->
                val existing = byWay[wayId]
                SelectedStreet(
                    wayId = wayId,
                    existing = existing,
                    canEdit = existing == null || existing.canEdit(context.viewer.profile),
                    confirmedByMe = existing?.isConfirmedBy(context.viewer.profile?.id) == true,
                )
            },
            errorMessage = context.error,
            // Only worth showing while there is something to name, and only
            // when the resident is not in the middle of placing a pin.
            showStreetHint = !context.settings.hasSeenStreetHint &&
                !interactionState.placingPin &&
                interactionState.selectedWayId == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /**
     * Asks the phone where it is, for the dot on the map.
     *
     * Silent about failure on purpose: a map with no blue dot is a map, while a
     * map with an error banner about location services is a scolding. The
     * provider already refuses anything outside the village, so a fix from a
     * drive down to Vilia does not put the dot in the wrong place.
     */
    fun locate() {
        viewModelScope.launch {
            val point = locationProvider.current() ?: return@launch
            interaction.update { it.copy(myPosition = point) }
        }
    }

    fun onZoomChanged(newZoom: Float) {
        // Clustering only changes meaningfully at half-step granularity; snapping
        // avoids recomputing on every pixel of a pinch.
        val snapped = (newZoom * 2).toInt() / 2f
        if (zoom.value != snapped) zoom.value = snapped
    }

    fun toggleCategory(category: IssueCategory) = filters.update { current ->
        current.copy(
            categories = if (category in current.categories) {
                current.categories - category
            } else {
                current.categories + category
            },
        )
    }

    fun toggleStatus(status: IssueStatus) = filters.update { current ->
        current.copy(
            statuses = if (status in current.statuses) {
                current.statuses - status
            } else {
                current.statuses + status
            },
        )
    }

    fun clearFilters() = filters.update { MapFilters() }

    fun setBasemap(basemap: MapBasemap) {
        viewModelScope.launch { settingsRepository.setBasemap(basemap) }
    }

    fun toggleBlocksLayer() {
        viewModelScope.launch {
            val current = uiState.value.showBlocks
            settingsRepository.setShowBlocksLayer(!current)
        }
    }

    fun startPlacingPin() = interaction.update {
        it.copy(placingPin = true, selectedClusterId = null, selectedBlockId = null)
    }

    fun cancelPlacingPin() = interaction.update { it.copy(placingPin = false, pendingPin = null) }

    fun onMapTapped(point: GeoPoint) {
        if (!interaction.value.placingPin) return
        interaction.update { it.copy(pendingPin = point) }
    }

    fun selectCluster(cluster: IssueCluster) =
        interaction.update { it.copy(selectedClusterId = cluster.id, selectedBlockId = null) }

    fun selectBlock(blockId: String) =
        interaction.update { it.copy(selectedBlockId = blockId, selectedClusterId = null) }

    fun dismissSheets() = interaction.update {
        it.copy(selectedClusterId = null, selectedBlockId = null, selectedWayId = null)
    }

    // ------------------------------------------------------------ street names

    /**
     * Opens the naming sheet for a tapped road.
     *
     * The village's streets are not in any public dataset — OpenStreetMap names
     * one way in the whole settlement — so this is where the names come from.
     */
    fun selectStreet(wayId: String) = interaction.update {
        it.copy(selectedWayId = wayId, selectedClusterId = null, selectedBlockId = null)
    }

    fun dismissStreetSheet() = interaction.update { it.copy(selectedWayId = null) }

    fun nameStreet(wayId: String, name: String) {
        val author = viewer.value.profile ?: return
        viewModelScope.launch {
            streetNameRepository.propose(wayId, name, author)
                .onSuccess { dismissStreetSheet() }
                .onFailure { error -> showError(error) }
        }
    }

    fun toggleStreetConfirmation(wayId: String, confirmed: Boolean) {
        val me = viewer.value.profile ?: return
        viewModelScope.launch {
            streetNameRepository.setConfirmed(wayId, me.id, confirmed)
                .onFailure { error -> showError(error) }
        }
    }

    fun clearStreetName(wayId: String) {
        viewModelScope.launch {
            streetNameRepository.clear(wayId)
                .onSuccess { dismissStreetSheet() }
                .onFailure { error -> showError(error) }
        }
    }

    fun dismissStreetHint() {
        viewModelScope.launch { settingsRepository.setStreetHintSeen() }
    }

    fun clearError() = errors.update { null }

    private fun showError(error: Throwable) {
        errors.update { error.message ?: "" }
    }

    private data class MapInteraction(
        val placingPin: Boolean = false,
        val pendingPin: GeoPoint? = null,
        val selectedClusterId: String? = null,
        val selectedBlockId: String? = null,
        val selectedWayId: String? = null,
        /**
         * Where the phone last said it was.
         *
         * Kept with the other transient map state rather than as a sixth
         * source, because `combine` tops out at five and because this is
         * exactly what interaction state is for: it belongs to looking at the
         * map, not to the village.
         */
        val myPosition: GeoPoint? = null,
    )
}
