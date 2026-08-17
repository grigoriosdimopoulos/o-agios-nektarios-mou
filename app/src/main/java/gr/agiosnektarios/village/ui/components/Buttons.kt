package gr.agiosnektarios.village.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.ui.theme.Motion
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app's main call to action.
 *
 * Presses squash the button slightly with a spring — the cheapest possible
 * "this thing is physical" cue, and the reason taps feel responsive even while
 * a network call is still in flight.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = Motion.snap(),
        label = "primaryButtonScale",
    )

    // A press dims the fill as well as shrinking it. Scale alone reads as the
    // button moving away; scale plus a darker face reads as it being pushed
    // *in*, which is the difference between an animation and a control.
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = Motion.snap(),
        label = "primaryButtonPress",
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .drawWithContent {
                drawContent()
                // Top-down sheen: lighter across the upper third, so the face
                // is lit rather than filled. Flat colour is what makes a
                // Material button look like a rectangle and an iOS button look
                // like an object.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.5f to Color.Transparent,
                    ),
                )
                if (press > 0f) {
                    drawRect(color = Color.Black.copy(alpha = 0.13f * press))
                }
            },
        // Stays clickable-looking but inert while loading, so a double tap
        // cannot submit a report twice.
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        // A loading button is disabled to the touch but must not *look*
        // disabled: Material's disabled colours are a flat grey fill with
        // grey-on-grey content, which rendered the spinner all but invisible.
        // While loading, keep the live colours and only slightly recede them.
        colors = if (loading) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        // Extracted rather than written inline: a Button's content lambda is
        // `RowScope.() -> Unit`, so RowScope stays an implicit receiver inside
        // any nested Box and captures the AnimatedVisibility call. A plain
        // function body has no such receiver, so the unscoped overload applies.
        PrimaryButtonContent(text = text, loading = loading, icon = icon)
    }
}

@Composable
private fun PrimaryButtonContent(
    text: String,
    loading: Boolean,
    icon: ImageVector?,
) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        AnimatedVisibility(
            visible = !loading,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = Motion.snap(),
        label = "secondaryButtonScale",
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp).scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
