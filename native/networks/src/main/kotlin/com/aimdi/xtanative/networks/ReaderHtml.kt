package com.aimdi.xtanative.networks

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A stretch of an article's text in one style; [href] when it is a link. */
data class ReaderRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val href: String? = null,
)

/** One block of an article as the plugins' reader draws it, in reading order. */
sealed interface ReaderBlock {
    /** A paragraph, heading, quote, list item or code block. */
    data class Text(val kind: Kind, val runs: List<ReaderRun>, val level: Int = 0, val marker: String? = null) : ReaderBlock {
        enum class Kind { PARAGRAPH, HEADING, QUOTE, LIST_ITEM, CODE }

        val text: String get() = runs.joinToString("") { it.text }
    }

    data class Image(val src: String, val alt: String? = null) : ReaderBlock

    data object Divider : ReaderBlock
}

/**
 * An article's HTML as blocks to read natively, where XTA draws it in a web
 * view: sanitized first ([RssHtml.sanitize]), then paragraphs, headings,
 * quotes, lists, code, pictures and rules in their order, each with its
 * runs of bold, italic, code and links. Addresses are made absolute
 * against [base], the article's own page. Scripts and styles never get here.
 */
object ReaderHtml {
    private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val containers = setOf("div", "section", "article", "header", "footer", "aside", "main", "figure", "picture", "body", "center", "details", "summary", "dl", "dd", "dt")
    private val paragraphs = setOf("p", "figcaption", "caption", "address")
    private val bold = setOf("b", "strong")
    private val italic = setOf("i", "em", "cite", "q")
    private val code = setOf("code", "kbd", "samp", "tt")
    private val spaces = Regex("[${JsSpace.CLASS}]+")

    fun blocks(html: String, base: String?): List<ReaderBlock> {
        val body = Jsoup.parseBodyFragment(RssHtml.sanitize(html)).body()
        return Builder(base?.toHttpUrlOrNull()).apply { walk(body, Style(), Place()) }.done()
    }

    /** How the text being read looks, and where a link leads. */
    private data class Style(val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false, val href: String? = null)

    /** Which block the text being read goes into. */
    private data class Place(val kind: ReaderBlock.Text.Kind = ReaderBlock.Text.Kind.PARAGRAPH, val level: Int = 0, val marker: String? = null)

    private class Builder(private val base: HttpUrl?) {
        private val blocks = ArrayList<ReaderBlock>()
        private val runs = ArrayList<ReaderRun>()
        private var place = Place()

        fun done(): List<ReaderBlock> {
            flush()
            return blocks
        }

        fun walk(element: Element, style: Style, at: Place) {
            element.childNodes().forEach { node -> visit(node, style, at) }
        }

        private fun visit(node: Node, style: Style, at: Place) {
            when (node) {
                is TextNode -> text(node.wholeText, style, at)
                is Element -> element(node, style, at)
            }
        }

        private fun element(element: Element, style: Style, at: Place) {
            when (val name = element.normalName()) {
                "br" -> text("\n", style, at)
                "img" -> image(element)
                "hr" -> divider()
                "pre" -> preformatted(element)
                "ul", "ol" -> list(element, at, ordered = name == "ol")
                "blockquote" -> block(element, style, at.copy(kind = ReaderBlock.Text.Kind.QUOTE))
                in headings -> block(element, style.copy(bold = true), Place(ReaderBlock.Text.Kind.HEADING, name.drop(1).toInt()))
                in paragraphs, in containers, "li", "table", "tr" -> block(element, style, at)
                "td", "th" -> cell(element, style, at)
                else -> walk(element, inline(element, style), at)
            }
        }

        private fun inline(element: Element, style: Style): Style = when (element.normalName()) {
            in bold -> style.copy(bold = true)
            in italic -> style.copy(italic = true)
            in code -> style.copy(code = true)
            "a" -> style.copy(href = address(element.attr("href")) ?: style.href)
            else -> style
        }

        /** [element]'s text as blocks of its own, apart from what came before and what follows. */
        private fun block(element: Element, style: Style, at: Place) {
            flush()
            walk(element, style, at)
            flush()
        }

        private fun cell(element: Element, style: Style, at: Place) {
            if (runs.isNotEmpty()) text(" · ", style, at)
            walk(element, style, at)
        }

        private fun list(element: Element, at: Place, ordered: Boolean) {
            flush()
            val level = if (at.kind == ReaderBlock.Text.Kind.LIST_ITEM) at.level + 1 else 0
            element.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
                block(item, Style(), Place(ReaderBlock.Text.Kind.LIST_ITEM, level, if (ordered) "${index + 1}." else "•"))
            }
        }

        private fun preformatted(element: Element) {
            flush()
            val text = element.wholeText().trimEnd()
            if (text.isNotBlank()) blocks += ReaderBlock.Text(ReaderBlock.Text.Kind.CODE, listOf(ReaderRun(text, code = true)))
        }

        private fun image(element: Element) {
            val src = address(element.attr("src")) ?: return
            flush()
            blocks += ReaderBlock.Image(src, element.attr("alt").trim().ifEmpty { null })
        }

        private fun divider() {
            flush()
            if (blocks.lastOrNull() !is ReaderBlock.Divider && blocks.isNotEmpty()) blocks += ReaderBlock.Divider
        }

        private fun text(raw: String, style: Style, at: Place) {
            if (runs.isEmpty()) place = at
            val before = runs.lastOrNull()?.text
            val collapsed = if (raw == "\n") raw else raw.replace(spaces, " ")
            // One space between words, however the markup split them.
            val text = if (collapsed.startsWith(' ') && (before == null || before.endsWith(' ') || before.endsWith('\n'))) collapsed.drop(1) else collapsed
            if (text.isEmpty()) return
            val last = runs.lastOrNull()
            val same = last != null && last.bold == style.bold && last.italic == style.italic && last.code == style.code && last.href == style.href
            if (same) runs[runs.lastIndex] = last!!.copy(text = last.text + text) else runs += ReaderRun(text, style.bold, style.italic, style.code, style.href)
        }

        /** The runs read so far as one block, their edges and the spaces around line breaks trimmed; nothing when they hold no text. */
        private fun flush() {
            val trimmed = trimmedRuns(runs)
            runs.clear()
            if (trimmed.isNotEmpty()) blocks += ReaderBlock.Text(place.kind, trimmed, place.level, place.marker)
            place = Place()
        }

        private fun address(raw: String?): String? {
            val value = raw?.trim()?.ifEmpty { null } ?: return null
            val url = base?.resolve(value) ?: value.toHttpUrlOrNull() ?: return null
            return url.toString().takeIf { url.scheme == "https" || url.scheme == "http" }
        }
    }

    private fun trimmedRuns(runs: List<ReaderRun>): List<ReaderRun> {
        val cleaned = runs.map { it.copy(text = it.text.replace(Regex(" *\n *"), "\n")) }.toMutableList()
        while (cleaned.isNotEmpty() && cleaned.first().text.trimStart().isEmpty()) cleaned.removeAt(0)
        while (cleaned.isNotEmpty() && cleaned.last().text.trimEnd().isEmpty()) cleaned.removeAt(cleaned.lastIndex)
        if (cleaned.isEmpty()) return emptyList()
        cleaned[0] = cleaned[0].copy(text = cleaned[0].text.trimStart())
        cleaned[cleaned.lastIndex] = cleaned.last().copy(text = cleaned.last().text.trimEnd())
        return cleaned
    }
}
