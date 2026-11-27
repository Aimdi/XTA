package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A drop target ([dragAndDropTarget]) that shows it would take what is
 * dragged over it: while [takes] says so of the drag, its place is tinted
 * and outlined in the accent, as a desktop's drop targets light up, so the
 * reader sees before letting go that the drop does something (a link opens,
 * files are added). [offered] says whether to follow a drag at all, as
 * [dragAndDropTarget]'s own check; [onDrop] takes the drop, true if it did.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.showingDropTarget(
    offered: (DragAndDropEvent) -> Boolean,
    takes: (DragAndDropEvent) -> Boolean,
    onDrop: (DragAndDropEvent) -> Boolean,
): Modifier {
    var over by remember { mutableStateOf(false) }
    val latestTakes by rememberUpdatedState(takes)
    val latestDrop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                over = runCatching { latestTakes(event) }.getOrDefault(false)
            }

            override fun onExited(event: DragAndDropEvent) {
                over = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                over = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                over = false
                return latestDrop(event)
            }
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    return dragAndDropTarget(shouldStartDragAndDrop = offered, target = target).drawWithContent {
        drawContent()
        if (over) {
            val edge = DROP_EDGE.dp.toPx()
            drawRect(accent.copy(alpha = DROP_TINT))
            drawRect(accent, topLeft = Offset(edge / 2, edge / 2), size = Size(size.width - edge, size.height - edge), style = Stroke(edge))
        }
    }
}

/** The outline's width round a drop target a drag is over, in dp. */
private const val DROP_EDGE = 3

/** How much of the accent tints a drop target a drag is over. */
private const val DROP_TINT = 0.08f
