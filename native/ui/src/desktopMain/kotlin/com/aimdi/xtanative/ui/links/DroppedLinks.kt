package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * A link dropped on the window (from a browser's address bar, a chat, a
 * note) opens as a link handed to `xta-native <url>` does: X's in the app,
 * a plugin's on its screen, others in the browser. Only one web address
 * is taken; other text and files are left to what is under them.
 */
internal object DroppedLinks {
    /** The web address [text] is: one http(s) link alone on its first line (past `#` comments), else null. */
    fun linkIn(text: String?): String? {
        // A `text/uri-list` may start with # comment lines.
        val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return null
        if (line.any { it.isWhitespace() }) return null
        val lower = line.lowercase()
        return line.takeIf { (lower.startsWith("https://") || lower.startsWith("http://")) && line.length > lower.indexOf("://") + 3 }
    }
}

/**
 * The text of what is dragged, read from Java's side: a browser's link or
 * address bar offers `text/uri-list` (which Compose reads as a list of
 * files, and so finds no file in a web address) beside plain text; the
 * address list first, then the text.
 */
internal object DraggedText {
    private val URI_LIST: DataFlavor? = runCatching { DataFlavor("text/uri-list;class=java.lang.String") }.getOrNull()

    /** Whether [transfer] offers text of either kind. */
    fun offered(transfer: Transferable): Boolean = flavors(transfer).isNotEmpty()

    /** The text [transfer] holds, the address list first; null for none. */
    fun read(transfer: Transferable): String? = flavors(transfer).firstNotNullOfOrNull { flavor ->
        runCatching { transfer.getTransferData(flavor) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun flavors(transfer: Transferable): List<DataFlavor> =
        listOfNotNull(URI_LIST, DataFlavor.stringFlavor).filter { flavor -> runCatching { transfer.isDataFlavorSupported(flavor) }.getOrDefault(false) }
}

// Compose's desktop drag data (awtTransferable) still carries @ExperimentalComposeUiApi; the drop target @ExperimentalFoundationApi.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.takesDroppedLinks(): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val link = DroppedLinks.linkIn(DraggedText.read(event.awtTransferable)) ?: return false
                DesktopIncomingLinks.hand(link)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> DraggedText.offered(event.awtTransferable) },
        target = target,
    )
}

/** A link copied elsewhere, opened with Ctrl+V as one dropped on the window is ([DroppedLinks]). */
internal object PastedLinks {
    /** Opens the web address on the system clipboard; false when it holds none. */
    fun open(): Boolean {
        val text = runCatching {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String else null
        }.getOrNull()
        val link = DroppedLinks.linkIn(text) ?: return false
        DesktopIncomingLinks.hand(link)
        return true
    }
}
