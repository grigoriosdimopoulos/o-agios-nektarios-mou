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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.ui.theme.raisedContainer
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

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().scale(scale),
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
                    )
                    if (issue.description.isNotBlank()) {
                        Text(
                            text = issue.description,
                            style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.labelSmall,
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(96.dp),
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
