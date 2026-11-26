package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.theme.edgeColor

/**
 * The hairline the app draws round a card, a quoted post, a chip or a field,
 * in the reader's theme (seen on the page and on a lit row): a plugin's own
 * card bordered with it matches the app's, where Material's
 * `outlineVariant` is fainter or stronger by theme. Plugin API 16.
 */
@Composable
fun pluginEdgeColor(): Color = edgeColor()
