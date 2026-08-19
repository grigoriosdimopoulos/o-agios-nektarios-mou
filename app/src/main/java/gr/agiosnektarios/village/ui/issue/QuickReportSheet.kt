package gr.agiosnektarios.village.ui.issue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.theme.rememberHaptics
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.components.BytesImage
import gr.agiosnektarios.village.ui.components.PrimaryButton
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedOutline
import gr.agiosnektarios.village.ui.components.fadingEdges
import gr.agiosnektarios.village.ui.components.CategoryChip
import gr.agiosnektarios.village.core.model.IssueCategory
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import gr.agiosnektarios.village.ui.theme.primaryInk
import gr.agiosnektarios.village.ui.theme.errorInk

/**
 * The ten-second report.
 *
 * Everything on this sheet is either already known or optional. The photo is
 * taken before the sheet appears, the position arrives from GPS while the
 * sheet is opening, and the only thing asked for is one line saying what is
 * wrong — and even that is optional if there is a picture.
 *
 * There is deliberately no category picker, no title field and no separate
 * description. Sixteen categories in eight rows is a form; this is a message.
 * Anything missing can be corrected afterwards by the author or a moderator,
 * and a vague report that exists beats a precise one nobody filed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickReportSheet(
    state: QuickReportUiState,
    onTextChange: (String) -> Unit,
    onRetakePhoto: () -> Unit,
    onRetryLocation: () -> Unit,
    onPickOnMap: () -> Unit,
    onSubmit: () -> Unit,
    onCategoryChange: (IssueCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Scrollable, because this sheet has to survive two things at
            // once that it was not sized for: a resident with accessibility
            // text at 2x, and the keyboard taking half the screen while they
            // type. Without it there is simply nowhere for the send button to
            // be.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_report_title),
            style = MaterialTheme.typography.titleLarge,
        )

        PhotoSlot(state = state, onRetakePhoto = onRetakePhoto)

        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.quick_report_hint)) },
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Default,
            ),
        )

        CategoryStrip(selected = state.category, onSelect = onCategoryChange)

        LocationLine(
            state = state,
            onRetryLocation = onRetryLocation,
            onPickOnMap = onPickOnMap,
        )

        PrimaryButton(
            text = stringResource(R.string.quick_report_send),
            onClick = { haptics.committed(); onSubmit() },
            enabled = state.canSubmit,
            loading = state.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhotoSlot(state: QuickReportUiState, onRetakePhoto: () -> Unit) {
    val outline = raisedOutline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(raisedContainer)
            .then(
                outline?.let { Modifier.androidBorder(it) } ?: Modifier,
            )
            .clickable(onClick = onRetakePhoto),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.encoding -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            state.photo != null -> {
                BytesImage(
                    bytes = state.photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop,
                )
                // A small affordance over the corner rather than a button
                // beside it: the picture is the control.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.quick_report_retake),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(R.string.quick_report_add_photo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Says where the report will land, and never silently guesses.
 *
 * A report filed onto the wrong house is worse than one with no location, so
 * when there is no fix this states it plainly and offers the map instead of
 * quietly dropping a pin in the middle of the village.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationLine(
    state: QuickReportUiState,
    onRetryLocation: () -> Unit,
    onPickOnMap: () -> Unit,
) {
    // A column of two rows, not one row of four things.
    //
    // Putting the status text and two actions side by side worked at the
    // default text size and collapsed at every size above it: unweighted
    // children are measured first, so at 1.5x in Greek the two links ate the
    // row, "Δεν βρέθηκε τοποθεσία" was squeezed to zero width and vanished,
    // and the send button was pushed off the bottom of the screen. That is the
    // configuration this village's residents actually use, on the one state
    // this whole path exists to rescue.
    //
    // Stacked, the text has the full width and the actions wrap among
    // themselves, so nothing can be crushed out of existence by anything else.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.fix == FixState.LOCATING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (state.position != null) {
                        Icons.Filled.MyLocation
                    } else {
                        Icons.Filled.LocationOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state.position != null) {
                        MaterialTheme.colorScheme.primaryInk
                    } else {
                        MaterialTheme.colorScheme.errorInk
                    },
                )
            }
            Text(
                text = when {
                    state.fix == FixState.LOCATING -> stringResource(R.string.quick_report_locating)
                    state.position != null -> stringResource(R.string.quick_report_here)
                    else -> stringResource(R.string.quick_report_no_fix)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.position == null && state.fix != FixState.LOCATING) {
                LocationAction(
                    text = stringResource(R.string.quick_report_pick),
                    onClick = onRetryLocation,
                )
            }
            LocationAction(
                text = stringResource(R.string.quick_report_change),
                onClick = onPickOnMap,
            )
        }
    }
}

@Composable
private fun LocationAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primaryInk,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** Border helper: BorderStroke needs a shape, and repeating that reads badly inline. */
private fun Modifier.androidBorder(stroke: BorderStroke): Modifier =
    border(stroke, RoundedCornerShape(18.dp))

/**
 * The category, on the sheet rather than behind a link to a bigger form.
 *
 * It used to live only in the full composer, reached by a link that was hidden
 * the moment a photo existed — so adding a picture made the category
 * disappear, which is exactly as strange as it sounds. A horizontal strip
 * costs one line of the sheet and removes the only reason to leave it.
 *
 * Nothing is required: the default is OTHER and anyone can correct it later.
 * The strip exists so that someone who *knows* it is a fallen tree can say so
 * in one tap, not so that everyone must classify their own emergency.
 */
@Composable
private fun CategoryStrip(
    selected: IssueCategory,
    onSelect: (IssueCategory) -> Unit,
) {
    val row = rememberLazyListState()
    LazyRow(
        state = row,
        modifier = Modifier.fillMaxWidth().fadingEdges(row),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(IssueCategory.entries.toList()) { category ->
            CategoryChip(
                category = category,
                selected = category == selected,
                onClick = { onSelect(category) },
            )
        }
    }
}
