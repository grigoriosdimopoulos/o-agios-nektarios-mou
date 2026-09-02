package gr.agiosnektarios.village.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * True when the app is currently rendering in Greek.
 *
 * Used where a string has to be picked from data rather than from resources —
 * a street name a resident typed, say — which resources cannot do.
 *
 * It lived in the neighbourhood picker until that was deleted, which is a
 * strange home for it and is why removing one feature broke three screens that
 * had nothing to do with it.
 */
@Composable
fun isGreekLocale(): Boolean = LocalConfiguration.current.locales[0].language == "el"
