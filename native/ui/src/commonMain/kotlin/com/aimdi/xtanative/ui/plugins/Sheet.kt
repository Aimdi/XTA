package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.LocalSheetCloser
import com.aimdi.xtanative.ui.home.movedByKeys

/**
 * A plugin's bottom sheet as the app's own are drawn, in place of Material's
 * `ModalBottomSheet`: [sheetState] opens it as [rememberPluginSheetState]
 * does (all the way on the desktop), and on the desktop the sheet takes the
 * keys as it opens, so ↑ and ↓ go along its rows (or move its list marked
 * [keyScrolled]: Page Up and Down, Space, Home and End too), and Esc closes
 * it. A button in it that closes it does so with [rememberSheetClose], so the
 * sheet slides away as a swipe or Esc has it go, rather than vanishing.
 * Without [dragHandle] it has no handle (a sheet whose own header starts it).
 * Plugin API 16.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberPluginSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) = KeyedSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    sheetState = sheetState,
    containerColor = containerColor,
    dragHandle = dragHandle,
    content = content,
)

/**
 * How a button in a [PluginSheet] closes it: `close { work }` slides the
 * sheet away, runs its `onDismissRequest`, then the work, once (a second
 * press while it slides does nothing). Read it inside the sheet's content;
 * outside a sheet, or while motion is reduced, the work is done at once.
 * Plugin API 16.
 */
@Composable
fun rememberSheetClose(): (then: () -> Unit) -> Unit {
    val closer = LocalSheetCloser.current
    return remember(closer) { { then -> closer.close(then) } }
}

/**
 * [list] moved by the desktop's reading keys (↑ ↓, Page Up and Down, Space,
 * Home, End): in a [PluginSheet] while it is open, on a screen while the
 * pointer is over it or it is the first shown. Nothing off the desktop.
 * Plugin API 16.
 */
@Composable
fun Modifier.keyScrolled(list: LazyListState): Modifier = movedByKeys(list)

/** A column scrolled as a whole by [scroll] moved by the desktop's keys ([keyScrolled]); put it before its `verticalScroll`. Plugin API 16. */
@Composable
fun Modifier.keyScrolled(scroll: ScrollState): Modifier = movedByKeys(scroll)
