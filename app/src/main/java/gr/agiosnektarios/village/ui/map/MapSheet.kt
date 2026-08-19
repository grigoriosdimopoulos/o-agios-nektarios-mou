package gr.agiosnektarios.village.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.ui.components.GlassSurface
import gr.agiosnektarios.village.ui.components.IssueCard
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.theme.Space
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState

/** Where the sheet can rest. Only three, because a drag should always land somewhere. */
enum class SheetStop { PEEK, HALF, FULL }

/**
 * The village's reports, on a pane you pull up over the map.
 *
 * The map and the list were two tabs showing the same forty reports, and
 * switching between them threw away the context of whichever you were in.
 * This is the pattern every serious map application converged on for a reason:
 * the map is the ground, the list is a drawer over it, and you decide how much
 * of each you want by dragging rather than by navigating.
 *
 * Three stops rather than free positioning. A pane that rests wherever you let
 * go feels loose, and a resident should not have to aim — a flick in either
 * direction lands somewhere deliberate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapSheet(
    issues: List<Issue>,
    onOpenIssue: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Called with the report sitting under the middle of the drawer as it
     * scrolls, so the map can light up where that one is.
     *
     * A list of reports beside a map of reports is two things; a list that
     * points at the map while you scroll it is one. Null when the drawer is
     * closed or the list is empty.
     */
    onFocusedIssue: (String?) -> Unit = {},
    /**
     * A line under the count, and something at the end of the row.
     *
     * The drawer's peek strip is the only piece of the map that is always on
     * screen and is not the navigation bar or the button, which makes it the
     * one place "somewhere at the bottom" can actually mean. The weather goes
     * in [trailing] and the date in [subtitle].
     */
    subtitle: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    /**
     * The measured height of the header above, reported so the button and the
     * street hint can clear the closed drawer at any text size.
     *
     * This used to be a 96dp constant, which is right at the default text size
     * and wrong at every other: the header is two lines of type, so at twice
     * the size it is 40dp taller than the space reserved for it and the drawer
     * closed over its own contents.
     */
    onPeekHeight: (Dp) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val fullHeight = maxHeight
        val barClearance = BottomBarDefaults.contentPadding()

        // What the closed drawer actually shows, measured rather than assumed.
        var peekHeight by remember { mutableStateOf(MapSheetDefaults.peekHeight) }
        LaunchedEffect(peekHeight) { onPeekHeight(peekHeight) }

        // Peek shows the handle and the count; half is the reading position;
        // full leaves a strip of map so it never feels like a different screen.
        val stops: Map<SheetStop, Float> = remember(fullHeight, barClearance, density, peekHeight) {
            with(density) {
                // Ordered, and never inverted. updateBounds throws if lower
                // exceeds upper, which a window shorter than the peek strip
                // plus the navigation bar would produce — unreachable on a
                // phone, reachable in a resizable multi-window.
                val top = 64.dp.toPx()
                val peek = (fullHeight - peekHeight - barClearance)
                    .toPx()
                    .coerceAtLeast(top)
                mapOf(
                    SheetStop.PEEK to peek,
                    SheetStop.HALF to (fullHeight * 0.45f).toPx().coerceIn(top, peek),
                    SheetStop.FULL to top,
                )
            }
        }

        // One Animatable holds the position, and it is the *only* thing that
        // does.
        //
        // The first version kept the resting stop in one state and the live
        // drag in another, then added them. Two bugs came out of that and both
        // were visible on every single drag: the release position was computed
        // with the finger's travel counted twice, and because the resting
        // value had never moved, letting go snapped the pane back to where the
        // gesture started before springing to the new stop. A pane that jumps
        // backwards when you release it is worse than one that does not move
        // at all.
        val offset = remember { Animatable(stops.getValue(SheetStop.PEEK)) }
        LaunchedEffect(stops) {
            offset.updateBounds(
                lowerBound = stops.getValue(SheetStop.FULL),
                upperBound = stops.getValue(SheetStop.PEEK),
            )
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .height(fullHeight)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .draggable(
                    state = rememberDraggableState { delta ->
                        // snapTo, not animateTo: while a finger is down the
                        // pane must track it exactly. Animating during the drag
                        // is the difference between a sheet being negotiated
                        // with and a sheet being operated.
                        scope.launch { offset.snapTo(offset.value + delta) }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val target = stops.getValue(nearestStop(stops, offset.value, velocity))
                        scope.launch { offset.animateTo(target, Motion.gentle()) }
                    },
                ),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            alpha = 0.94f,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.onSizeChanged { measured ->
                        peekHeight = with(density) { measured.height.toDp() }
                    },
                ) {
                    Handle()
                    // Flowing, not a fixed row.
                    //
                    // A `Row` measures its unweighted children first and gives
                    // them whatever they ask for. In Greek at one and a half
                    // times the text the weather strip asks for the entire
                    // width, which left the report count with about five
                    // device-independent pixels and rendered it one letter per
                    // line. The render is in the goldens. Here the strip simply
                    // drops to its own line when it will not fit beside the
                    // count, and the drawer's peek grows to suit because that
                    // height is measured rather than assumed.
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Space.page, end = Space.page - 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.map_sheet_count,
                                    issues.size,
                                    issues.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            subtitle()
                        }
                        trailing()
                    }
                }
                val listState = rememberLazyListState()

                // Which card is under the middle of the visible strip. Derived
                // rather than computed on every frame: recomposing the map's
                // highlight on each scroll pixel would cost a geometry upload
                // per frame.
                val focused by remember(listState) {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
                        info.visibleItemsInfo
                            .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - middle) }
                            ?.key as? String
                    }
                }
                LaunchedEffect(focused) { onFocusedIssue(focused) }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Space.page,
                        end = Space.page,
                        top = Space.gutter,
                        bottom = barClearance + Space.page,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.gutter),
                ) {
                    items(issues, key = { it.id }) { issue ->
                        // No shared-element key here. The Reports tab's list
                        // already claims these keys, and two live elements on
                        // one key inside a single SharedTransitionLayout is an
                        // artefact waiting to happen when the tabs cross-fade.
                        IssueCard(issue = issue, onClick = { onOpenIssue(issue.id) }, shareKey = false)
                    }
                }
            }
        }
    }
}

/**
 * The height a caller should assume before the drawer has measured itself.
 *
 * Only a starting value now: the real one is measured from the header and
 * reported through `onPeekHeight`, because the header grows with the text size
 * and a constant is right at exactly one of them.
 */
object MapSheetDefaults {
    val peekHeight = 96.dp
}

/**
 * Picks where a released drag lands.
 *
 * Velocity wins over position when the flick was decisive, because someone who
 * threw the pane upward means "open" even if their thumb stopped short of
 * halfway. Below that threshold it is simply the nearest stop.
 */
private fun nearestStop(
    stops: Map<SheetStop, Float>,
    landed: Float,
    velocity: Float,
): SheetStop {
    val order = listOf(SheetStop.FULL, SheetStop.HALF, SheetStop.PEEK)
    val nearest = order.minByOrNull { abs(stops.getValue(it) - landed) }
        ?: SheetStop.PEEK
    if (abs(velocity) < FLICK_VELOCITY) return nearest
    val index = order.indexOf(nearest)
    // Positive velocity is downward on screen, which means closing.
    val shifted = if (velocity > 0) index + 1 else index - 1
    return order.getOrNull(shifted) ?: nearest
}

/** The grip. Wide enough to read as draggable without a label saying so. */
@Composable
private fun Handle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

private const val FLICK_VELOCITY = 900f
