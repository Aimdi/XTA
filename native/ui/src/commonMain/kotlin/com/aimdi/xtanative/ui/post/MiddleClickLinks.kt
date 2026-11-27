package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * what it leads to in a window of its own where the app has a screen for it
 * (a mention, a tag, a post's or a profile's link: [LocalWindowOpens]), as a
 * middle click on a post does; a page on the web opens its address
 * ([MiddleClickLinks]) with [openInBrowser]. A click off a link, and every
 * other button, is left to what is under it.
 */
@Composable
internal fun Modifier.linkMiddleClicks(text: AnnotatedString, layout: TextLayoutHolder, openInBrowser: (String) -> Unit): Modifier {
    val open = rememberMiddleClickOpen(openInBrowser)
    return pointerInput(text, layout) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isTertiaryPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                val tag = tagAt(text, layout.result, change.position) { MiddleClickLinks.webAddressOf(it) != null } ?: continue
                change.consume()
                open(tag)
            }
        }
    }
}

/** A middle click anywhere on this opens [url] in a window of its own or the browser, as [linkMiddleClicks] opens a link; nothing without one. */
@Composable
internal fun Modifier.middleClickOpens(url: String?, openInBrowser: (String) -> Unit): Modifier {
    if (MiddleClickLinks.webAddressOf(url) == null) return this
    val open = rememberMiddleClickOpen(openInBrowser)
    return pointerInput(url) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isTertiaryPressed) continue
                event.changes.forEach { it.consume() }
                url?.let(open)
            }
        }
    }
}

/** What a middle click on a link's [tag] does: a window of its own where the app has a screen for it, else the browser at its web address. */
@Composable
private fun rememberMiddleClickOpen(openInBrowser: (String) -> Unit): (String) -> Unit {
    val windows by rememberUpdatedState(LocalWindowOpens.current)
    val browser by rememberUpdatedState(openInBrowser)
    return remember {
        { tag ->
            windows?.link?.invoke(tag)?.invoke() ?: MiddleClickLinks.webAddressOf(tag)?.let(browser)
        }
    }
}
