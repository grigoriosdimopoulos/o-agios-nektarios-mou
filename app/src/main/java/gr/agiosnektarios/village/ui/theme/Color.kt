package gr.agiosnektarios.village.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
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

/**
 * The same pine, light enough to be read on a dark surface.
 *
 * [PineDarkTheme] was tuned to carry white as a button fill and then reused as
 * ink, where the requirement runs the other way: it measures 3.89:1 on the
 * dark surface and 2.78:1 on the highest container, so every green label in
 * the dark theme — the household count, "it is over", "taken on by", "clear
 * filters" — was under 4.5:1. Same hue (162°) and saturation, lifted until the
 * worst of those surfaces clears: 4.91:1.
 */
private val PineDarkInk = Color(0xFF3FB18E)
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
/**
 * The alarm red, dark enough to be read as well as filled.
 *
 * It was #D64545, chosen as a button fill. As ink it measured 4.10:1 on the
 * cream page and 4.38:1 on a white card, and the sentence it was carrying is
 * the one that says the app does not telephone anyone for you. As a fill it
 * carried white at 4.38:1, on the fire banner, at twelve points. Both are
 * under 4.5:1. This is the same hue two steps darker: 5.05:1 as ink on cream,
 * 5.38:1 on white, and 5.38:1 carrying white.
 */
private val Alarm = Color(0xFFBE3C3C)
/**
 * The alarm red for the dark theme.
 *
 * It was #FF8A80, a pale pink. Contrast was never the problem — it measured
 * 5.74:1 — the problem was that the fire banner and the map's urgent button
 * became soft pink blocks, so the dark theme said "gentle" where the light
 * theme said "alarm". A signal whose meaning depends on the time of day is not
 * a signal. This carries white at 4.89:1 and stands off the night surface at
 * 3.75:1, which is what a block of colour needs.
 */
private val AlarmDark = Color(0xFFC94040)

/**
 * The alarm red as *text* on a dark surface — the same split as [primaryInk],
 * and for the same reason in the same direction.
 *
 * A red that stands as a block on near-black cannot also be read on it:
 * [AlarmDark] measures 3.75:1 on the background and 2.67:1 on the highest
 * container, and `colorScheme.error` is the colour of every validation message
 * under a text field, the sign-in errors, and the "call first" sentence on the
 * emergency screen. This is the pale red the dark theme used to use as a fill
 * — wrong there, right here, at 5.73:1 on the worst surface.
 */
private val AlarmDarkInk = Color(0xFFFF8A80)

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
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE4E6E4),
    inverseOnSurface = Ink,
    inversePrimary = Pine,
    scrim = Color(0xCC000000),
)

/**
 * The primary green as *text*, which is not the same colour as the primary
 * green as a *fill*.
 *
 * Material has one `primary` role and both uses read it. On the light theme
 * that is harmless — Pine measures 5.65:1 on the cream page. On the dark
 * theme a fill that carries white is by construction too dark to be read on
 * near-black, so the two requirements pull apart and one token cannot serve
 * both.
 */
val ColorScheme.primaryInk: Color
    get() = if (surface.luminance() < 0.5f) PineDarkInk else Pine

/** The error colour as text rather than as a block. See [AlarmDarkInk]. */
val ColorScheme.errorInk: Color
    get() = if (surface.luminance() < 0.5f) AlarmDarkInk else Alarm

/**
 * The line that says "this is a control", as opposed to the hairline that
 * separates one surface from another.
 *
 * WCAG 1.4.11 asks 3:1 of a control's visual boundary. `outlineVariant`
 * measured 1.27:1 against the cream page, which is what the twenty-one filter
 * chips were drawn with — for anyone who does not already know they are
 * buttons, they were not buttons until the words were read. `outline` itself
 * is only 2.04:1 in the light theme; in the dark theme it already clears, so
 * only the light value moves.
 */
val ColorScheme.controlOutline: Color
    get() = if (surface.luminance() < 0.5f) outline else Color(0xFF8A8378)

/** Semantic accents that are not part of the Material scheme. */
object VillageAccents {
    val upvote = Color(0xFF2F7D32)
    val downvote = Color(0xFFB4453D)

    private val upvoteDark = Color(0xFF5CBF60)
    private val downvoteDark = Color(0xFFE8756B)

    /**
     * The vote colours, which are ink on a dark surface and a fill on a light
     * one, so they need the same split as [ColorScheme.primaryInk].
     *
     * The light values measure 3.58:1 and 3.37:1 against the night background,
     * under the 4.5:1 the count beside them needs; the dark values clear at
     * 7.93:1 and 6.28:1.
     */
    val ColorScheme.upvoteInk: Color
        get() = if (surface.luminance() < 0.5f) upvoteDark else upvote

    val ColorScheme.downvoteInk: Color
        get() = if (surface.luminance() < 0.5f) downvoteDark else downvote
    val mapWater = Color(0xFF9FC7DE)
    val badgeGlow = Color(0x33F2B441)
}
