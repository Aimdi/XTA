package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * Where a post's ⋯ menu shows its entries ([content]): on a phone, a sheet
 * rising from the foot of the screen; on the desktop, a menu beside the
 * pointer, as a desktop opens a thing's menu. Both close at [onDismiss]: a
 * swipe or a tap outside, a click outside or Escape.
 */
@Composable
internal expect fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)
