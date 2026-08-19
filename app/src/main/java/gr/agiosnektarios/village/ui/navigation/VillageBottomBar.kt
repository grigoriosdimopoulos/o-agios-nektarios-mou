package gr.agiosnektarios.village.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.chat.ChatRepository
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.ui.theme.Motion
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.graphics.Color
import gr.agiosnektarios.village.ui.components.GlassSurface
import gr.agiosnektarios.village.core.model.Feature
import gr.agiosnektarios.village.core.model.FeatureFlags
import gr.agiosnektarios.village.data.settings.FeatureRepository

/**
 * How much room the bar takes at the bottom of the screen.
 *
 * The bar floats *over* the content rather than occupying a slot in the
 * Scaffold, so nothing reserves this space automatically: a list that should
 * scroll clear of the bar asks for it here. Static per screen — a tab always
 * has the bar, a detail screen never does — so no layout ever changes size
 * because of it, which is what the slot version did mid-transition.
 */
object BottomBarDefaults {
    /** Material's NavigationBar height, which this bar does not override. */
    val BarHeight = 80.dp

    @Composable
    fun contentPadding(): Dp =
        BarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

@Composable
fun VillageBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BottomBarViewModel = hiltViewModel(),
) {
    val unreadChats by viewModel.unreadChats.collectAsStateWithLifecycle()
    val flags by viewModel.flags.collectAsStateWithLifecycle()

    // Glass rather than an opaque bar: content scrolling beneath tints through
    // it, which is what makes chrome feel like it is floating above the app
    // instead of being a wall the app stops at.
    // The tint is left to GlassSurface's default, which picks a container that
    // contrasts with the page. Naming one here is how the bar previously ended
    // up nearly the same colour as the screen behind it.
    GlassSurface(modifier = modifier) {
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        // A tab for a feature the village has switched off is a tab that opens
        // an empty screen, so it is not drawn. The chat tab is the only one
        // that can disappear: everything else in this bar is what the app is.
        TopLevelDestination.entries
            .filter { it != TopLevelDestination.CHATS || flags.isOn(Feature.CHAT) }
            .forEach { destination ->
            val selected = currentRoute == destination.route
            // Selected icons swell slightly; combined with the crossfade between
            // outlined and filled variants this reads as a physical "press in".
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.14f else 1f,
                animationSpec = Motion.standard(),
                label = "tabScale",
            )

            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    val badgeCount =
                        if (destination == TopLevelDestination.CHATS) unreadChats else 0
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") }
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                                    (fadeOut() + scaleOut(targetScale = 0.8f))
                            },
                            label = "tabIcon",
                        ) { isSelected ->
                            Icon(
                                imageVector = if (isSelected) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = null,
                                modifier = Modifier.scale(scale),
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
        }
    }
}

@HiltViewModel
class BottomBarViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    chatRepository: ChatRepository,
    featureRepository: FeatureRepository,
) : ViewModel() {

    val flags: StateFlow<FeatureFlags> = featureRepository.flags

    /** Number of conversations with at least one unseen message, not total messages. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadChats: StateFlow<Int> = sessionRepository.profile
        .flatMapLatest { profile: UserProfile? ->
            if (profile == null) {
                flowOf(0)
            } else {
                chatRepository.observeChats(profile.id)
                    .map { chats -> chats.count { it.unreadFor(profile.id) > 0 } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
