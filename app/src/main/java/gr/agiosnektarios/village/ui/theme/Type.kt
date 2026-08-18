package gr.agiosnektarios.village.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import gr.agiosnektarios.village.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Two typefaces, both carrying real Greek.
 *
 * This used to be `FontFamily.Default` — the system face — defended on the
 * grounds that it is the only thing guaranteed to cover Greek. The constraint
 * was right and the conclusion was wrong: several excellent faces draw Greek
 * properly, and shipping the system face meant a village register, a Euripides
 * line about Kithairon and the phone's own Settings app were all set in
 * exactly the same letters. Type is the one thing that appears on every screen
 * at every size, so it is where "this looks like an app someone made" is won
 * or lost.
 *
 * [Text] is Inter: a neutral, very legible UI face with full Greek and a large
 * x-height, which matters on a screen read outdoors by people who are not
 * twenty. [Display] is Alegreya, a serif whose Greek was drawn by people who
 * care about Greek — it carries titles, the splash quote and nothing else. A
 * serif title over a sans body is what gives a civic register its voice, and
 * it is the contrast the app had none of.
 *
 * All three are variable fonts, so one file covers every weight instead of
 * four, and all are subset to Latin plus Greek: 801 KiB for the three
 * files rather than the 1.7 MiB the full ones cost. Variable weight axes need API 26, which is
 * this app's minimum.
 *
 * SIL Open Font License, both of them; the licences are in /licenses.
 */
@OptIn(ExperimentalTextApi::class)
private val Text = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * The serif carries a real italic file, not a skew.
 *
 * The splash sets the translation in italic, and with no italic bundled
 * Compose synthesises one by slanting the roman. Synthetic italic on a serif
 * is among the most recognisable cheap tells in typography — the stems lean
 * but the letterforms never change, so an `a` stays a roman `a` where a real
 * italic draws a different letter — and it was on the first screen the app
 * shows. Alegreya ships a drawn italic with full Greek; this is it.
 *
 * W400 is declared too. Without it, body text asking for Normal weight
 * silently resolved to the 500 instance, which is a fallback nobody chose.
 */
@OptIn(ExperimentalTextApi::class)
private val Display = FontFamily(
    Font(
        R.font.alegreya_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.alegreya_italic_variable,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.alegreya_italic_variable,
        weight = FontWeight.Medium,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.alegreya_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.alegreya_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.alegreya_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** The serif, for the few places a title should have a voice. */
val VillageDisplayFamily: FontFamily = Display

val VillageTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Numerals inside map badges and counters, centred and tabular-feeling. */
val CounterTextStyle = TextStyle(
    fontFamily = Text,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 14.sp,
    textAlign = TextAlign.Center,
)
