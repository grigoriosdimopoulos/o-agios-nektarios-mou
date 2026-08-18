package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.model.Issue
import androidx.compose.ui.draw.shadow
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedShadow
import gr.agiosnektarios.village.ui.theme.shadowTint
import gr.agiosnektarios.village.ui.theme.raisedOutline

/**
 * The report as it appears in every list in the app.
 *
 * Deliberately one component rather than per-screen variants: a resident should
 * recognise the same card whether they are browsing the village feed, a map
 * cluster, or their own profile.
 */
@Composable
fun IssueCard(
    issue: Issue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPhoto: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = gr.agiosnektarios.village.ui.theme.Motion.standard(),
        label = "issueCardScale",
    )

    val lift = raisedShadow

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            // Depth in the light theme, where a hairline alone was not enough
            // to lift a white card off a cream page. Zero in the dark theme,
            // which separates by value instead. See Surfaces.kt.
            // The card and the detail page's header carry the same key, so
            // tapping does not slide a new screen over this one — this one
            // becomes it.
            .sharedBoundsOrNone(SharedKeys.issueCard(issue.id))
            .then(
                if (lift > 0.dp) {
                    Modifier.shadow(
                        elevation = lift,
                        shape = MaterialTheme.shapes.medium,
                        ambientColor = shadowTint,
                        spotColor = shadowTint,
                    )
                } else {
                    Modifier
                },
            ),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        // See Surfaces.kt: naming a container role directly is what made cards
        // dissolve into the cream page in the light theme.
        colors = CardDefaults.cardColors(containerColor = raisedContainer),
        border = raisedOutline,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            if (showPhoto && issue.thumbnail != null) {
                BytesImage(
                    bytes = issue.thumbnail.toBytes(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryBadge(category = issue.category)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedElementOrNone(SharedKeys.issueTitle(issue.id)),
                    )
                    if (issue.description.isNotBlank()) {
                        Text(
                            text = issue.description,
                            // bodyMedium, not bodySmall. The ramp went 16sp
                            // title straight to 12sp body and then 11sp for
                            // everything else, so four fifths of every card
                            // was set in grey 11-12sp. One extra step makes
                            // the feed readable — which matters more here
                            // than in most apps, given who lives in this
                            // village.
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusChip(status = issue.status)
                        Text(
                            text = relativeTime(issue.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MetaCount(
                            icon = Icons.Filled.ThumbUp,
                            value = issue.upvotes,
                        )
                        MetaCount(
                            icon = Icons.AutoMirrored.Filled.Comment,
                            value = issue.commentCount,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = issue.authorName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // End-aligned inside a bounded width, not
                            // start-aligned inside a fixed 96dp. Fixed width
                            // left every name beginning on the same line and
                            // ending wherever it happened to end, 40-54dp
                            // short of the card's right edge — a column that
                            // visibly missed its margin.
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = 110.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact variant for the map's cluster sheet, where vertical space is scarce. */
@Composable
fun IssueRow(
    issue: Issue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category = issue.category, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTime(issue.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(status = issue.status)
    }
}
