package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

/**
 * A link dropped on the window (from a browser's address bar, a chat, a
 * note) opens as a link handed to `xta-native <url>` does: X's in the app,
 * a plugin's on its screen, others in the browser. Only one web address
 * is taken; other text and files are left to what is under them.
 */
internal object DroppedLinks {
    /** The web address [text] is: one http(s) link alone on its first line, else null. */
    fun linkIn(text: String?): String? {
        val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
        if (line.any { it.isWhitespace() }) return null
        val lower = line.lowercase()
        return line.takeIf { (lower.startsWith("https://") || lower.startsWith("http://")) && line.length > lower.indexOf("://") + 3 }
    }
}

// Compose's desktop drag data still carries @ExperimentalComposeUiApi; the drop target @ExperimentalFoundationApi.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.takesDroppedLinks(): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val link = DroppedLinks.linkIn((event.dragData() as? DragData.Text)?.readText()) ?: return false
                DesktopIncomingLinks.hand(link)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> (event.dragData() as? DragData.Text) != null },
        target = target,
    )
}
