package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where a post's ⋯ menu shows its entries ([content]): on a phone, a sheet
 * rising from the foot of the screen; on the desktop, a menu beside the
 * pointer, as a desktop opens a thing's menu. Both close at [onDismiss]: a
 * swipe or a tap outside, a click outside or Escape.
 */
@Composable
internal expect fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)

/**
 * How a menu's row is laid out: a phone sheet's rows are tall for a finger;
 * the desktop's pointer menu ([PostMenuSurface]) lays its own out denser, as
 * a desktop's menus are.
 */
internal data class MenuRowMetrics(val minHeight: Dp, val padding: Dp, val gap: Dp, val iconSize: Dp, val verticalPadding: Dp = 0.dp) {
    companion object {
        val Sheet = MenuRowMetrics(minHeight = 52.dp, padding = 24.dp, gap = 24.dp, iconSize = 24.dp)
        val Pointer = MenuRowMetrics(minHeight = 36.dp, padding = 16.dp, gap = 12.dp, iconSize = 20.dp, verticalPadding = 6.dp)
    }
}

/** The [MenuRowMetrics] the rows below lay themselves out with; a sheet's unless a pointer menu says otherwise. */
internal val LocalMenuRowMetrics = staticCompositionLocalOf { MenuRowMetrics.Sheet }
