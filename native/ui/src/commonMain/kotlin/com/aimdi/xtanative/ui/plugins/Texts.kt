package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.currentLocale

/** [text] in the reader's language, filled with [args]: what stringResource is to the app's own text. */
@Composable
fun pluginString(text: PluginText, vararg args: Any): String = text.get(currentLanguage(), *args)

/** [plural] for [count] in the reader's language, filled with [args]. */
@Composable
fun pluginPlural(plural: PluginPlural, count: Int, vararg args: Any): String = plural.get(count, currentLanguage(), *args)

/** The language the screens are drawn in, as a BCP 47 tag ("en", "de", "pt-BR"); it follows the reader's setting as it changes. */
@Composable
fun currentLanguage(): String = currentLocale().toLanguageTag()
