package com.aimdi.xtanative.ui.post

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Where a key takes the focus among a menu's rows ([MenuKeys]). */
internal enum class MenuMove { NEXT, PREVIOUS, FIRST, LAST }

/**
 * The keys of a menu of rows at the pointer ([PostMenuSurface]), as a
 * desktop's context menus have them: ↓ and ↑ to the next row or the one
 * before, round from the last to the first and back, Home and End to the
 * first and the last (Page Up and Page Down too). Enter and Space press the
 * row with the focus, as any button; Esc closes the menu.
 */
internal object MenuKeys {
    /** The move [key] makes, with no modifier held; null for any other key, which goes on. */
    fun move(key: Key, modified: Boolean): MenuMove? = when {
        modified -> null
        key == Key.DirectionDown -> MenuMove.NEXT
        key == Key.DirectionUp -> MenuMove.PREVIOUS
        key == Key.MoveHome || key == Key.PageUp -> MenuMove.FIRST
        key == Key.MoveEnd || key == Key.PageDown -> MenuMove.LAST
        else -> null
    }

    /**
     * [event] on the menu, its focus moved with [focus]: true when it was a
     * move. Nothing focused yet (a menu the mouse opened), ↓ goes to the
     * first row and ↑ to the last. The first and last are the ones Tab's
     * order reaches first from either end, once nothing in the menu has the
     * focus.
     */
    fun handle(event: KeyEvent, focus: FocusManager): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val modified = event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed
        return when (move(event.key, modified)) {
            MenuMove.NEXT -> focus.moveFocus(FocusDirection.Next)
            MenuMove.PREVIOUS -> focus.moveFocus(FocusDirection.Previous)
            MenuMove.FIRST -> {
                focus.clearFocus()
                focus.moveFocus(FocusDirection.Next)
            }
            MenuMove.LAST -> {
                focus.clearFocus()
                focus.moveFocus(FocusDirection.Previous)
            }
            null -> false
        }
    }
}
