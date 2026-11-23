package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import java.net.URLEncoder

/**
 * The web address a link in a post's text stands for, for opening it in
 * the browser with the mouse's middle button, as a desktop's browser opens
 * a link apart: a link its own address, a mention the profile on x.com, a
 * hashtag or cashtag X's search for it. Null for what is no page (a
 * contract address, which a click copies).
 */
internal object MiddleClickLinks {
    fun webAddressOf(tag: String?): String? {
        val text = tag?.trim()?.takeIf { it.length > 1 } ?: return null
        val lower = text.lowercase()
        return when {
            lower.startsWith("https://") || lower.startsWith("http://") -> text
            text.startsWith('@') -> "https://x.com/" + text.substring(1)
            text.startsWith('#') -> "https://x.com/hashtag/" + encoded(text.substring(1))
            text.startsWith('$') -> "https://x.com/search?q=" + encoded(text)
            else -> null
        }
    }

    private fun encoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}

/**
 * A middle click on a link in [text], laid out as [layout] holds it, opens
 * its web address ([MiddleClickLinks]) with [openInBrowser]; a click off a
 * link, and every other button, is left to what is under it.
 */
@Composable
internal fun Modifier.linkMiddleClicks(text: AnnotatedString, layout: TextLayoutHolder, openInBrowser: (String) -> Unit): Modifier {
    val open by rememberUpdatedState(openInBrowser)
    return pointerInput(text, layout) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isTertiaryPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                val address = tagAt(text, layout.result, change.position) { MiddleClickLinks.webAddressOf(it) != null }?.let(MiddleClickLinks::webAddressOf) ?: continue
                change.consume()
                open(address)
            }
        }
    }
}

/** A middle click anywhere on this opens [url] (a web address, see [MiddleClickLinks]) with [openInBrowser]; nothing without one. */
@Composable
internal fun Modifier.middleClickOpens(url: String?, openInBrowser: (String) -> Unit): Modifier {
    val address = MiddleClickLinks.webAddressOf(url) ?: return this
    val open by rememberUpdatedState(openInBrowser)
    return pointerInput(address) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isTertiaryPressed) continue
                event.changes.forEach { it.consume() }
                open(address)
            }
        }
    }
}
