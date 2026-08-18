package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.ui.theme.Motion
import java.util.Date

/** One thing that happened to a report. */
data class TimelineEntry(
    val label: String,
    val detail: String?,
    val at: Date?,
    val tint: Color,
    val done: Boolean,
)

/**
 * What has happened to a report, as a sequence rather than a label.
 *
 * A status chip says where a report is now and nothing about how it got
 * there. For the village that history is the interesting part — who noticed
 * it, who took it on, when it was cleared — and it is the difference between
 * a database field and an account of something.
 *
 * The line between the dots is drawn per-segment rather than as one background
 * rule, so a step that has not happened yet reads as genuinely unreached
 * instead of as a completed step drawn in a paler colour.
 */
@Composable
fun IssueTimeline(issue: Issue, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val entries = buildList {
        add(
            TimelineEntry(
                label = issue.authorName.ifBlank { "—" },
                detail = null,
                at = issue.createdAt,
                tint = scheme.primary,
                done = true,
            ),
        )
        if (issue.isTaken) {
            add(
                TimelineEntry(
                    label = issue.assigneeName,
                    detail = null,
                    at = issue.assignedAt,
                    tint = IssueStatus.IN_PROGRESS.tint,
                    done = true,
                ),
            )
        }
        if (issue.status.isTerminal) {
            add(
                TimelineEntry(
                    label = issue.resolvedByName.ifBlank { issue.assigneeName },
                    detail = issue.resolutionNote.takeIf { it.isNotBlank() },
                    at = issue.updatedAt,
                    tint = issue.status.tint,
                    done = true,
                ),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp),
                ) {
                    Dot(tint = entry.tint)
                    if (index < entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(scheme.outlineVariant),
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(start = 10.dp, bottom = 10.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = relativeTime(entry.at),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    entry.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Springs in when a step is added, so taking a report has a visible result. */
@Composable
private fun Dot(tint: Color) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = Motion.playful(),
        label = "timelineDot",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(tint),
    )
}
