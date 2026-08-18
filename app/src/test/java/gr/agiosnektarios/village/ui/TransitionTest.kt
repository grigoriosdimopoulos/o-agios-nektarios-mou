package gr.agiosnektarios.village.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.theme.VillageTheme
import org.junit.Rule
import org.junit.Test

/**
 * A single frame from the middle of a navigation push.
 *
 * Transitions are the one thing snapshots are supposed to be bad at, but the
 * complaint here is not about timing — it is that the screens *tear*, and a
 * still frame is exactly where tearing shows. This reproduces what the NavHost
 * actually does at a chosen point in the animation: the outgoing screen
 * translated a third of the way out and dimmed, the incoming one sliding over
 * it, both composed at once.
 *
 * What it exposes: a screen whose root is a bare Column paints no background,
 * so the screen underneath shows straight through it.
 */
class TransitionTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    /** Where in the push to freeze. 0 = not started, 1 = arrived. */
    private val progress = 0.45f

    private fun push(dark: Boolean = false, incoming: @Composable () -> Unit) {
        paparazzi.snapshot {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Outgoing: slides a third out, dims under the new screen.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .slideX(-progress / Motion.PARALLAX_FRACTION)
                                .drawWithContent {
                                    drawContent()
                                    // A scrim, not alpha: alpha on an opaque
                                    // screen blends it toward the page colour,
                                    // which in the light theme means it gets
                                    // *lighter* the further back it goes.
                                    drawRect(
                                        Color.Black.copy(
                                            alpha = Motion.UNDERLAY_DIM * progress,
                                        ),
                                    )
                                },
                        ) { UnderlyingScreen() }
                        // Incoming. Opaque throughout: nothing fades a moving
                        // full-screen surface, because a translucent one is a
                        // window onto the screen it is covering.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .slideX(1f - progress),
                        ) { incoming() }
                    }
                }
            }
        }
    }

    /** Translate by a fraction of the container's own width, as the NavHost does. */
    private fun Modifier.slideX(fraction: Float) = layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(IntOffset((placeable.width * fraction).toInt(), 0))
        }
    }

    /** A screen rooted in a bare Column, as several of the app's screens are. */
    @Test fun push_over_transparent_screen() = push { TransparentRootScreen() }

    @Test fun push_over_transparent_screen_dark() =
        push(dark = true) { TransparentRootScreen() }
}

@Composable
private fun UnderlyingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("THE SCREEN BEING LEFT", style = MaterialTheme.typography.titleLarge)
        repeat(16) {
            Text(
                "old screen row $it — this must not be visible through the new one",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TransparentRootScreen() {
    // Mirrors what VillageNavHost.ScreenBackdrop now puts under every
    // destination, so a screen rooted in a bare Column still has a floor.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("THE SCREEN ARRIVING", style = MaterialTheme.typography.titleLarge)
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) { Text("new screen card $it", modifier = Modifier.padding(12.dp)) }
        }
    }
    }
}
