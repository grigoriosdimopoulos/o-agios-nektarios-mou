package gr.agiosnektarios.village.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.Motion
import kotlinx.coroutines.delay

/** How long the splash holds before the app takes over. */
const val SPLASH_DURATION_MS = 2150L

/**
 * What the village sees for the couple of seconds before its map arrives.
 *
 * The line is Euripides, *Bacchae* 1045 — `λέπας Κιθαιρώνειον`, "the rocky
 * heights of Kithairon" — the mountain this settlement is built on the skirts
 * of. It is a real line from a real play, checked against Perseus rather than
 * recalled from memory: a fabricated classical quotation on a Greek village's
 * own app would be a small act of vandalism.
 *
 * The animation is deliberately slow and single-purpose. The Greek fades up and
 * rises a few pixels, a hairline draws itself outward, then the translation and
 * the attribution follow. Nothing bounces and nothing flies in from off-screen,
 * because a splash that performs is a splash you resent by the fourth launch.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // One integer stage rather than three animatables, so the sequence cannot
    // get out of order if the composition restarts part-way through.
    var stage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(140)
        stage = 1
        delay(440)
        stage = 2
        delay(380)
        stage = 3
    }

    val greekAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = Motion.slow(),
        label = "greekAlpha",
    )
    val greekRise by animateFloatAsState(
        targetValue = if (stage >= 1) 0f else 16f,
        animationSpec = Motion.gentle(),
        label = "greekRise",
    )
    val ruleWidth by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = Motion.slow(),
        label = "ruleWidth",
    )
    val tailAlpha by animateFloatAsState(
        targetValue = if (stage >= 3) 1f else 0f,
        animationSpec = Motion.slow(),
        label = "tailAlpha",
    )

    // A very slow drift on the light behind the text. Barely perceptible, which
    // is the point: it stops the screen reading as a static image without ever
    // asking to be looked at.
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "driftValue",
    )

    val surface = MaterialTheme.colorScheme.surface
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .background(
                Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent),
                    center = Offset(540f, 700f + drift * 60f),
                    radius = 1100f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.splash_quote_greek),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = greekRise.dp)
                    .alpha(greekAlpha),
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 22.dp)
                    .height(1.dp)
                    .width((96 * ruleWidth).dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            )

            Text(
                text = stringResource(R.string.splash_quote_translation),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(tailAlpha),
            )

            Text(
                text = stringResource(R.string.splash_quote_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp).alpha(tailAlpha * 0.9f),
            )
        }
    }
}
