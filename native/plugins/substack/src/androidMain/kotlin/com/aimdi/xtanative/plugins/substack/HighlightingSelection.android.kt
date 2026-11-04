package com.aimdi.xtanative.plugins.substack

import android.content.ClipboardManager
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Android's selection: its floating toolbar offers Copy, [label] and Select
 * all. Compose hands a toolbar no selected text, so [label] copies the
 * selection and reads it back from the clipboard once the copy lands there.
 */
@Composable
internal actual fun HighlightingSelection(label: String, onHighlight: (String) -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    val highlight = rememberUpdatedState(onHighlight)
    val toolbar = remember(view, label) { HighlightToolbar(view, label) { highlight.value(it) } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer { content() }
    }
}

private class HighlightToolbar(private val view: View, private val label: String, private val onHighlight: (String) -> Unit) : TextToolbar {
    private var mode: ActionMode? = null
    private var area = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null

    override val status: TextToolbarStatus get() = if (mode == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        area = rect
        copy = onCopyRequested
        selectAll = onSelectAllRequested
        val shown = mode
        if (shown == null) mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING) else shown.invalidate()
    }

    override fun hide() {
        mode?.finish()
        mode = null
    }

    private fun highlight() {
        val copy = copy ?: return
        val clipboard = view.context.getSystemService(ClipboardManager::class.java) ?: return
        val listener = object : ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged() {
                clipboard.removePrimaryClipChangedListener(this)
                val clip = clipboard.primaryClip?.takeIf { it.itemCount > 0 } ?: return
                clip.getItemAt(0).coerceToText(view.context)?.toString()?.takeIf { it.isNotBlank() }?.let(onHighlight)
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        copy()
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            if (copy != null) {
                menu.add(0, COPY, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, HIGHLIGHT, 1, label).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (selectAll != null) menu.add(0, SELECT_ALL, 2, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                COPY -> copy?.invoke()
                HIGHLIGHT -> highlight()
                SELECT_ALL -> selectAll?.invoke()
                else -> return false
            }
            if (item.itemId != SELECT_ALL) mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@HighlightToolbar.mode = null
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt())
        }
    }

    private companion object {
        const val COPY = 1
        const val HIGHLIGHT = 2
        const val SELECT_ALL = 3
    }
}
