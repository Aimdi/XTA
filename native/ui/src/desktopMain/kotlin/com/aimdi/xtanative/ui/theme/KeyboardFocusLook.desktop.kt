package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * Whether the focus the app shows came from the keyboard. Compose's desktop
 * buttons take the focus on a mouse click too, so a lit focus would stay on
 * every button clicked until something else took it; desktops light the
 * focus only once the keyboard moves it, as GTK's focus-visible does.
 *
 * One listener for the whole app sees every window's input (dialogs, sheets,
 * post windows) before Compose handles it, so the mode is already right when
 * the click or the Tab moves the focus.
 */
internal object KeyboardFocusLook {
    /** True once a key was pressed, false again at the next mouse press. */
    var keyboard by mutableStateOf(false)
        private set

    /** Starts following the input, once for the whole app. */
    fun start() = installed

    private val installed: Unit by lazy {
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                { event -> modeAfter(keyboard, event.id, (event as? KeyEvent)?.keyCode)?.let { keyboard = it } },
                AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK,
            )
        }
    }

    /**
     * The mode after an AWT event of type [id] ([keyCode] for a key), or null
     * where it stays: a key pressed (bar a modifier on its own, which starts
     * a shortcut or a modified click) turns it to the keyboard, a mouse
     * button pressed back to the pointer.
     */
    fun modeAfter(keyboard: Boolean, id: Int, keyCode: Int?): Boolean? = when {
        id == KeyEvent.KEY_PRESSED && keyCode !in MODIFIERS && !keyboard -> true
        id == MouseEvent.MOUSE_PRESSED && keyboard -> false
        else -> null
    }

    private val MODIFIERS = setOf(
        KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_META, KeyEvent.VK_WINDOWS,
    )
}
