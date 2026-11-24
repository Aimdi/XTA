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
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

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
            startDragAndDropTransfer(
                DragAndDropTransferData(DragAndDropTransferable(LinkTransfer(url)), supportedActions = listOf(DragAndDropTransferAction.Copy, DragAndDropTransferAction.Link)),
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
