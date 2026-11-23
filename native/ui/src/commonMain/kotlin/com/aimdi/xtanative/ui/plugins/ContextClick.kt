package com.aimdi.xtanative.ui.plugins

import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.common.onSecondaryClick

/**
 * [action] at a right-click on this, as a desktop opens a thing's menu:
 * give a card the same action its long press has (its post's menu,
 * [PluginHost.openPostMenu]), since a mouse's long press does not reach a
 * long click on the desktop. Nothing on a touch screen, which has no such
 * button; the card's own taps and long press are left as they are.
 * Plugin API 15.
 */
fun Modifier.onContextClick(action: () -> Unit): Modifier = onSecondaryClick(action)
