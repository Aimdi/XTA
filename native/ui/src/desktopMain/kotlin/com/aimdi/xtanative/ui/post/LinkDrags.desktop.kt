package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.links.DraggedText
import java.awt.Component
import java.awt.EventQueue
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal actual fun Modifier.dragsLink(url: String?): Modifier {
    val address = LinkDrags.addressOf(url) ?: return this
    return this then LinkDragElement(address, null)
}

internal actual fun Modifier.dragsLinks(linkAt: (Offset) -> String?): Modifier = this then LinkDragElement(null, linkAt)

private data class LinkDragElement(val address: String?, val linkAt: ((Offset) -> String?)?) : ModifierNodeElement<LinkDragNode>() {
    override fun create() = LinkDragNode(address, linkAt)

    override fun update(node: LinkDragNode) {
        node.address = address
        node.linkAt = linkAt
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dragsLink"
    }
}

/**
 * Starts a link's drag once the mouse, pressed on the link, has moved past
 * the touch slop: that move is consumed, so the click under it (opening the
 * link or the post) is cancelled. A press on no link is left alone, so text
 * is still selected by dragging from outside a link. Touch is left alone too
 * (a touch screen's drag scrolls).
 */
@OptIn(ExperimentalComposeUiApi::class)
private class LinkDragNode(var address: String?, var linkAt: ((Offset) -> String?)?) : DelegatingNode(), CompositionLocalConsumerModifierNode {
    private var dragged: String? = null

    private val source = delegate(
        DragAndDropSourceModifierNode { _ ->
            val url = dragged ?: return@DragAndDropSourceModifierNode
            val (size, draw) = decoration(url)
            val under = DragHover.componentUnderPointer()
            startDragAndDropTransfer(
                DragAndDropTransferData(
                    DragAndDropTransferable(LinkTransfer(url)),
                    supportedActions = listOf(DragAndDropTransferAction.Copy, DragAndDropTransferAction.Link),
                    onTransferCompleted = { DragHover.settle(under) },
                ),
                size,
                draw,
            )
        },
    )

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                    val url = address ?: linkAt?.invoke(down.position) ?: return@awaitEachGesture
                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    dragged = url
                    source.requestDragAndDropTransfer(down.position)
                }
            },
        )
    }

    /** What follows the pointer: the address on a dark pill, as a browser shows a dragged link. */
    private fun decoration(url: String): Pair<Size, androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit> {
        val density = currentValueOf(LocalDensity)
        val measurer = TextMeasurer(currentValueOf(LocalFontFamilyResolver), density, currentValueOf(LocalLayoutDirection), cacheSize = 0)
        val pad = with(density) { 8.dp.toPx() }
        val text = measurer.measure(
            url,
            TextStyle(color = Color.White, fontSize = 13.sp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = with(density) { 360.dp.roundToPx() }),
        )
        val size = Size(text.size.width + pad * 2, text.size.height + pad * 2)
        return size to {
            drawRoundRect(Color(0xE6202124), cornerRadius = CornerRadius(pad, pad))
            drawText(text, topLeft = Offset(pad, pad))
        }
    }
}

/**
 * The pointer after a drag out of the window: the drag holds the mouse, so
 * the window hears neither the pointer leaving nor its button's release, and
 * what was under it at the start (a tooltip, a lit card) stays lit until the
 * pointer comes back. At the drag's end the window is told where the pointer
 * now is: moved to there when it is over the window, gone when it is not.
 */
internal object DragHover {
    /** The window's component under the pointer, which takes its mouse events; null when none is. */
    fun componentUnderPointer(): Component? {
        val at = MouseInfo.getPointerInfo()?.location ?: return null
        val window = Window.getWindows().firstOrNull { it.isShowing && it.bounds.contains(at) } ?: return null
        val local = Point(at).also { SwingUtilities.convertPointFromScreen(it, window) }
        return SwingUtilities.getDeepestComponentAt(window, local.x, local.y)
    }

    /** Tells [component] where the pointer is now, once the drag has let the mouse go. */
    fun settle(component: Component?) {
        component ?: return
        EventQueue.invokeLater {
            if (!component.isShowing) return@invokeLater
            val at = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
            val local = at?.let { Point(it).also { p -> SwingUtilities.convertPointFromScreen(p, component) } } ?: Point(-1, -1)
            val id = eventAt(component.width, component.height, local.x, local.y)
            component.dispatchEvent(MouseEvent(component, id, System.currentTimeMillis(), 0, local.x, local.y, 0, false))
        }
    }

    /** A move to the pointer when it is inside a [width] by [height] component, else the pointer leaving it. */
    fun eventAt(width: Int, height: Int, x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) MouseEvent.MOUSE_MOVED else MouseEvent.MOUSE_EXITED
}

/** The desktop's side of [LinkDrags]: what a dragged link hands over. */
internal object LinkDragFlavors {
    /** `text/uri-list`, which browsers and file managers take a link as. */
    val uriList: DataFlavor = DataFlavor("text/uri-list;class=java.lang.String").also { flavor ->
        // AWT maps text/uri-list to file lists alone; this lets a String go out under it too.
        (SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap)?.addUnencodedNativeForFlavor(flavor, "text/uri-list")
    }
}

/**
 * A link as a drag hands it over: `text/uri-list` for browsers and file
 * managers, plain text for editors and terminals, and the app's own mark
 * ([DraggedText.FROM_APP]), so the window it came from does not take it back.
 */
internal class LinkTransfer(private val address: String) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(LinkDragFlavors.uriList, DataFlavor.stringFlavor, DraggedText.FROM_APP)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = transferDataFlavors.any { it.equals(flavor) }

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor.equals(LinkDragFlavors.uriList) -> LinkDrags.uriList(address)
        flavor.equals(DataFlavor.stringFlavor) -> address
        flavor.equals(DraggedText.FROM_APP) -> address
        else -> throw UnsupportedFlavorException(flavor)
    }
}
