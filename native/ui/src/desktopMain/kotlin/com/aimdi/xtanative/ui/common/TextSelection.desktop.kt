package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

/**
 * What selecting text does on the Linux desktop beyond Compose's own
 * dragging, double and triple clicks and Ctrl+C: Ctrl+A selects all of a
 * read-only block (an opened post, a bio, an article) clicked into
 * ([TextBlocks]), the selection's menu offers Select all there too, and
 * whatever is selected and could be copied becomes the X primary selection,
 * which a middle click pastes in other apps. [base] is the menu that is drawn.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SelectingTextMenu(private val base: TextContextMenu) : TextContextMenu {
    @Composable
    override fun Area(textManager: TextContextMenu.TextManager, state: ContextMenuState, content: @Composable () -> Unit) {
        // Compose keeps a block's focus while its window is in the background, so the block
        // has the keys only while its own window has them too: Ctrl+A in another window is that window's.
        val window = LocalWindowInfo.current
        val block = remember(textManager, window) { SelectionManagers.of(textManager)?.inWindow { window.isWindowFocused } }
        val manager = remember(textManager, block) { WithSelectAll(textManager, block) }
        val current by rememberUpdatedState(manager)
        OffersPrimarySelection { current }
        if (block != null) {
            DisposableEffect(block) {
                TextBlocks.add(block)
                onDispose { TextBlocks.remove(block) }
            }
        }
        base.Area(manager, state, content)
    }
}

/**
 * The read-only blocks of text on screen. Compose's block holds the keys
 * once clicked into but lets only Ctrl+C through to itself, and the window
 * reads Ctrl+A before it ([selectAllInFocused]). The list is the whole app's:
 * a block counts as focused only while its own window is ([TextBlock.inWindow]).
 */
internal object TextBlocks {
    private val live = mutableListOf<TextBlock>()

    fun add(block: TextBlock) {
        live += block
    }

    fun remove(block: TextBlock) {
        live -= block
    }

    /** Selects all of the block that has the keys; false when none has. */
    fun selectAllInFocused(): Boolean {
        val block = live.lastOrNull { it.focused() } ?: return false
        block.selectAll()
        return true
    }
}

/** One read-only block of selectable text: whether it has the keys, and selecting all of it. */
internal class TextBlock(val focused: () -> Boolean, val selectAll: () -> Unit) {
    /** This block, having the keys only while [windowFocused] says its window has them. */
    fun inWindow(windowFocused: () -> Boolean): TextBlock = TextBlock(focused = { windowFocused() && focused() }, selectAll = selectAll)
}

/**
 * [inner] with a Select all for a read-only [block], which Compose's own
 * leaves out; a text field's own one stays.
 */
@OptIn(ExperimentalFoundationApi::class)
private class WithSelectAll(private val inner: TextContextMenu.TextManager, block: TextBlock?) : TextContextMenu.TextManager by inner {
    private val all: (() -> Unit)? = block?.selectAll
    override val selectAll: (() -> Unit)? get() = inner.selectAll ?: all
}

/** The text [manager] has selected, as the X primary selection whenever it settles and could be copied. */
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun OffersPrimarySelection(manager: () -> TextContextMenu.TextManager) {
    LaunchedEffect(Unit) {
        snapshotFlow { PrimaryText.offered(runCatching { manager().selectedText.text }.getOrDefault(""), manager().copy != null) }
            .debounce(PRIMARY_SETTLE_MS)
            .distinctUntilChanged()
            .filter { it != null }
            .collect { text -> withContext(Dispatchers.IO) { PrimaryText.set(text!!) } }
    }
}

/** How long a selection rests before it becomes the primary one, so a drag does not claim it at every step. */
private const val PRIMARY_SETTLE_MS = 200L

/** The X primary selection: what a middle click pastes. */
internal object PrimaryText {
    /** What a selection of [text] offers as the primary selection; null for none, or when it could not be copied (a password). */
    fun offered(text: String, copyable: Boolean): String? = text.takeIf { copyable && it.isNotEmpty() }

    /** Sets [text] as the primary selection, where the desktop has one (X11 and XWayland). */
    fun set(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemSelection?.setContents(StringSelection(text), null) }
    }
}

/** Which keys select all of a block's text. */
internal object TextSelectionKeys {
    /** Ctrl+A alone, as a text field takes it. */
    fun selectsAll(key: Key, type: KeyEventType, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Boolean =
        type == KeyEventType.KeyDown && key == Key.A && ctrl && !shift && !alt && !meta
}

/**
 * Compose's selection of a read-only block can select all of it and knows
 * whether it has the keys, but says neither; this reaches both from the
 * block's menu, where Compose's version has them (null where it does not).
 */
@OptIn(ExperimentalFoundationApi::class)
internal object SelectionManagers {
    const val MANAGER = "androidx.compose.foundation.text.selection.SelectionManager"
    const val SELECT_ALL = "selectAll\$foundation"
    const val HAS_FOCUS = "getHasFocus"

    fun of(textManager: TextContextMenu.TextManager): TextBlock? = runCatching {
        val field = textManager.javaClass.declaredFields.firstOrNull { it.type.name == MANAGER } ?: return null
        field.isAccessible = true
        val manager = field.get(textManager) ?: return null
        val selectAll = manager.javaClass.getMethod(SELECT_ALL)
        val hasFocus = manager.javaClass.getMethod(HAS_FOCUS)
        TextBlock(
            focused = { runCatching { hasFocus.invoke(manager) == true }.getOrDefault(false) },
            selectAll = { runCatching { selectAll.invoke(manager) } },
        )
    }.getOrNull()
}
