package gr.agiosnektarios.village.ui.announcements

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Announcement
import gr.agiosnektarios.village.ui.components.BytesImage
import gr.agiosnektarios.village.ui.components.EmptyState
import gr.agiosnektarios.village.ui.components.ListSkeleton
import gr.agiosnektarios.village.ui.components.TagPill
import gr.agiosnektarios.village.ui.components.relativeTime
import gr.agiosnektarios.village.ui.navigation.BottomBarDefaults
import gr.agiosnektarios.village.ui.components.ScreenHeader
import gr.agiosnektarios.village.ui.theme.Space

@Composable
fun AnnouncementsScreen(
    isAdmin: Boolean,
    onCompose: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AnnouncementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = onCompose,
                    // Clears the navigation bar, which is drawn over this
                    // screen rather than occupying a slot beside it.
                    modifier = Modifier.padding(bottom = BottomBarDefaults.contentPadding()),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.announcement_new),
                    )
                }
            }
        },
        // Only the top inset belongs to this Scaffold: the app's navigation
        // bar is drawn over the screen, so its height is added by whatever
        // must clear it rather than reserved here.
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = stringResource(R.string.announcements_title))

            when {
                state.loading -> ListSkeleton()
                state.announcements.isEmpty() -> EmptyState(
                    emoji = "📣",
                    title = stringResource(R.string.announcements_empty),
                )
                else -> LazyColumn(
                    // Clears the overlaid navigation bar; see BottomBarDefaults.
                    contentPadding = PaddingValues(
                        start = Space.page,
                        end = Space.page,
                        bottom = BottomBarDefaults.contentPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.gutter),
                ) {
                    items(state.announcements, key = { it.id }) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            isAdmin = isAdmin,
                            onEdit = { onEdit(announcement.id) },
                            onDelete = { viewModel.delete(announcement.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Announcements expand in place rather than opening a detail screen: most are
 * two lines long, and a full navigation for "the water is off on Tuesday" would
 * be more ceremony than content.
 */
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (announcement.pinned) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            if (announcement.image != null) {
                BytesImage(
                    bytes = announcement.image.toBytes(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (announcement.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.announcement_pinned),
                            modifier = Modifier.height(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (isAdmin) {
                        IconButton(onClick = onEdit, modifier = Modifier.height(28.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                                modifier = Modifier.height(16.dp),
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.height(28.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier.height(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagPill(text = announcement.authorName)
                    Text(
                        text = relativeTime(announcement.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
