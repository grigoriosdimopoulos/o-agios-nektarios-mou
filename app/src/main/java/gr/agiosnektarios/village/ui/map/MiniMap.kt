package gr.agiosnektarios.village.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.geo.GeoBounds
import gr.agiosnektarios.village.core.geo.GeoPoint
import gr.agiosnektarios.village.core.geo.IssueCluster
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.ui.theme.LocalIsDarkTheme
import gr.agiosnektarios.village.ui.theme.raisedOutline

/**
 * Where the report is, on the screen that is about the report.
 *
 * A report has a coordinate and the detail screen never showed it: to see where
 * "πεσμένο δέντρο κλείνει τον δρόμο" actually was you had to go back to the map
 * and hunt for the pin — on an app whose other half is that map. This is not
 * interactive on purpose; it is a picture of a place with a tap that takes you
 * to the real thing, which is what a detail screen owes a location.
 */
@Composable
fun MiniMap(
    issue: Issue,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val point = remember(issue.lat, issue.lng) { GeoPoint(issue.lat, issue.lng) }
    val cluster = remember(issue.id) {
        IssueCluster(id = issue.id, position = point, issues = listOf(issue))
    }
    // A tight box around the point: about 120 m, which frames the report with
    // enough of its surroundings to be recognisable from a window.
    val frame = remember(point) {
        GeoBounds(
            south = point.lat - 0.00055,
            west = point.lng - 0.00070,
            north = point.lat + 0.00055,
            east = point.lng + 0.00070,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(raisedOutline?.let { Modifier.border(it, MaterialTheme.shapes.medium) } ?: Modifier)
            .clickable(onClick = onOpenMap),
    ) {
        VillageMap(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            clusters = listOf(cluster),
            blocks = emptyList(),
            showBlocks = false,
            pendingPin = null,
            darkTheme = dark,
            basemap = gr.agiosnektarios.village.core.MapBasemap.STREETS,
            greekLabels = true,
            streetNames = emptyMap(),
            focusedIssueId = issue.id,
            myPosition = null,
            homePosition = null,
            allowRoadTaps = false,
            focusBounds = frame,
            onZoomChanged = {},
            // Every gesture belongs to the tap that opens the real map. A small
            // map you can pan is a small map you get lost in.
            onMapTap = { onOpenMap() },
            onClusterTap = { onOpenMap() },
            onBlockTap = {},
            onRoadTap = {},
        )

        if (issue.placeLabel.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = issue.placeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
