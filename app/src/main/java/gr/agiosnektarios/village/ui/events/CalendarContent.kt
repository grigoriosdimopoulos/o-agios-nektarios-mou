package gr.agiosnektarios.village.ui.events

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.EventKind
import gr.agiosnektarios.village.core.model.VillageEvent
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.theme.Space
import gr.agiosnektarios.village.ui.theme.raisedContainer
import gr.agiosnektarios.village.ui.theme.raisedOutline
import gr.agiosnektarios.village.ui.weather.clock
import gr.agiosnektarios.village.ui.weather.longDate
import java.util.Calendar

/**
 * What the village has coming.
 *
 * A flat list rather than a month grid, and that is the whole design. A grid is
 * for a diary with something on most days; this calendar will hold a liturgy,
 * a work day and the rubbish round, and a month of empty squares to find them
 * in would be worse than useless on a phone. What matters is the order and how
 * soon, so the list says "Today", "Tomorrow", then the date.
 */
@Composable
fun CalendarContent(
    state: CalendarUiState,
    onAttend: (VillageEvent) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What "today" means, injectable for the same reason the theme is.
     *
     * Every row on this screen is written relative to now — "Today",
     * "Tomorrow", then a date — and whether an event is over decides whether
     * it offers a button. Reading the wall clock inside meant the rendered
     * goldens were correct on the day they were recorded and wrong the next
     * morning, which is a snapshot test that guards nothing.
     */
    now: Long = System.currentTimeMillis(),
) {
    // Taking something off the village calendar is not undoable and the button
    // is a 32dp bin next to an edit pencil. Asking first is the difference
    // between a mis-tap and a liturgy nobody hears about.
    var pendingDelete by remember { mutableStateOf<VillageEvent?>(null) }

    when {
        state.loading -> ListSkeleton()
        state.events.isEmpty() -> EmptyState(
            emoji = "🗓",
            title = stringResource(R.string.calendar_empty_title),
            subtitle = stringResource(R.string.calendar_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = Space.gutter,
                bottom = BottomBarDefaults.contentPadding() + Space.page,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.gutter),
        ) {
            items(state.events, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    now = now,
                    userId = state.userId,
                    canManage = state.canModerate || event.authorId == state.userId,
                    onAttend = { onAttend(event) },
                    onEdit = { onEdit(event.id) },
                    onDelete = { pendingDelete = event },
                )
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(event.title) },
            text = { Text(stringResource(R.string.calendar_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(event.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EventCard(
    event: VillageEvent,
    now: Long,
    userId: String,
    canManage: Boolean,
    onAttend: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val attending = event.isAttending(userId)
    val past = event.isPast(now)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(raisedContainer)
            .then(raisedOutline?.let { Modifier.border(it, RoundedCornerShape(16.dp)) } ?: Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top-aligned, not centred: in Greek at one and a half times the text
        // the date runs to three lines, and a centred icon then floats in the
        // middle of them instead of sitting beside the first word.
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = event.eventKind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = whenLine(event, now),
                style = MaterialTheme.typography.labelLarge,
                color = if (past) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
            )
            if (canManage) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.calendar_edit),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            text = event.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (event.place.isNotBlank()) {
            Text(
                text = event.place,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.description.isNotBlank()) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // "I'll be there" only where a turnout is the point. A liturgy does not
        // need a headcount, and offering one on everything would make the
        // button mean nothing on the Saturday it does.
        if (event.eventKind.takesAttendance) {
            // Stacked. Side by side, "Nobody has said yet" read as a caption on
            // the button rather than as the state of the list it describes.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttendeeSummary(event)
                if (!past) AttendButton(attending = attending, onClick = onAttend)
            }
        }
    }
}

/**
 * The count, and then the names.
 *
 * The names are what does the work. "6 coming" is a statistic; "Μαρία, Γιώργος,
 * Νίκος…" is your neighbours, and it is the reason the seventh person taps the
 * button.
 */
@Composable
private fun AttendeeSummary(event: VillageEvent) {
    val names = event.attendeeNames
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (names.isEmpty()) {
                stringResource(R.string.calendar_nobody)
            } else {
                pluralStringResource(R.plurals.calendar_attendees, names.size, names.size)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (names.isNotEmpty()) {
            Text(
                text = names.take(6).joinToString(", ") +
                    if (names.size > 6) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Filled once you are coming, outlined until then.
 *
 * The first version used `secondaryContainer` for the unselected state, which
 * in this palette is a pale pink — on a green-and-cream card it read as an
 * error rather than as an invitation. An outline in the app's own green says
 * "not yet" without introducing a colour the app uses nowhere else.
 */
@Composable
private fun AttendButton(attending: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (attending) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "attendBackground",
    )
    val content = if (attending) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (attending) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp),
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (attending) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = stringResource(
                if (attending) R.string.calendar_attending else R.string.calendar_attend,
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

/** "Today · 10:00", "Tomorrow", or the full date. */
@Composable
private fun whenLine(event: VillageEvent, now: Long): String {
    val day = dayLabel(event.start, now)
    return if (event.allDay) {
        "$day · " + stringResource(R.string.calendar_all_day)
    } else {
        "$day · " + clock(event.start)
    }
}

@Composable
private fun dayLabel(millis: Long, now: Long): String {
    val today = now.startOfLocalDay()
    val day = millis.startOfLocalDay()
    // Day counting via Calendar rather than dividing by 86 400 000, which is
    // wrong on the two days a year that are not that long.
    return when (day) {
        today -> stringResource(R.string.calendar_today)
        Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis -> stringResource(R.string.calendar_tomorrow)
        // The year, once the date leaves the current one. Without it a bad or
        // mistyped event dated years ahead reads exactly like this year's.
        else -> {
            val label = longDate(millis).replaceFirstChar { it.uppercase() }
            if (yearOf(millis) == yearOf(now)) label else "$label ${yearOf(millis)}"
        }
    }
}

private fun yearOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

internal fun EventKind.icon(): ImageVector = when (this) {
    EventKind.CHURCH -> Icons.Filled.Church
    EventKind.FESTIVAL -> Icons.Filled.Celebration
    EventKind.WORK_DAY -> Icons.Filled.Handyman
    EventKind.MEETING -> Icons.Filled.Groups
    EventKind.SERVICE -> Icons.Filled.LocalShipping
    EventKind.OTHER -> Icons.Filled.Event
}

@Composable
internal fun EventKind.label(): String = stringResource(
    when (this) {
        EventKind.CHURCH -> R.string.event_kind_church
        EventKind.FESTIVAL -> R.string.event_kind_festival
        EventKind.WORK_DAY -> R.string.event_kind_work_day
        EventKind.MEETING -> R.string.event_kind_meeting
        EventKind.SERVICE -> R.string.event_kind_service
        EventKind.OTHER -> R.string.event_kind_other
    },
)
