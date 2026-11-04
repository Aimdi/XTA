package com.aimdi.xtanative.networks.pixiv

import java.time.Instant
import java.time.OffsetDateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Text rules every Pixiv parser shares. Whitespace is what Dart and
 * JavaScript call whitespace, wider than Java's `\s` (the ideographic space
 * among it), so captions and pages collapse and split as they do in XTA.
 */
internal object PixivText {
    const val SPACE = """\s   -     　﻿"""

    private val spaces = Regex("[$SPACE]+")

    /** A caption, which Pixiv sends as an HTML fragment, as plain text: tags and line breaks dropped. */
    fun plain(html: String?): String {
        val raw = html?.trim().orEmpty()
        return if (raw.isEmpty()) "" else textOf(Jsoup.parseBodyFragment(raw).body()).trim()
    }

    /** All the text under [node], joined as the DOM's `textContent` joins it. */
    fun textOf(node: Node?): String = buildString {
        node?.traverse { child, _ -> if (child is TextNode) append(child.wholeText) }
    }

    /** [element]'s text on one line, each run of whitespace one space. */
    fun collapsed(element: Element?): String = textOf(element).replace(spaces, " ").trim()

    fun nonEmpty(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    /** Pixiv's dates, such as `2026-08-01T09:00:00+09:00`; null for anything else. */
    fun instant(value: String?): Instant? = value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}

/** The elements under this one matching [query], never this one itself, as the DOM's `querySelectorAll` finds them. */
internal fun Element.below(query: String): List<Element> = select(query).filter { it !== this }

internal fun Element.firstBelow(query: String): Element? = below(query).firstOrNull()
