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
 * A long press on a cashtag or a hashtag in [text], as [layout] holds it
 * laid out, as XTA's rich text takes it: a cashtag opens its sheet
 * ([LocalCashtagSheet]), a hashtag goes to [onHashtag] (its topic sheet,
 * [TopicSheet]); a tap on either still goes where it goes, and a press
 * elsewhere in the text is left to the post.
 */
@Composable
internal fun Modifier.tagPresses(text: AnnotatedString, layout: TextLayoutHolder, onHashtag: (String) -> Unit): Modifier {
    val sheet = LocalCashtagSheet.current
    val pressable: (String?) -> Boolean = { tag -> TopicLinks.topicOf(tag) != null || (sheet != null && CashtagLinks.symbolOf(tag) != null) }
    val any = remember(text, sheet) { text.getLinkAnnotations(0, text.length).any { pressable(tagOf(it.item)) } }
    if (!any) return this
    return longPressOver(accepts = { tagAt(text, layout.result, it, pressable) != null }) { position ->
        val tag = tagAt(text, layout.result, position, pressable)
        val symbol = CashtagLinks.symbolOf(tag)
        when {
            symbol != null -> sheet?.invoke(symbol)
            tag != null -> TopicLinks.topicOf(tag)?.let(onHashtag)
        }
    }
}

/** The tag of the link of [text] under [position] that [accepts] takes, laid out as [layout]; null where the press is off one. */
private fun tagAt(text: AnnotatedString, layout: TextLayoutResult?, position: Offset, accepts: (String?) -> Boolean): String? {
    if (layout == null || text.isEmpty()) return null
    val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length - 1)
    val link = text.getLinkAnnotations(offset, offset + 1).firstOrNull { accepts(tagOf(it.item)) } ?: return null
    if (!layout.getPathForRange(link.start, link.end).getBounds().contains(position)) return null
    return tagOf(link.item)
}

private fun tagOf(link: LinkAnnotation): String? = (link as? LinkAnnotation.Clickable)?.tag
