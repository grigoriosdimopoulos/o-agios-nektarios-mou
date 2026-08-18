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
import androidx.compose.ui.Alignment
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import gr.agiosnektarios.village.ui.theme.Motion
import kotlinx.coroutines.delay

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
    // The splash is held for its own duration *and* until the session resolves,
    // whichever is longer. Resolving fast should not flash the quote for 200 ms,
    // and resolving slowly should not show a spinner after it.
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        splashDone = true
    }
    val ready = splashDone && session != SessionState.Loading

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Crossfaded rather than swapped: the village should appear *through*
        // the quote, not replace it on a frame boundary.
        Crossfade(
            targetState = ready,
            animationSpec = tween(520, easing = Motion.emphasized),
            label = "splash",
        ) { showApp ->
            if (!showApp) {
                SplashScreen()
            } else {
                VillageContent(session, deepLink, onDeepLinkHandled)
            }
        }
    }
}

@Composable
private fun VillageContent(
    session: SessionState,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
        // The map draws its own edge-to-edge surface, so content insets are
        // consumed per-screen rather than globally here.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // All zeros today — no top bar, no bottom bar slot, and content insets
        // are consumed per screen — but applied rather than discarded so it
        // stays correct if a slot is ever filled. Critically it can no longer
        // *change*: nothing occupies a Scaffold slot, so this padding has
        // nothing to animate and cannot resize the graph mid-transition.
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

            // The bar floats over the content instead of occupying a slot in
            // the Scaffold.
            //
            // In the slot, its height was part of the layout: AnimatedVisibility
            // kept the bar measured for the whole of its exit and then removed
            // it in one step, which changed Scaffold's content padding from
            // 80dp to 0 instantly. Every screen inside the NavHost — the one
            // arriving and the one leaving — grew 80dp taller in a single
            // frame, halfway through a 380ms push. That was the tearing.
            //
            // Overlaid, the bar's height leaves the layout entirely. Screens
            // that need to scroll clear of it ask for BottomBarDefaults, which
            // is a constant: a tab always has the bar, a detail screen never
            // does, so nothing resizes because of it, ever.
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                VillageBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination -> navController.switchTab(destination) },
                )
            }
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
