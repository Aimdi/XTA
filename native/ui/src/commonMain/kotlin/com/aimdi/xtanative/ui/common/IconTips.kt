package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/**
 * [content], an icon button, with [label] shown beside the pointer while it
 * rests on it: on the desktop a mouse has no long press to ask what an icon
 * is. The label is the one the button already gives screen readers. Nothing
 * more on Android.
 */
@Composable
expect fun IconTip(label: String, content: @Composable () -> Unit)
