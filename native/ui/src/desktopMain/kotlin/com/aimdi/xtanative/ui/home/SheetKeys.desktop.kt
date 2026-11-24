package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal actual fun Modifier.sheetKeys(keys: SheetKeys): Modifier {
    if ((LocalDesktopActions.current ?: LocalDesktopActionsHost.current.actions) == null) return this
    val focus = remember { FocusRequester() }
    // Whether the sheet, or something in it, has the keys.
    val held = remember { booleanArrayOf(false) }
    val scope = rememberCoroutineScope()
    val line = with(LocalDensity.current) { LINE.toPx() }
    LaunchedEffect(focus) {
        delay(SHEET_SETTLE_MS)
        if (!held[0]) runCatching { focus.requestFocus() }
    }
    // After what has the focus inside: a field types the key, a slider moves with it; only a key left unused moves the list.
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val action = DesktopKeys.sheetScroll(deskKey(event.key), event.isShiftPressed, event.isCtrlPressed, event.isAltPressed)
        if (action == DeskAction.NONE) return@onKeyEvent false
        when (val list = keys.list()) {
            is LazyListState -> {
                scope.launch { list.moveFor(action, line) }
                true
            }
            is ScrollState -> DesktopKeys.scrolledTo(action, list.value, list.maxValue, list.viewportSize, line)?.let { target ->
                scope.launch { list.moveTo(action, target) }
                true
            } ?: false
            // No list: ↑ and ↓ go along the sheet's rows, as Tab does, the row reached lit.
            else -> when (action) {
                DeskAction.LINE_DOWN -> keys.focus?.moveFocus(FocusDirection.Next) ?: false
                DeskAction.LINE_UP -> keys.focus?.moveFocus(FocusDirection.Previous) ?: false
                else -> false
            }
        }
    }.onFocusChanged { held[0] = it.hasFocus }.focusRequester(focus).focusable()
}

/** Long enough for a sheet to be laid out, and for a field it focuses as it opens to have done so. */
private const val SHEET_SETTLE_MS = 120L
