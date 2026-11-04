package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import com.aimdi.xtanative.ui.common.longPressOver

/** How a post's text was last laid out, for what a press lands on: read when pressed, never drawn from. */
internal class TextLayoutHolder {
    var result: TextLayoutResult? = null
        private set

    fun take(layout: TextLayoutResult) {
        result = layout
    }
}

/**
 * A long press on a cashtag in [text], as [layout] holds it laid out,
 * opening its sheet ([LocalCashtagSheet]) as XTA's does; a tap on it still
 * goes where it goes, and a press elsewhere in the text is left to the post.
 */
@Composable
internal fun Modifier.cashtagPresses(text: AnnotatedString, layout: TextLayoutHolder): Modifier {
    val sheet = LocalCashtagSheet.current ?: return this
    val any = remember(text) { text.getLinkAnnotations(0, text.length).any { symbolOf(it.item) != null } }
    if (!any) return this
    return longPressOver(accepts = { cashtagAt(text, layout.result, it) != null }) { position -> cashtagAt(text, layout.result, position)?.let(sheet) }
}

/** The cashtag of [text] under [position], laid out as [layout]; null where the press is off one. */
internal fun cashtagAt(text: AnnotatedString, layout: TextLayoutResult?, position: Offset): String? {
    if (layout == null || text.isEmpty()) return null
    val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length - 1)
    val link = text.getLinkAnnotations(offset, offset + 1).firstOrNull { symbolOf(it.item) != null } ?: return null
    if (!layout.getPathForRange(link.start, link.end).getBounds().contains(position)) return null
    return symbolOf(link.item)
}

private fun symbolOf(link: LinkAnnotation): String? = CashtagLinks.symbolOf((link as? LinkAnnotation.Clickable)?.tag)
