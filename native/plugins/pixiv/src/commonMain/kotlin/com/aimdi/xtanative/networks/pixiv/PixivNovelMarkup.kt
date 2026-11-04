package com.aimdi.xtanative.networks.pixiv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** One piece of a novel's body, top to bottom. */
sealed interface PixivNovelBlock {
    /** One line of text; an empty one is a blank line the author left. */
    data class Paragraph(val spans: List<PixivNovelSpan>) : PixivNovelBlock {
        val isBlank: Boolean get() = spans.isEmpty()
    }

    /** `[newpage]`: where page [page] starts; the text opens on page 1. */
    data class PageBreak(val page: Int) : PixivNovelBlock

    /** `[chapter:…]`: a heading, which may carry ruby. */
    data class Heading(val spans: List<PixivNovelSpan>) : PixivNovelBlock

    /** `[pixivimage:ID]` or `[pixivimage:ID-N]`: page [page] (from 1) of a work; [key] is how the novel's page files the picture. */
    data class Illust(val illustId: Long, val page: Int, val key: String) : PixivNovelBlock

    /** `[uploadedimage:ID]`: a picture uploaded with the novel. */
    data class Upload(val imageId: String) : PixivNovelBlock
}

/** A run inside a line. */
sealed interface PixivNovelSpan {
    data class Plain(val text: String) : PixivNovelSpan

    /** `[[rb:base > ruby]]`: [ruby] is read over [base]. */
    data class Ruby(val base: String, val ruby: String) : PixivNovelSpan

    /** `[[jumpuri:label > url]]`, an http or https [url]. */
    data class Link(val label: String, val url: String) : PixivNovelSpan

    /** `[jump:N]`: a link to page [page] of the same novel. */
    data class PageJump(val page: Int) : PixivNovelSpan {
        /** The markup as written, for a page the novel does not have. */
        val source: String get() = "[jump:$page]"
    }
}

/**
 * Pixiv's novel markup. Every line is a paragraph (blank lines kept), a page
 * break, a heading or a picture, and a tag sharing a line with text splits
 * it. A tag this does not know, or one written wrong, stays as text: nothing
 * in a text can make this throw.
 */
object PixivNovelMarkup {
    fun parse(text: String): List<PixivNovelBlock> {
        var page = 1
        return text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .flatMap(::lineBlocks)
            .map { if (it is PixivNovelBlock.PageBreak) PixivNovelBlock.PageBreak(++page) else it }
    }

    /** Where each page starts in [blocks]: page 1 at the top, the others at their breaks. */
    fun pageStarts(blocks: List<PixivNovelBlock>): Map<Int, Int> = buildMap {
        put(1, 0)
        blocks.forEachIndexed { index, block -> if (block is PixivNovelBlock.PageBreak) put(block.page, index) }
    }

    /**
     * The text without the markup: ruby as `base(ruby)`, links by their label
     * with the address beside it, a page break as a blank line, and pictures
     * and page jumps left out.
     */
    fun plainText(blocks: List<PixivNovelBlock>): String = blocks.mapNotNull(::plainLine).joinToString("\n")

    private fun plainLine(block: PixivNovelBlock): String? = when (block) {
        is PixivNovelBlock.Paragraph -> block.spans.joinToString("", transform = ::plainSpan)
        is PixivNovelBlock.Heading -> block.spans.joinToString("", transform = ::plainSpan)
        is PixivNovelBlock.PageBreak -> ""
        is PixivNovelBlock.Illust, is PixivNovelBlock.Upload -> null
    }

    private fun plainSpan(span: PixivNovelSpan): String = when (span) {
        is PixivNovelSpan.Plain -> span.text
        is PixivNovelSpan.Ruby -> "${span.base}(${span.ruby})"
        is PixivNovelSpan.Link -> if (span.label == span.url) span.url else "${span.label} (${span.url})"
        is PixivNovelSpan.PageJump -> ""
    }

    private fun lineBlocks(line: String): List<PixivNovelBlock> {
        val pieces = pieces(line)
        if (pieces.all { it is PixivNovelSpan }) return listOf(PixivNovelBlock.Paragraph(pieces.filterIsInstance<PixivNovelSpan>()))
        val (blocks, tail) = pieces.fold(emptyList<PixivNovelBlock>() to emptyList<PixivNovelSpan>()) { (blocks, spans), piece ->
            when (piece) {
                is PixivNovelSpan -> blocks to spans + piece
                else -> withParagraph(blocks, spans) + (piece as PixivNovelBlock) to emptyList()
            }
        }
        return withParagraph(blocks, tail)
    }

    /** Space left beside a block tag is not a line of its own. */
    private fun withParagraph(blocks: List<PixivNovelBlock>, spans: List<PixivNovelSpan>): List<PixivNovelBlock> =
        if (spans.all { it is PixivNovelSpan.Plain && it.text.isBlank() }) blocks else blocks + PixivNovelBlock.Paragraph(spans)

    /** The line's spans and blocks in order; text between tags is one plain span. */
    private fun pieces(line: String): List<Any> = buildList {
        var plainFrom = 0
        var at = line.indexOf('[')
        while (at >= 0) {
            val tag = tagAt(line, at)
            if (tag == null) {
                at = line.indexOf('[', at + 1)
                continue
            }
            if (at > plainFrom) add(PixivNovelSpan.Plain(line.substring(plainFrom, at)))
            add(tag.piece)
            plainFrom = tag.end
            at = line.indexOf('[', plainFrom)
        }
        if (plainFrom < line.length) add(PixivNovelSpan.Plain(line.substring(plainFrom)))
    }

