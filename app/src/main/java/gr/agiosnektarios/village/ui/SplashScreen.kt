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
import gr.agiosnektarios.village.BuildConfig
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.Motion
import gr.agiosnektarios.village.ui.theme.VillageDisplayFamily
import kotlinx.coroutines.delay

/** How long the splash holds before the app takes over. */
const val SPLASH_DURATION_MS = 2150L

/**
 * What the village sees for the couple of seconds before its map arrives.
 *
 * The line is Hesiod, *Theogony* 129 — `οὔρεα μακρά, θεῶν χαρίεντας ἐναύλους`,
 * "the tall mountains, the gracious haunts of the gods". It is Earth herself
 * bearing the mountains, and the couplet finishes at 130 with `οὔρεα
 * βησσήεντα`, the wooded mountains where the Nymphs live — which is what this
 * settlement sits in the middle of.
 *
 * It replaced a line from Euripides that named Kithairon directly. Naming the
 * mountain was the obvious choice and the weaker one: the Bacchae line is
 * about a place of madness and dismemberment, and it said nothing about the
 * forest, the land, or why anyone would look after either.
 *
 * Verified against the canonical Perseus text (tlg0020.tlg001), not recalled
 * from memory: a fabricated classical quotation on a Greek village's own app
 * would be a small act of vandalism. The full line begins `γείνατο δ'` — "and
 * she bore" — which is dropped here so the fragment stands as a phrase rather
 * than as a sentence missing its subject.
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
        // No opening delay. Every alpha on this screen is driven from `stage`,
        // so while it is 0 the screen is genuinely blank — an empty tinted
        // rectangle is the *first* thing the app shows, which is the worst
        // possible place for it. The fade still reads as a fade because
        // animateFloatAsState starts at 0 and springs up from there.
        stage = 1
        delay(560)
        stage = 2
        delay(380)
        stage = 3
    }

    SplashContent(stage = stage, modifier = modifier)
}

/**
 * The splash at an explicit point in its sequence.
 *
 * Split out from the timed wrapper purely so it can be rendered and looked at:
 * a screenshot of [SplashScreen] catches frame zero, which shows nothing.
 */
@Composable
internal fun SplashContent(
    stage: Int,
    modifier: Modifier = Modifier,
    /**
     * Which build to print, or null for none.
     *
     * A parameter rather than a direct read of [BuildConfig], because the
     * default reads the git-derived version — commit count and short SHA — and
     * a snapshot test that renders it produces a golden that is out of date the
     * instant it is committed. It cannot be fixed by re-recording either: the
     * SHA of a commit is not known before the commit exists, so the golden and
     * the build can never agree and CI's snapshot verification would fail on
     * every push. The committed golden had in fact been stale for two commits
     * when this was noticed.
     */
    version: String? = BuildConfig.VERSION_NAME.takeIf {
        BuildConfig.APPLICATION_ID.endsWith(".debug")
    },
) {
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
                // The serif, explicitly. This line is not interface text —
                // it is a quotation, and it should not look like a button
                // label that happens to be large.
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = VillageDisplayFamily,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
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
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = VillageDisplayFamily,
                    fontStyle = FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(tailAlpha),
            )

            // Which build this is, on the screen you cannot miss.
            //
            // "Is this the same APK?" has been asked twice and answered wrong
            // once. The version lives in Settings > About, which is four taps
            // away and no use at all when the question is whether the install
            // even took. On a build handed to a tester it belongs where the
            // app opens. Suppressed for a real release, where a build number
            // over a Euripides quotation would be graffiti.
            if (version != null) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp).alpha(tailAlpha),
                )
            }

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
