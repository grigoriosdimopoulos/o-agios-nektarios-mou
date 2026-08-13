package gr.agiosnektarios.village.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Profile picture, or a coloured monogram when there is none.
 *
 * The fallback gradient is derived from the user id, so a given resident always
 * gets the same colours everywhere in the app — that consistency is what makes
 * an initials avatar read as an identity rather than as a missing image.
 */
@Composable
fun Avatar(
    photoUrl: String,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    seed: String = initials,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(monogramBrush(seed)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials.take(2).ifBlank { "?" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFF1F6F5C) to Color(0xFF3F9A82),
    Color(0xFFE2724B) to Color(0xFFF2A05C),
    Color(0xFF4A6FA5) to Color(0xFF6E9AD1),
    Color(0xFF8B5E9C) to Color(0xFFB07FC4),
    Color(0xFFB4762C) to Color(0xFFE0A422),
    Color(0xFF2F7D32) to Color(0xFF61B065),
)

private fun monogramBrush(seed: String): Brush {
    val (start, end) = avatarPalette[(seed.hashCode().absoluteValue) % avatarPalette.size]
    return Brush.linearGradient(listOf(start, end))
}
