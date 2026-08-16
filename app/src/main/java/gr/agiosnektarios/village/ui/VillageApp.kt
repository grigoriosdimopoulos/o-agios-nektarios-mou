package gr.agiosnektarios.village.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.ui.components.LoadingState
import gr.agiosnektarios.village.ui.navigation.Routes
import gr.agiosnektarios.village.ui.navigation.TopLevelDestination
import gr.agiosnektarios.village.ui.navigation.VillageBottomBar
import gr.agiosnektarios.village.ui.navigation.VillageNavHost
import kotlinx.coroutines.launch

/**
 * The root of the UI.
 *
 * Authentication is handled by swapping the whole navigation graph rather than
 * by guarding individual screens: a signed-out user has no back stack into the
 * village, and signing out cannot leave a stale screen behind.
 */
@Composable
fun VillageApp(
    session: SessionState,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (session) {
            SessionState.Loading -> LoadingState()
            SessionState.SignedOut -> AuthFlow()
            is SessionState.ProfileIncomplete -> AuthFlow(
                startDestination = Routes.COMPLETE_PROFILE,
            )
            // A deep link is only navigable once the village graph exists, so a
            // notification tapped while signed out survives until sign-in
            // completes rather than being discarded.
            is SessionState.SignedIn -> SignedInApp(
                session = session,
                deepLink = deepLink,
                onDeepLinkHandled = onDeepLinkHandled,
            )
        }
    }
}

@Composable
private fun AuthFlow(startDestination: String = Routes.SIGN_IN) {
    val navController = rememberNavController()
    VillageNavHost(
        navController = navController,
        startDestination = startDestination,
        signedIn = false,
        profile = null,
        showSnackbar = {},
    )
}

@Composable
private fun SignedInApp(
    session: SessionState.SignedIn,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    NotificationPermissionRequest()

    LaunchedEffect(deepLink) {
        // The graph declares matching deep links, so handing the URI over is
        // enough. Marking it handled matters: without it, the same notification
        // would be re-navigated on the next recomposition that changes state.
        if (!deepLink.isNullOrBlank()) {
            runCatching { navController.navigate(Uri.parse(deepLink)) }
            onDeepLinkHandled()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.fromRoute(currentRoute) != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // The bar slides away on detail screens instead of disappearing, so
            // the transition reads as "going deeper" rather than as a redraw.
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                VillageBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination -> navController.switchTab(destination) },
                )
            }
        },
        // The map draws its own edge-to-edge surface, so content insets are
        // consumed per-screen rather than globally here.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            VillageNavHost(
                navController = navController,
                startDestination = Routes.MAP,
                signedIn = true,
                profile = session.profile,
                // Dispatched rather than awaited. SnackbarHostState.showSnackbar
                // suspends until the snackbar goes away — several seconds — and
                // every screen that announced a save and then navigated was
                // waiting out that delay before leaving, which read as the
                // window refusing to close. The host lives above the NavHost,
                // so the message outlives the screen that raised it.
                showSnackbar = { message ->
                    snackbarScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                },
            )
        }
    }
}

/** Tab switching that preserves each tab's own back stack and scroll position. */
private fun NavHostController.switchTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Asks for the notification permission once, on first entry to the signed-in
 * app — the point at which the request is obviously about village updates and
 * not an unexplained prompt on launch.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Declining is fine; in-app activity still works. */ },
    )
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
