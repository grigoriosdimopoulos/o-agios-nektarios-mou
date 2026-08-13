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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.R

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
    val transition = rememberInfiniteTransition(label = "authAmbience")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Reverse),
        label = "drift",
    )
    val counterDrift by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "counterDrift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientBlob(
            color = MaterialTheme.colorScheme.primary,
            alignmentBias = drift,
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
                    color = MaterialTheme.colorScheme.primary,
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
            .size(260.dp)
            // A heavy blur turns a flat circle into soft light; cheaper and far
            // more forgiving across densities than a hand-tuned radial gradient.
            .blur(90.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                ),
            ),
    )
}
