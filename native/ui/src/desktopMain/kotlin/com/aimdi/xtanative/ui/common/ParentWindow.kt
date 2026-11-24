package com.aimdi.xtanative.ui.common

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window

/** The app's windows as other programs and dialogs see them. */
object ActiveWindow {
    /** The window the reader is using, else the app's first one showing; null in none (tests, headless). */
    fun window(): Window? = runCatching {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow ?: compose()
    }.getOrNull()

    /** The app's Compose window the reader is using, else the one showing. */
    fun compose(): ComposeWindow? =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? ComposeWindow
            ?: Window.getWindows().filterIsInstance<ComposeWindow>().firstOrNull { it.isShowing }
}

/**
 * A window as a portal's parent_window names it ("x11:<XID in hex>"), so
 * the desktop's dialog opens over it and stays above it. The X window id
 * is not public in AWT: it is read through the window's peer
 * (sun.awt.X11.XBaseWindow.getWindow), which the launcher opens with
 * --add-opens; where that fails (another toolkit, no opens) the handle is
 * empty, which portals take as "no parent".
 */
object ParentWindow {
    /** [window]'s handle; "" for none or one that cannot be read. */
    fun handle(window: Window?): String = window?.let(::xid)?.let(::x11) ?: ""

    /** The handle of X window [xid]. */
    fun x11(xid: Long): String = "x11:" + java.lang.Long.toHexString(xid)

    /** [window]'s X window id; null where it has none or the toolkit hides it. */
    fun xid(window: Window): Long? = runCatching {
        val peer = Component::class.java.getDeclaredField("peer").apply { isAccessible = true }.get(window) ?: return null
        val base = Class.forName("sun.awt.X11.XBaseWindow")
        if (!base.isInstance(peer)) return null
        (base.getMethod("getWindow").invoke(peer) as? Long)?.takeIf { it != 0L }
    }.getOrNull()
}
