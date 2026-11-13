package com.aimdi.xtanative.ui.plugins

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An entry a plugin adds to the post sheets ([XtaPlugin.postSheetActions]),
 * as XTA's share sheet offers "Save to Karakeep": its [icon], its [label] in a
 * language, and what it does with the post, [run] once the sheet has closed,
 * beyond the screen that opened it. It says how that went itself
 * ([PluginHost.tell]).
 */
class PluginPostAction(
    val icon: ImageVector,
    val label: (language: String) -> String,
    val run: suspend (PluginSharedPost) -> Unit,
)

/**
 * An entry a plugin adds to a cashtag's sheet ([XtaPlugin.cashtagActions]),
 * as XTA's Stocks adds Add to watchlist: its [icon], its [label] in a
 * language, and what it does, [run] once the sheet has closed, beyond the
 * screen that opened it.
 */
class PluginCashtagAction(
    val icon: ImageVector,
    val label: (language: String) -> String,
    val run: suspend () -> Unit,
)
