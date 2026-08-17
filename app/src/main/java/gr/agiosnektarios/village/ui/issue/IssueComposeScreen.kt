package gr.agiosnektarios.village.ui.issue

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.MapBasemap
import gr.agiosnektarios.village.ui.theme.LocalIsDarkTheme
import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.ui.map.VillageMap
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.ui.components.BytesImage
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.ui.components.InlineSpinner
import gr.agiosnektarios.village.ui.components.IssueRow
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.components.VillageTextField
import gr.agiosnektarios.village.ui.components.isGreekLocale

/**
 * Compose or edit a report.
 *
 * The map here is a small, draggable-pin editor rather than a full map: the
 * location is already roughly chosen (by tapping the village map, or by the
 * report being edited) and this is for fine adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueComposeScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    showSnackbar: (String) -> Unit,
    viewModel: IssueComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val greek = isGreekLocale()
    val savedMessage = stringResource(
        if (state.isEditing) R.string.issue_updated else R.string.issue_submitted,
    )

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(viewModel::addPhoto) },
    )

    LaunchedEffect(state.savedIssueId) {
        state.savedIssueId?.let {
            showSnackbar(savedMessage)
            onSaved(it)
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.issue_edit else R.string.issue_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VillageTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = stringResource(R.string.issue_title_label),
                placeholder = stringResource(R.string.issue_title_hint),
                error = state.titleError?.let { stringResource(it) },
            )

            VillageTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = stringResource(R.string.issue_description_label),
                placeholder = stringResource(R.string.issue_description_hint),
                singleLine = false,
                minLines = 4,
                imeAction = ImeAction.Default,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.issue_category_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                IssueCategory.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { category ->
                            CategoryChip(
                                category = category,
                                selected = state.category == category,
                                onClick = { viewModel.onCategoryChange(category) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
                state.categoryError?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.issue_location_label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val blockName = if (greek) state.blockNameEl else state.blockNameEn
                    if (blockName.isNotBlank()) {
                        Text(
                            text = blockName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium),
                ) {
                    // The same map component as the main screen, with no
                    // neighbourhoods or clusters — just the one pin being placed.
                    VillageMap(
                        modifier = Modifier.fillMaxSize(),
                        clusters = emptyList(),
                        blocks = emptyList(),
                        showBlocks = false,
                        pendingPin = state.position,
                        darkTheme = LocalIsDarkTheme.current,
                        basemap = MapBasemap.STREETS,
                        greekLabels = greek,
                        // The picker's only job is to place one pin, so every
                        // tap is the pin: roads are drawn, but not tappable, and
                        // their names are not fetched for a thumbnail map.
                        streetNames = emptyMap(),
                        allowRoadTaps = false,
                        focusBounds = state.position?.let {
                            GeoBounds(it.lat, it.lng, it.lat, it.lng)
                        },
                        onMapTap = viewModel::setPosition,
                        onClusterTap = {},
                        onBlockTap = {},
                        onRoadTap = {},
                        onZoomChanged = {},
                    )
                }
                state.locationError?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Duplicate warning: appears only once both a category and a
            // location exist, which is when it can actually be accurate.
            AnimatedVisibility(
                visible = state.similarNearby.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.issue_nearby_similar),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    state.similarNearby.take(3).forEach { issue ->
                        IssueRow(issue = issue, onClick = {})
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.issue_photos_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.existingPhotos, key = { it.id }) { photo ->
                        PhotoThumb(
                            bytes = photo.bytes,
                            onRemove = { viewModel.removeExistingPhoto(photo.id) },
                        )
                    }
                    itemsIndexed(state.newPhotos) { index, bytes ->
                        PhotoThumb(
                            bytes = bytes,
                            onRemove = { viewModel.removeNewPhoto(index) },
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.uploadingPhoto) {
                                InlineSpinner()
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = stringResource(R.string.issue_add_photo),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                text = stringResource(
                    if (state.isEditing) R.string.action_save else R.string.issue_submit,
                ),
                onClick = viewModel::submit,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One picked photo with a remove affordance, shared by the kept and new strips. */
@Composable
private fun PhotoThumb(bytes: ByteArray?, onRemove: () -> Unit) {
    Box {
        BytesImage(
            bytes = bytes,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onRemove)
                .padding(2.dp),
        )
    }
}
