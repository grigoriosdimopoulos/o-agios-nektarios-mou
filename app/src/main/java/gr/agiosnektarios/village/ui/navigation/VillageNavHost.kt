package gr.agiosnektarios.village.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.ui.admin.AdminScreen
import gr.agiosnektarios.village.ui.admin.AdminUserDetailScreen
import gr.agiosnektarios.village.ui.announcements.AnnouncementComposeScreen
import gr.agiosnektarios.village.ui.announcements.AnnouncementsScreen
import gr.agiosnektarios.village.ui.auth.CompleteProfileScreen
import gr.agiosnektarios.village.ui.auth.ForgotPasswordScreen
import gr.agiosnektarios.village.ui.auth.SignInScreen
import gr.agiosnektarios.village.ui.auth.SignUpScreen
import gr.agiosnektarios.village.ui.chat.ChatRoomScreen
import gr.agiosnektarios.village.ui.chat.ChatsScreen
import gr.agiosnektarios.village.ui.chat.NewChatScreen
import gr.agiosnektarios.village.ui.issue.IssueComposeScreen
import gr.agiosnektarios.village.ui.issue.IssueDetailScreen
import gr.agiosnektarios.village.ui.issue.IssueListScreen
import gr.agiosnektarios.village.ui.map.MapScreen
import gr.agiosnektarios.village.ui.profile.EditProfileScreen
import gr.agiosnektarios.village.ui.profile.ProfileScreen
import gr.agiosnektarios.village.ui.settings.ChangePasswordScreen
import gr.agiosnektarios.village.ui.settings.SettingsScreen
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.components.LocalSharedTransitionScope
import gr.agiosnektarios.village.ui.components.LocalAnimatedVisibilityScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi

/**
 * The push. A new screen slides in from the trailing edge while the one it
 * covers slides a *third* of the way out and dims underneath it.
 *
 * That asymmetry is the whole trick. A full-width slide reads as two unrelated
 * screens swapping; a partial slide with the outgoing screen still visible and
 * darkened reads as depth — one thing on top of another. It is the most
 * recognisable single detail of iOS navigation and costs four functions.
 *
 * **Nothing here fades the moving screen, and that is deliberate.** Two full
 * screens are composed at once for the length of a push. An arriving screen at
 * 60% alpha is a window onto the one it is covering, and the result is both
 * screens' text legible on top of each other — which is what "the components
 * break" looks like. The screens are opaque; only the underlay dims, and it
 * dims because it is *behind*, not because it is on its way out.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Motion.PUSH_MS, easing = Motion.enter),
        initialOffsetX = { it },
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Motion.PUSH_MS, easing = Motion.enter),
        targetOffsetX = { -it / Motion.PARALLAX_FRACTION },
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Motion.POP_MS, easing = Motion.enter),
        initialOffsetX = { -it / Motion.PARALLAX_FRACTION },
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Motion.POP_MS, easing = Motion.exit),
        targetOffsetX = { it },
    )

/**
 * Tabs are siblings, not a hierarchy, so they cross-fade rather than push.
 *
 * This used to be described in a comment and implemented nowhere: every
 * destination inherited the push, so switching tabs slid the whole map
 * sideways with a parallax meant for going one level deeper. Worse, the tab
 * switcher pops back to the start destination, so half the tab switches were
 * *pops* — the map came in from the left, the others from the right, and the
 * direction of travel meant nothing.
 */
private fun tabEnter(): EnterTransition =
    fadeIn(animationSpec = tween(Motion.TAB_MS, easing = Motion.standardEasing))

private fun tabExit(): ExitTransition =
    fadeOut(animationSpec = tween(Motion.TAB_MS, easing = Motion.standardEasing))

/** Which motion a destination arrives with. */
private enum class ScreenMotion { PUSH, TAB }

/**
 * An opaque floor under every screen, plus the dim that makes a push read as
 * depth.
 *
 * Screens in this app root themselves in whatever suits them — a Scaffold, a
 * Column, a LazyColumn — and a Column paints nothing. During a push that meant
 * the screen being left showed straight through the screen arriving. Rather
 * than requiring twenty screens to remember a background, the graph gives each
 * destination one.
 *
 * The dim is drawn here rather than expressed as alpha in the transition
 * spec, because alpha on an opaque screen blends it toward whatever is behind
 * — in the light theme, toward cream. That washes the underlay out and makes
 * it look like it is dissolving. A scrim darkens it in both themes, which is
 * what "behind" looks like.
 */
@Composable
private fun AnimatedContentScope.ScreenBackdrop(
    motion: ScreenMotion,
    content: @Composable () -> Unit,
) {
    // Only a push has an underlay. Tabs cross-fade, and dimming a screen that
    // is already fading out just makes the swap look like a flicker.
    val dim by transition.animateFloat(
        transitionSpec = { tween(Motion.PUSH_MS, easing = Motion.enter) },
        label = "underlayDim",
    ) { state ->
        if (motion == ScreenMotion.PUSH && state == EnterExitState.PostExit) {
            Motion.UNDERLAY_DIM
        } else {
            0f
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawWithContent {
                drawContent()
                if (dim > 0f) drawRect(color = Color.Black.copy(alpha = dim))
            },
    ) {
        content()
    }
}

/**
 * A destination, with a floor under it and the right motion attached.
 *
 * Wrapping `composable` rather than repeating four transition arguments and a
 * background at twenty-two call sites: the ones that were meant to differ were
 * the four tabs, and before this they silently did not.
 */
private fun NavGraphBuilder.screen(
    route: String,
    motion: ScreenMotion = ScreenMotion.PUSH,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = { if (motion == ScreenMotion.TAB) tabEnter() else pushEnter() },
    exitTransition = { if (motion == ScreenMotion.TAB) tabExit() else pushExit() },
    popEnterTransition = { if (motion == ScreenMotion.TAB) tabEnter() else popEnter() },
    popExitTransition = { if (motion == ScreenMotion.TAB) tabExit() else popExit() },
) { entry ->
    // Each destination publishes its own AnimatedVisibilityScope, which is the
    // half of a shared transition that knows *when* the element is arriving or
    // leaving. Without it a shared key is inert.
    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
        ScreenBackdrop(motion) { content(entry) }
    }
}

