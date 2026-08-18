package gr.agiosnektarios.village.ui.map

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
@Composable
fun MapSheet(
    issues: List<Issue>,
    onOpenIssue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeight = maxHeight
        val barClearance = BottomBarDefaults.contentPadding()

        // Peek shows the handle and the count; half is the reading position;
        // full leaves a strip of map so it never feels like a different screen.
        val stops = remember(fullHeight, barClearance) {
            mapOf(
                SheetStop.PEEK to fullHeight - (96.dp + barClearance),
                SheetStop.HALF to fullHeight * 0.45f,
                SheetStop.FULL to 64.dp,
            )
        }

        var stop by remember { mutableStateOf(SheetStop.PEEK) }
        var drag by remember { mutableStateOf(0f) }

        val resting = stops.getValue(stop)
        val settled by animateDpAsState(
            targetValue = resting,
            animationSpec = Motion.gentle(),
            label = "sheetOffset",
        )
        // While a finger is down the pane tracks it exactly; the spring only
        // takes over on release. Animating during the drag is what makes a
        // sheet feel like it is being negotiated with rather than moved.
        val offset = if (drag != 0f) {
            (settled + with(density) { drag.toDp() }).coerceIn(stops.getValue(SheetStop.FULL), stops.getValue(SheetStop.PEEK))
        } else {
            settled
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, with(density) { offset.roundToPx() }) }
                .height(fullHeight)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .draggable(
                    state = rememberDraggableState { delta -> drag += delta },
                    orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val landed = with(density) { (offset + drag.toDp()) }
                        stop = nearestStop(stops, landed, velocity)
                        drag = 0f
                    },
                ),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            alpha = 0.94f,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Handle()
                Text(
                    text = pluralStringResource(R.plurals.map_sheet_count, issues.size, issues.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Space.page, vertical = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.page,
                        end = Space.page,
                        top = Space.gutter,
                        bottom = barClearance + Space.page,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.gutter),
                ) {
                    items(issues, key = { it.id }) { issue ->
                        IssueCard(issue = issue, onClick = { onOpenIssue(issue.id) })
                    }
                }
            }
        }
    }
}

/**
 * Picks where a released drag lands.
 *
 * Velocity wins over position when the flick was decisive, because someone who
 * threw the pane upward means "open" even if their thumb stopped short of
 * halfway. Below that threshold it is simply the nearest stop.
 */
private fun nearestStop(
    stops: Map<SheetStop, Dp>,
    landed: Dp,
    velocity: Float,
): SheetStop {
    val order = listOf(SheetStop.FULL, SheetStop.HALF, SheetStop.PEEK)
    val nearest = order.minByOrNull { abs(stops.getValue(it).value - landed.value) }
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
