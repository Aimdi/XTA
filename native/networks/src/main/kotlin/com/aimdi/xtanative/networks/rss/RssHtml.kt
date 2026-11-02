package com.aimdi.xtanative.networks.rss

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** What XTA does with the HTML a feed sends: an excerpt for cards, a sanitized body for the reader. */
object RssHtml {
    /** How long an excerpt may be, in characters. */
    const val EXCERPT_LENGTH = 240

    private val executable = Regex("^[${JsSpace.CLASS}]*(javascript|vbscript|data)[${JsSpace.CLASS}]*:", RegexOption.IGNORE_CASE)
    private val controls = Regex("[\\u0000-\\u0020]")

    private const val UNSAFE = "script, style, noscript, iframe, object, embed, meta, link, base, svg, math"
    private val urlAttributes = setOf("href", "src", "srcset", "action", "formaction", "poster", "background", "data")

    /**
     * [html] as plain text for a card: tags dropped, spaces collapsed, cut to
     * [EXCERPT_LENGTH] with an ellipsis. Null when no text is left.
     */
    fun excerpt(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val text = JsSpace.collapse(Jsoup.parseBodyFragment(html).body().text())
        return when {
            text.isEmpty() -> null
            text.length <= EXCERPT_LENGTH -> text
            else -> text.take(EXCERPT_LENGTH - 3) + "…"
        }
    }

    /**
     * [raw] without what would run rather than be read: scripts, frames,
     * embeds, styles, `<meta>` and `<base>`, event handlers, `srcdoc`, and
     * `javascript:`, `vbscript:` or `data:` addresses (control characters
     * and spaces inside the scheme do not hide one).
     */
    fun sanitize(raw: String): String {
        val document = Jsoup.parseBodyFragment(raw)
        document.outputSettings().prettyPrint(false)
        document.body().select(UNSAFE).remove()
        document.body().allElements.forEach(::stripExecutable)
        return document.body().html()
    }

    private fun stripExecutable(element: Element) {
        element.attributes().asList()
            .filter { isExecutable(it.key.lowercase().substringAfterLast(':'), it.value) }
            .forEach { element.removeAttr(it.key) }
    }

    private fun isExecutable(name: String, value: String): Boolean = when {
        name.startsWith("on") || name == "srcdoc" -> true
        name in urlAttributes -> executable.containsMatchIn(value.replace(controls, ""))
        else -> false
    }
}

/**
 * JavaScript's `\s`, which XTA's patterns are written with: ASCII spaces,
 * the no-break space and Unicode's spaces. Web sources read with it too.
 */
internal object JsSpace {
    /** The spaces as the inside of a regex character class. */
    const val CLASS = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    private val runs = Regex("[$CLASS]+")

    fun isSpace(c: Char): Boolean = when (c.code) {
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true
        in 0x2000..0x200A -> true
        else -> false
    }

    /** [text] with every run of spaces made one and its ends trimmed. */
    fun collapse(text: String): String = text.replace(runs, " ").trim()
}
