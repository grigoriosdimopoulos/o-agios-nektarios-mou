package gr.agiosnektarios.village.ui.village

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.announcements.AnnouncementsContent
import gr.agiosnektarios.village.ui.announcements.AnnouncementsViewModel
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.events.CalendarContent
import gr.agiosnektarios.village.ui.events.CalendarViewModel
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.theme.Space

/**
 * Everything the village is telling itself: notices and the calendar.
 *
 * One tab rather than two because the bar is already at five, which is the most
 * a bottom bar can hold before it becomes a menu. The two belong together in
 * any case — an announcement says something happened, the calendar says
 * something *will* — and they were going to be read in the same visit.
 *
 * The split matters, though, and it is not cosmetic: an announcement speaks
 * *for* the village and only an administrator may write one, while an event
 * merely speaks *to* it and anyone may add one. Putting them behind one
 * "compose" button would have made that difference invisible, so the button
 * belongs to whichever half is showing.
 */
@Composable
fun VillageScreen(
    isAdmin: Boolean,
    onComposeAnnouncement: () -> Unit,
    onEditAnnouncement: (String) -> Unit,
    onComposeEvent: () -> Unit,
    onEditEvent: (String) -> Unit,
    announcements: AnnouncementsViewModel = hiltViewModel(),
    calendar: CalendarViewModel = hiltViewModel(),
) {
    // Saveable, so a rotation does not send someone back to the notices they
    // had just navigated away from.
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val announcementState by announcements.uiState.collectAsStateWithLifecycle()
    val calendarState by calendar.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            val canCompose = if (showCalendar) true else isAdmin
            if (canCompose) {
                FloatingActionButton(
                    onClick = if (showCalendar) onComposeEvent else onComposeAnnouncement,
                    modifier = Modifier.padding(bottom = BottomBarDefaults.contentPadding()),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(
                            if (showCalendar) R.string.calendar_add else R.string.announcement_new,
                        ),
                    )
                }
            }
        },
        // Only the top inset: the navigation bar is drawn over this screen
        // rather than beside it, so its height is added by whatever must clear
        // it. See BottomBarDefaults.
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = stringResource(R.string.nav_village))
            Segments(
                showCalendar = showCalendar,
                onSelect = { showCalendar = it },
                modifier = Modifier.padding(horizontal = Space.page, vertical = 6.dp),
            )
            if (showCalendar) {
                CalendarContent(
                    state = calendarState,
                    onAttend = calendar::toggleAttendance,
                    onEdit = onEditEvent,
                    onDelete = calendar::delete,
                )
            } else {
                AnnouncementsContent(
                    state = announcementState,
                    isAdmin = isAdmin,
                    onEdit = onEditAnnouncement,
                    onDelete = announcements::delete,
                )
            }
        }
    }
}

/**
 * Two words in a track, iOS-style, rather than Material tabs.
 *
 * Tabs with an underline sit directly under a title bar and read as a second
 * level of navigation; this is one screen with two views of the same subject,
 * and a segmented control is what says that.
 */
@Composable
private fun Segments(
    showCalendar: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Segment(
            label = stringResource(R.string.village_tab_announcements),
            selected = !showCalendar,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
        Segment(
            label = stringResource(R.string.village_tab_calendar),
            selected = showCalendar,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "segmentBackground",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}
