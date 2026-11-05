package com.aimdi.xtanative.ui.archive

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

@Composable
internal actual fun HighlightMenu(label: String, onHighlight: (() -> Unit)?, content: @Composable () -> Unit) {
    val view = LocalView.current
    val toolbar = remember(view) { HighlightToolbar(view) }
    SideEffect {
        toolbar.label = label
        toolbar.onHighlight = onHighlight
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}

/**
 * The selection's floating toolbar as Compose's own draws it (Copy, Paste,
 * Cut, Select all, as each is offered), with Highlight and note after them.
 */
private class HighlightToolbar(private val view: View) : TextToolbar {
    var label: String = ""
    var onHighlight: (() -> Unit)? = null
    private var mode: ActionMode? = null
    private var rect = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var paste: (() -> Unit)? = null
    private var cut: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.rect = rect
        copy = onCopyRequested
        paste = onPasteRequested
        cut = onCutRequested
        selectAll = onSelectAllRequested
        val shown = mode
        if (shown == null) {
            status = TextToolbarStatus.Shown
            mode = view.startActionMode(Callback(), ActionMode.TYPE_FLOATING)
        } else {
            shown.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        mode?.finish()
        mode = null
    }

    private fun fill(menu: Menu) {
        menu.clear()
        copy?.let { menu.add(0, COPY, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        paste?.let { menu.add(0, PASTE, 1, android.R.string.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        cut?.let { menu.add(0, CUT, 2, android.R.string.cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        selectAll?.let { menu.add(0, SELECT_ALL, 3, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        if (onHighlight != null) menu.add(0, HIGHLIGHT, 4, label).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    private inner class Callback : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            fill(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            fill(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                COPY -> copy?.invoke()
                PASTE -> paste?.invoke()
                CUT -> cut?.invoke()
                SELECT_ALL -> selectAll?.invoke()
                HIGHLIGHT -> onHighlight?.invoke()
                else -> return false
            }
            if (item.itemId != SELECT_ALL) mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (this@HighlightToolbar.mode === mode) {
                this@HighlightToolbar.mode = null
                status = TextToolbarStatus.Hidden
            }
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
            outRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }
    }

    private companion object {
        const val COPY = 1
        const val PASTE = 2
        const val CUT = 3
        const val SELECT_ALL = 4
        const val HIGHLIGHT = 5
    }
}