    private class Found(val piece: Any, val end: Int)

    private fun tagAt(line: String, at: Int): Found? = when {
        line.startsWith("[[", at) -> pairedTag(line, at)
        line.startsWith("[chapter:", at) -> chapterTag(line, at)
        else -> line.indexOf(']', at).takeIf { it >= 0 }?.let { close -> simpleTag(line.substring(at + 1, close))?.let { Found(it, close + 1) } }
    }

    private fun simpleTag(body: String): Any? {
        if (body == "newpage") return PixivNovelBlock.PageBreak(0)
        val colon = body.indexOf(':')
        if (colon < 0) return null
        val value = body.substring(colon + 1).trim()
        return when (body.substring(0, colon)) {
            "jump" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { PixivNovelSpan.PageJump(it) }
            "pixivimage" -> illustBlock(value)
            "uploadedimage" -> value.ifEmpty { null }?.let { PixivNovelBlock.Upload(it) }
            else -> null
        }
    }

    private fun illustBlock(value: String): PixivNovelBlock.Illust? {
        val dash = value.indexOf('-')
        val id = (if (dash < 0) value else value.substring(0, dash)).toLongOrNull()
        val page = if (dash < 0) 1 else value.substring(dash + 1).toIntOrNull()
        if (id == null || id <= 0 || page == null || page <= 0) return null
        return PixivNovelBlock.Illust(id, page, value)
    }

    private fun pairedTag(line: String, at: Int): Found? {
        val close = line.indexOf("]]", at + 2)
        if (close < 0) return null
        val body = line.substring(at + 2, close)
        val piece = when {
            body.startsWith("rb:") -> ruby(body.substring(3))
            body.startsWith("jumpuri:") -> link(body.substring(8))
            else -> null
        }
        return piece?.let { Found(it, close + 2) }
    }

    private fun ruby(body: String): PixivNovelSpan.Ruby? =
        splitArrow(body, last = false)?.takeIf { (base, _) -> base.isNotEmpty() }?.let { (base, ruby) -> PixivNovelSpan.Ruby(base, ruby) }

    /** The address is what follows the last arrow, so a label may hold an arrow of its own. */
    private fun link(body: String): PixivNovelSpan.Link? {
        val (label, url) = splitArrow(body, last = true) ?: ("" to "")
        val uri = PixivUri.parse(url)?.takeIf { it.scheme == "https" || it.scheme == "http" } ?: return null
        return if (uri.host.isEmpty()) null else PixivNovelSpan.Link(label.ifEmpty { url }, url)
    }

    /** `left > right`, with the half- or full-width arrow Pixiv's editor writes; null without one or without a right side. */
    private fun splitArrow(body: String, last: Boolean): Pair<String, String>? {
        val half = if (last) body.lastIndexOf('>') else body.indexOf('>')
        val full = if (last) body.lastIndexOf('＞') else body.indexOf('＞')
        val at = when {
            last -> maxOf(half, full)
            half < 0 -> full
            full < 0 -> half
            else -> minOf(half, full)
        }
        if (at < 0) return null
        val right = body.substring(at + 1).trim()
        return if (right.isEmpty()) null else body.substring(0, at).trim() to right
    }

    private fun chapterTag(line: String, at: Int): Found? {
        val start = at + "[chapter:".length
        val close = chapterEnd(line, start) ?: return null
        val spans = pieces(line.substring(start, close)).filterIsInstance<PixivNovelSpan>()
        return Found(PixivNovelBlock.Heading(spans), close + 1)
    }

    /** The `]` closing a chapter title, past any `[[rb:…]]` inside it. */
    private fun chapterEnd(line: String, from: Int): Int? {
        var at = from
        while (at < line.length) {
            val ruby = if (line.startsWith("[[", at)) line.indexOf("]]", at + 2) else -1
            when {
                ruby >= 0 -> at = ruby + 2
                line[at] == ']' -> return at
                else -> at++
            }
        }
        return null
    }
}

/** Finds the novel object a novel's webview page writes after `novel:` among its scripts. */
internal object PixivNovelPage {
    private const val KEY = "novel:"
    private val strict = Json

    /** The first object after a `novel:` that is JSON; null when there is none. */
    fun json(html: String): JsonObject? = objectSources(html).firstNotNullOfOrNull { source ->
        runCatching { strict.parseToJsonElement(source) as? JsonObject }.getOrNull()
    }

    /**
     * Each `{…}` right after a `novel:`, cut at the brace that balances it.
     * Braces and escaped quotes inside strings do not count, so a text full of
     * them is cut where the object really ends.
     */
    fun objectSources(html: String): Sequence<String> = sequence {
        var at = html.indexOf(KEY)
        while (at >= 0) {
            val start = skipWhitespace(html, at + KEY.length)
            if (start < html.length && html[start] == '{') closingBrace(html, start)?.let { yield(html.substring(start, it + 1)) }
            at = html.indexOf(KEY, at + KEY.length)
        }
    }

    private fun skipWhitespace(text: String, from: Int): Int {
        var at = from
        while (at < text.length && text[at].code <= 0x20) at++
        return at
    }

    /** Where the object opened at [start] closes, or null when it never does. */
    private fun closingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (at in start until text.length) {
            val char = text[at]
            when {
                inString && escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                inString && char == '"' -> inString = false
                inString -> Unit
                char == '"' -> inString = true
                char == '{' -> depth++
                char == '}' && --depth == 0 -> return at
            }
        }
        return null
    }
}
