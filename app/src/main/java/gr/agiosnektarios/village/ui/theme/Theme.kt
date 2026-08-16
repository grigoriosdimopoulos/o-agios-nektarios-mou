package gr.agiosnektarios.village.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import gr.agiosnektarios.village.data.settings.ThemeMode

/** Generous, friendly radii — the "playful" half of the brief, applied structurally. */
val VillageShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Whether the app is currently dark, as [VillageTheme] decided it.
 *
 * Anything that has to match the theme but is not drawn by Material — the map,
 * which loads a whole different tile style — must read this rather than ask
 * `isSystemInDarkTheme()` again. Asking again is what left the map dark while
 * the rest of the app went light: the resident had chosen Light, the phone was
 * in dark mode, and the two questions have different answers.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun VillageTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Dynamic colour is deliberately not used: the village palette is the
    // product's identity, and a wallpaper-derived scheme would break the
    // category pin colours the map relies on.
    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = VillageTypography,
            shapes = VillageShapes,
            content = content,
        )
    }
}
