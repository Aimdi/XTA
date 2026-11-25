package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [content], an icon button, with [label] shown beside the pointer while it
 * rests on it: on the desktop a mouse has no long press to ask what an icon
 * is. The label is the one the button already gives screen readers. Nothing
 * more on Android.
 */
@Composable
expect fun IconTip(label: String, content: @Composable () -> Unit)

/**
 * The ring [IconTip] draws round an icon button the keyboard moved to, for
 * a control drawn without one (a round play button, a toggle): on the
 * desktop only, and never for a focus a click gave. Nothing on Android.
 */
@Composable
internal expect fun Modifier.keyboardFocusRing(): Modifier
