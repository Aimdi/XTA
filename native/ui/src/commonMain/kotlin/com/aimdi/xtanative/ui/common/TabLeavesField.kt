package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalFocusManager
import com.aimdi.xtanative.ui.home.DeskKey
import com.aimdi.xtanative.ui.home.DesktopKeys
import com.aimdi.xtanative.ui.home.TabStep

/**
 * Tab and Shift+Tab go on from a field of several lines to the next or the
 * previous control, as from a browser's: Compose types a tab into such a
 * field, which holds the keyboard there, and a tab in a note or a list of
 * names is never what is meant. Ctrl+Tab and Alt+Tab are left alone.
 */
@Composable
fun Modifier.tabLeavesField(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        // The key, and the tab character it types after it, which would land in the field.
        val tab = event.key == Key.Tab || event.utf16CodePoint == '\t'.code
        val step = DesktopKeys.tabStep(if (tab) DeskKey.TAB else DeskKey.OTHER, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed)
            ?: return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(if (step == TabStep.FORWARD) FocusDirection.Next else FocusDirection.Previous)
        true
    }
}