/**
 * The whole navigation graph.
 *
 * Tab destinations cross-fade (they are siblings, not a hierarchy) while detail
 * destinations slide in from the side, so the direction of travel is always
 * legible.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VillageNavHost(
    navController: NavHostController,
    startDestination: String,
    signedIn: Boolean,
    profile: UserProfile?,
    showSnackbar: (String) -> Unit,
) {
    // Everything the graph draws lives inside one SharedTransitionLayout, which
    // is what lets a card on one screen and a header on the next be understood
    // as the same object rather than two similar rectangles.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
        // ------------------------------------------------------------ auth
        screen(Routes.SIGN_IN) {
            // No onSignedIn callback: SessionState swaps the whole graph.
            SignInScreen(
                onNavigateToSignUp = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        screen(Routes.SIGN_UP) {
            SignUpScreen(onBack = navController::popBackStack)
        }
        screen(
            Routes.FORGOT_PASSWORD,
        ) {
            ForgotPasswordScreen(onBack = navController::popBackStack)
        }
        screen(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen()
        }

        if (!signedIn) return@NavHost

        // ------------------------------------------------------- main tabs
        screen(Routes.MAP, motion = ScreenMotion.TAB) {
            MapScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
                onCreateIssueAt = { lat, lng ->
                    navController.navigate(Routes.newIssueAt(lat, lng))
                },
            )
        }
        screen(Routes.ISSUES, motion = ScreenMotion.TAB) {
            IssueListScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
            )
        }
        screen(
            route = Routes.ANNOUNCEMENTS,
            motion = ScreenMotion.TAB,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME/announcements" }),
        ) {
            AnnouncementsScreen(
                isAdmin = profile?.isAdmin == true,
                onCompose = { navController.navigate(Routes.announcementCompose()) },
                onEdit = { navController.navigate(Routes.announcementCompose(it)) },
            )
        }
        screen(Routes.CHATS, motion = ScreenMotion.TAB) {
            ChatsScreen(
                onOpenChat = { navController.navigate(Routes.chatRoom(it)) },
                onNewChat = { navController.navigate(Routes.NEW_CHAT) },
            )
        }
        screen(Routes.PROFILE, motion = ScreenMotion.TAB) {
            ProfileScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
            )
        }

        // ----------------------------------------------------- issue detail
        screen(
            route = Routes.ISSUE_DETAIL,
            arguments = listOf(navArgument("issueId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "$DEEP_LINK_SCHEME/issue/{issueId}" },
            ),
        ) {
            IssueDetailScreen(
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(Routes.editIssue(it)) },
                onDeleted = navController::popBackStack,
                onOpenChatWith = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                showSnackbar = showSnackbar,
            )
        }
        screen(
            route = Routes.ISSUE_COMPOSE,
            arguments = listOf(
                navArgument("issueId") { type = NavType.StringType; defaultValue = "" },
                navArgument("lat") { type = NavType.StringType; defaultValue = "0.0" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "0.0" },
            ),
        ) {
            IssueComposeScreen(
                onBack = navController::popBackStack,
                onSaved = { issueId ->
                    navController.popBackStack()
                    navController.navigate(Routes.issueDetail(issueId))
                },
                showSnackbar = showSnackbar,
            )
        }

        // ------------------------------------------------------------ chat
        screen(
            route = Routes.CHAT_ROOM,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME/chat/{chatId}" }),
        ) {
            ChatRoomScreen(onBack = navController::popBackStack)
        }
        screen(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = navController::popBackStack,
                onChatReady = { chatId ->
                    navController.popBackStack()
                    navController.navigate(Routes.chatRoom(chatId))
                },
            )
        }

        // -------------------------------------------------- profile & admin
        screen(Routes.EDIT_PROFILE) {
            EditProfileScreen(onBack = navController::popBackStack, showSnackbar = showSnackbar)
        }
        screen(Routes.SETTINGS) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                showSnackbar = showSnackbar,
            )
        }
        screen(
            Routes.CHANGE_PASSWORD,
        ) {
            ChangePasswordScreen(onBack = navController::popBackStack, showSnackbar = showSnackbar)
        }
        screen(Routes.ADMIN) {
            AdminScreen(
                onBack = navController::popBackStack,
                onOpenUser = { navController.navigate(Routes.adminUser(it)) },
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
            )
        }
        screen(
            route = Routes.ADMIN_USER_DETAIL,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) {
            AdminUserDetailScreen(
                onBack = navController::popBackStack,
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
                showSnackbar = showSnackbar,
            )
        }

        // --------------------------------------------------- announcements
        screen(
            route = Routes.ANNOUNCEMENT_COMPOSE,
            arguments = listOf(
                navArgument("announcementId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            AnnouncementComposeScreen(
                onBack = navController::popBackStack,
                showSnackbar = showSnackbar,
            )
        }
            }
        }
    }
}
