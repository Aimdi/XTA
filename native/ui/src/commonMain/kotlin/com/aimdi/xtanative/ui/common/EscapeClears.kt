package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.aimdi.xtanative.ui.home.LocalDesktopActions

/** What Esc does in a search field: empties it while it holds words, else leaves it as before. */
object EscapeClears {
    /** Whether Esc empties a field holding [text] rather than leaving it. */
    fun clears(text: String): Boolean = text.isNotEmpty()

    /** Esc in a field holding [text], [focused] or not: true when it emptied it ([clear]), and keeps the keyboard there. */
    fun escape(focused: Boolean, text: String, clear: () -> Unit): Boolean = (focused && clears(text)).also { if (it) clear() }
}

/**
 * A search field's Esc, as a desktop's search entries have it: the first Esc
 * empties the field ([clear]) and keeps the keyboard in it; the next, with
 * nothing left to empty, leaves the field or closes what it is in, as Esc
 * did before. [text] is what the field holds. The window's own Esc comes
 * first ([com.aimdi.xtanative.ui.home.DesktopActions]), so the field asks
 * it to wait while it has words; in a dialog, which the window's keys do not
 * reach, the field takes the key itself.
 */
@Composable
internal fun Modifier.escapeClears(text: String, clear: () -> Unit): Modifier {
    val actions = LocalDesktopActions.current
    val latestText by rememberUpdatedState(text)
    val latestClear by rememberUpdatedState(clear)
    var focused by remember { mutableStateOf(false) }
    if (actions != null) {
        DisposableEffect(actions) {
            val owner = Any()
            actions.addFieldEscape(owner) { EscapeClears.escape(focused, latestText, latestClear) }
            onDispose { actions.removeFieldEscape(owner) }
        }
    }
    return onFocusChanged { focused = it.hasFocus }.onPreviewKeyEvent { event ->
        val esc = event.key == Key.Escape && !event.isAltPressed && !event.isCtrlPressed && !event.isShiftPressed
        when {
            !esc || !EscapeClears.clears(latestText) -> false
            else -> {
                if (event.type == KeyEventType.KeyDown) latestClear()
                true
            }
        }
    }
}
