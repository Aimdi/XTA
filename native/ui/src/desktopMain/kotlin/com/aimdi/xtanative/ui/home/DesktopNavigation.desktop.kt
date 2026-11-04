package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

/**
 * The window's keys and mouse on the way to the screens: the content's frame
 * keeps the focus whenever nothing inside it has it, so the keys always
 * arrive here first.
 */
@Composable
actual fun DesktopNavigation(nav: NavHostController, content: @Composable () -> Unit) {
    val actions = remember { DesktopActions() }
    val focus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var focusInside by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun act(action: DeskAction): Boolean = when (action) {
        DeskAction.LEAVE_FIELD -> {
            focus.requestFocus()
            true
        }
        DeskAction.CLOSE -> {
            actions.close()
            true
        }
        DeskAction.BACK -> nav.popBackStack()
        DeskAction.REFRESH -> actions.refresh()
        DeskAction.PREVIOUS_PAGE -> actions.turn(-1)
        DeskAction.NEXT_PAGE -> actions.turn(1)
        DeskAction.NONE -> false
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val canGoBack = nav.previousBackStackEntry != null
        return act(DesktopKeys.action(deskKey(event.key), event.isAltPressed, event.isCtrlPressed, focusInside, canGoBack, rtl, actions.somethingOpen()))
    }

    CompositionLocalProvider(LocalDesktopActions provides actions) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(nav) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isBackPressed) {
                                act(DesktopKeys.back(nav.previousBackStackEntry != null, actions.somethingOpen()))
                            }
                        }
                    }
                }
                .onPreviewKeyEvent(::onKey)
                .onFocusChanged { state ->
                    hasFocus = state.hasFocus
                    focusInside = state.hasFocus && !state.isFocused
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            content()
        }
    }
    // The frame takes the focus when nothing has it: at the start, and once the screen that had it is gone.
    // Only after the screens change, so a sheet's own field is never robbed of it.
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect {
            delay(FOCUS_SETTLE_MS)
            if (!hasFocus) runCatching { focus.requestFocus() }
        }
    }
}

/** Longer than a screen's slide, by which time a screen that focuses a field has done so. */
private const val FOCUS_SETTLE_MS = 400L

private fun deskKey(key: Key): DeskKey = when (key) {
    Key.Escape -> DeskKey.ESCAPE
    Key.DirectionLeft -> DeskKey.LEFT
    Key.DirectionRight -> DeskKey.RIGHT
    Key.F5 -> DeskKey.F5
    Key.R -> DeskKey.R
    Key.Back -> DeskKey.BACK
    else -> DeskKey.OTHER
}
