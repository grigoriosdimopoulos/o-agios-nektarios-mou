package gr.agiosnektarios.village.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
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

/**
 * The push. A new screen slides in from the trailing edge while the one it
 * covers slides a *third* of the way out and dims underneath it.
 *
 * That asymmetry is the whole trick. A full-width slide reads as two unrelated
 * screens swapping; a partial slide with the outgoing screen still visible and
 * darkened reads as depth — one thing on top of another. It is the most
 * recognisable single detail of iOS navigation and costs four functions.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Motion.PUSH_MS, easing = Motion.enter),
        initialOffsetX = { it },
    ) + fadeIn(animationSpec = tween(Motion.PUSH_MS / 2, easing = Motion.enter))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Motion.PUSH_MS, easing = Motion.enter),
        targetOffsetX = { -it / Motion.PARALLAX_FRACTION },
    ) + fadeOut(
        animationSpec = tween(Motion.PUSH_MS, easing = Motion.enter),
        targetAlpha = 1f - Motion.UNDERLAY_DIM,
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Motion.POP_MS, easing = Motion.enter),
        initialOffsetX = { -it / Motion.PARALLAX_FRACTION },
    ) + fadeIn(
        animationSpec = tween(Motion.POP_MS, easing = Motion.enter),
        initialAlpha = 1f - Motion.UNDERLAY_DIM,
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Motion.POP_MS, easing = Motion.exit),
        targetOffsetX = { it },
    ) + fadeOut(animationSpec = tween(Motion.POP_MS, easing = Motion.exit), targetAlpha = 0.6f)

/**
 * The whole navigation graph.
 *
 * Tab destinations cross-fade (they are siblings, not a hierarchy) while detail
 * destinations slide in from the side, so the direction of travel is always
 * legible.
 */
@Composable
fun VillageNavHost(
    navController: NavHostController,
    startDestination: String,
    signedIn: Boolean,
    profile: UserProfile?,
    showSnackbar: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        // ------------------------------------------------------------ auth
        composable(Routes.SIGN_IN) {
            // No onSignedIn callback: SessionState swaps the whole graph.
            SignInScreen(
                onNavigateToSignUp = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(onBack = navController::popBackStack)
        }
        composable(
            Routes.FORGOT_PASSWORD,
        ) {
            ForgotPasswordScreen(onBack = navController::popBackStack)
        }
        composable(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen()
        }

        if (!signedIn) return@NavHost

        // ------------------------------------------------------- main tabs
        composable(Routes.MAP) {
            MapScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
                onCreateIssueAt = { lat, lng ->
                    navController.navigate(Routes.newIssueAt(lat, lng))
                },
            )
        }
        composable(Routes.ISSUES) {
            IssueListScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
            )
        }
        composable(
            route = Routes.ANNOUNCEMENTS,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME/announcements" }),
        ) {
            AnnouncementsScreen(
                isAdmin = profile?.isAdmin == true,
                onCompose = { navController.navigate(Routes.announcementCompose()) },
                onEdit = { navController.navigate(Routes.announcementCompose(it)) },
            )
        }
        composable(Routes.CHATS) {
            ChatsScreen(
                onOpenChat = { navController.navigate(Routes.chatRoom(it)) },
                onNewChat = { navController.navigate(Routes.NEW_CHAT) },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
            )
        }

        // ----------------------------------------------------- issue detail
        composable(
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
        composable(
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
        composable(
            route = Routes.CHAT_ROOM,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME/chat/{chatId}" }),
        ) {
            ChatRoomScreen(onBack = navController::popBackStack)
        }
        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = navController::popBackStack,
                onChatReady = { chatId ->
                    navController.popBackStack()
                    navController.navigate(Routes.chatRoom(chatId))
                },
            )
        }

        // -------------------------------------------------- profile & admin
        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(onBack = navController::popBackStack, showSnackbar = showSnackbar)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                showSnackbar = showSnackbar,
            )
        }
        composable(
            Routes.CHANGE_PASSWORD,
        ) {
            ChangePasswordScreen(onBack = navController::popBackStack, showSnackbar = showSnackbar)
        }
        composable(Routes.ADMIN) {
            AdminScreen(
                onBack = navController::popBackStack,
                onOpenUser = { navController.navigate(Routes.adminUser(it)) },
                onOpenIssue = { navController.navigate(Routes.issueDetail(it)) },
            )
        }
        composable(
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
        composable(
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

