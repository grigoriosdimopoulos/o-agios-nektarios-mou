package gr.agiosnektarios.village.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The village palette: pine green for civic trust, terracotta for the rooftops,
 * olive gold for warmth. Deliberately not the stock Material purple — this app
 * should feel like *this* place.
 */
private val Pine = Color(0xFF1F6F5C)
private val PineLight = Color(0xFF3F9A82)

/**
 * The dark theme's primary.
 *
 * PineLight was used here, and white-on-PineLight measures 3.4:1 while the
 * scheme's own onPrimary measured 3.85:1 — both under the 4.5:1 an ordinary
 * button needs. The same control was therefore confident in the light theme
 * (6.0:1) and washed out in the dark one. This is dark enough to carry white
 * at 4.7:1 while staying recognisably the same green; PineLight stays on as
 * inversePrimary and for accents, where it is never a text background.
 */
private val PineDarkTheme = Color(0xFF2E8168)
private val PineDark = Color(0xFF0E4A3C)
private val Terracotta = Color(0xFFE2724B)
private val TerracottaLight = Color(0xFFFF9C72)
private val Olive = Color(0xFFF2B441)
private val OliveDeep = Color(0xFF9A6C00)
private val Cream = Color(0xFFFBF7F2)
private val CreamDim = Color(0xFFF1EAE0)
private val Ink = Color(0xFF17211E)
private val Slate = Color(0xFF5A6660)
private val NightBase = Color(0xFF10151A)
private val NightSurface = Color(0xFF171E24)
private val NightSurfaceHigh = Color(0xFF1F282F)
private val Alarm = Color(0xFFD64545)
private val AlarmDark = Color(0xFFFF8A80)

val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE7D9),
    onPrimaryContainer = Color(0xFF04241C),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCD),
    onSecondaryContainer = Color(0xFF3B1105),
    tertiary = OliveDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3A3),
    onTertiaryContainer = Color(0xFF2A1D00),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CreamDim,
    onSurfaceVariant = Slate,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFAF6),
    surfaceContainer = Color(0xFFF6F0E8),
    surfaceContainerHigh = Color(0xFFF0E9DF),
    surfaceContainerHighest = Color(0xFFE9E1D6),
    outline = Color(0xFFB6AFA4),
    outlineVariant = Color(0xFFDCD4C8),
    error = Alarm,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410004),
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    inversePrimary = PineLight,
    scrim = Color(0x99000000),
)

val DarkColors = darkColorScheme(
    primary = PineDarkTheme,
    onPrimary = Color.White,
    primaryContainer = PineDark,
    onPrimaryContainer = Color(0xFFBFE7D9),
    secondary = TerracottaLight,
    onSecondary = Color(0xFF5A1C08),
    secondaryContainer = Color(0xFF7A3418),
    onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Olive,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4400),
    onTertiaryContainer = Color(0xFFFFE3A3),
    background = NightBase,
    onBackground = Color(0xFFE4E6E4),
    surface = NightBase,
    onSurface = Color(0xFFE4E6E4),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFB6C0BA),
    surfaceContainerLowest = Color(0xFF0B1014),
    surfaceContainerLow = NightSurface,
    surfaceContainer = Color(0xFF1B232A),
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF283239),
    outline = Color(0xFF6C7A72),
    outlineVariant = Color(0xFF3A4640),
    error = AlarmDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE4E6E4),
    inverseOnSurface = Ink,
    inversePrimary = Pine,
    scrim = Color(0xCC000000),
)

/** Semantic accents that are not part of the Material scheme. */
object VillageAccents {
    val upvote = Color(0xFF2F7D32)
    val downvote = Color(0xFFB4453D)
    val mapWater = Color(0xFF9FC7DE)
    val badgeGlow = Color(0x33F2B441)
}
