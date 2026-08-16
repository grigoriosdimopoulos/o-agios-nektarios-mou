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
import gr.agiosnektarios.village.data.issue.IssueRepository
import gr.agiosnektarios.village.data.settings.SettingsRepository
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
    val errorMessage: String? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val blockRepository: VillageBlockRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Kept out of [MapUiState] so camera movement never re-renders the sheets. */
    private val zoom = MutableStateFlow(gr.agiosnektarios.village.core.VillageConfig.DEFAULT_ZOOM)

    private val filters = MutableStateFlow(MapFilters())
    private val interaction = MutableStateFlow(MapInteraction())

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

    val uiState: StateFlow<MapUiState> = combine(
        issues,
        blockSummaries,
        filters,
        zoom,
        combine(interaction, settingsRepository.settings) { interaction, settings ->
            interaction to settings
        },
    ) { allIssues, blocks, activeFilters, currentZoom, (interactionState, settings) ->
        val visible = allIssues.filter(activeFilters::matches)
        val clusters = IssueClustering.cluster(visible, currentZoom)
        MapUiState(
            clusters = clusters,
            blocks = blocks,
            filters = activeFilters,
            showBlocks = settings.showBlocksLayer,
            basemap = settings.basemap,
            loading = false,
            placingPin = interactionState.placingPin,
            pendingPin = interactionState.pendingPin,
            // Re-resolved from the live list so an open sheet updates when a
            // report inside it changes, instead of showing a frozen snapshot.
            selectedCluster = interactionState.selectedClusterId?.let { id ->
                clusters.firstOrNull { it.id == id }
            },
            selectedBlock = interactionState.selectedBlockId?.let { id ->
                blocks.firstOrNull { it.block.id == id }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

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

    fun dismissSheets() =
        interaction.update { it.copy(selectedClusterId = null, selectedBlockId = null) }

    private data class MapInteraction(
        val placingPin: Boolean = false,
        val pendingPin: GeoPoint? = null,
        val selectedClusterId: String? = null,
        val selectedBlockId: String? = null,
    )
}
