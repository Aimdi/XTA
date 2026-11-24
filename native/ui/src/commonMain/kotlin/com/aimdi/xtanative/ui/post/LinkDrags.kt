package com.aimdi.xtanative.ui.post

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import java.net.URI

/**
 * Links dragged out of the app, as a browser's are: a post's time drags the
 * post's address, a link in a post's text or a link's card its address, into
 * a browser, an editor or a terminal.
 */
internal object LinkDrags {
    /** The address a drag hands over for [url] (a web address, or a mention or tag, see [MiddleClickLinks]): in ASCII, as `text/uri-list` asks; null for what is no page. */
    fun addressOf(url: String?): String? {
        val address = MiddleClickLinks.webAddressOf(url) ?: return null
        if (address.any { it.isWhitespace() }) return null
        return runCatching { URI(address).toASCIIString() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: address.takeIf { a -> a.all { it.code in 0x21..0x7e } }
    }

    /** [address] as a `text/uri-list` holds it (RFC 2483): one line, ended with CR LF. */
    fun uriList(address: String): String = address + "\r\n"
}

/**
 * Dragging this with the mouse drags [url]'s address out ([LinkDrags]); a
 * drag does not click what it starts on. Nothing without an address, and
 * nothing on Android, whose touch drags scroll.
 */
internal expect fun Modifier.dragsLink(url: String?): Modifier

/**
 * Dragging this with the mouse from a place [linkAt] names an address for
 * drags that address out ([LinkDrags]); a press anywhere else, and a drag
 * from there (selecting text), is left to what is under it.
 */
internal expect fun Modifier.dragsLinks(linkAt: (Offset) -> String?): Modifier

/** A drag from a link in [text], laid out as [layout] holds it, drags its address out (a mention's profile, a tag's search, as a middle click opens them). */
internal fun Modifier.linkDrags(text: AnnotatedString, layout: TextLayoutHolder): Modifier = dragsLinks { position ->
    tagAt(text, layout.result, position) { LinkDrags.addressOf(it) != null }?.let(LinkDrags::addressOf)
}
