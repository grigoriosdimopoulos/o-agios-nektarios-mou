package gr.agiosnektarios.village.ui.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.ui.theme.primaryInk
import gr.agiosnektarios.village.ui.theme.reducedMotion

/**
 * Shared chrome for the authentication screens: a slow-drifting pair of colour
 * blobs behind a scrollable form.
 *
 * The motion is intentionally very slow (12 s and 9 s cycles). Sign-in is a
 * screen people see often and stare at while typing, so the ambience has to be
 * something you notice once and never again — anything faster becomes an
 * irritation by the third visit.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val still = reducedMotion()
    val transition = rememberInfiniteTransition(label = "authAmbience")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Reverse),
        label = "drift",
    )
    val counterDriftValue by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "counterDrift",
    )
    // Held at the midpoint rather than at an end, so the two blobs sit where
    // they were designed to sit rather than stacked in a corner.
    val driftAt = if (still) 0.5f else drift
    val counterDrift = if (still) 0.5f else counterDriftValue

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientBlob(
            color = MaterialTheme.colorScheme.primaryInk,
            alignmentBias = driftAt,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        AmbientBlob(
            color = MaterialTheme.colorScheme.secondary,
            alignmentBias = counterDrift,
            modifier = Modifier.align(Alignment.BottomStart),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = if (onBack != null) 0.dp else 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primaryInk,
                )
                Text(text = title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            content()
        }
    }
}

@Composable
private fun AmbientBlob(
    color: androidx.compose.ui.graphics.Color,
    alignmentBias: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(
                top = (40 + alignmentBias * 60).dp,
                end = (alignmentBias * 40).dp,
                start = (alignmentBias * 40).dp,
                bottom = (alignmentBias * 60).dp,
            )
            .size(340.dp)
            // No blur here, deliberately.
            //
            // It used to run the circle through a 90dp `blur`, whose default
            // edge treatment clips the result to the composable's rectangle.
            // A 260dp circle blurred by 90dp spills well past that box, so the
            // spill was cut off square and the sign-in screen — the first thing
            // anyone sees — carried a hard-edged grey slab across its lower
            // half. It drifted, so the slab drifted with it.
            //
            // Paparazzi renders `blur` as a no-op, so no golden could ever have
            // shown this. The radial gradient alone was already what every
            // device below API 31 saw; it is soft by construction, identical on
            // every device, and a snapshot test can actually see it.
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = 0.26f),
                        color.copy(alpha = 0.10f),
                        color.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}
