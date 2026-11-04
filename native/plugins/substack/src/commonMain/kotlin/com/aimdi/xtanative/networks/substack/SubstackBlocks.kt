package com.aimdi.xtanative.networks.substack

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A stretch of text with one style, inside a text block. */
data class SubstackRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val href: String? = null,
    /** The footnote this run points at, for a footnote marker. */
    val footnote: Int? = null,
)

/** One block of a post as the reader shows it, in reading order. */
sealed interface SubstackBlock {
    /** The anchors of the passages ([SubstackPassage]) drawn in this block. */
    val anchors: List<String>
}

/** A paragraph, heading, quote, list item, code block or footnote. */
data class SubstackTextBlock(
    val kind: Kind,
    val runs: List<SubstackRun>,
    /** A heading's level, or how deep a list item is nested (0 at the top). */
    val level: Int = 0,
    /** A list item's marker ("1.", "•") or a footnote's number. */
    val marker: String? = null,
    /** This passage's own anchor, when it has one. */
    val anchor: String? = null,
    override val anchors: List<String> = emptyList(),
) : SubstackBlock {
    enum class Kind { PARAGRAPH, HEADING, QUOTE, LIST_ITEM, CODE, FOOTNOTE }

    val text: String get() = runs.joinToString("") { it.text }
}

/** A picture, with its size when the post gives one and its caption as runs. */
data class SubstackImageBlock(
    val src: String,
    val alt: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val caption: List<SubstackRun> = emptyList(),
    override val anchors: List<String> = emptyList(),
) : SubstackBlock

/** An embed held until the reader asks for it: nothing from [host] loads before. */
data class SubstackEmbedBlock(val host: String, val src: String) : SubstackBlock {
    override val anchors: List<String> get() = emptyList()
}

/** Where Substack's own scripts would have drawn something: LaTeX, a gallery, a poll (BUG-08). */
data object SubstackWebOnlyBlock : SubstackBlock {
    override val anchors: List<String> get() = emptyList()
}

/** A horizontal rule. */
data object SubstackDividerBlock : SubstackBlock {
    override val anchors: List<String> get() = emptyList()
}

/** A table as rows of cell texts; rows with no text are left out. */
data class SubstackTableBlock(val rows: List<List<String>>, override val anchors: List<String> = emptyList()) : SubstackBlock

/** A post body as blocks, as XTA's substack_blocks.dart reads it. */
object SubstackBlocks {
    private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val blockTags = headings + setOf(
        "p", "div", "section", "article", "blockquote", "ul", "ol", "pre", "figure", "picture", "img", "hr", "table",
        "header", "footer", "aside", "main",
    )

    /** The blocks of an article's [SubstackArticle.body]: sanitized, its passages marked and its embeds held. */
    fun ofBody(body: String): List<SubstackBlock> = of(SubstackHtml.fragment(body))

    /** The blocks of an already sanitized body, in reading order. */
    fun of(root: Element): List<SubstackBlock> = Walker().also { it.children(root, 0) }.out

    /**
     * The block a passage is drawn in: its own, else the block of the nearest
     * passage before it, since a passage folded into a neighbour (a caption
     * cell, a heading in a list) reads there.
     */
    fun blockOfPassage(blocks: List<SubstackBlock>, passages: List<String>, anchor: String): Int? {
        val where = HashMap<String, Int>()
        blocks.forEachIndexed { index, block -> block.anchors.forEach { where[it] = index } }
        return (passages.indexOf(anchor) downTo 0).firstNotNullOfOrNull { where[passages[it]] }
    }

    /** The styled runs of a node's inline content. */
    fun runsOf(node: Node): List<SubstackRun> {
        if (node is TextNode) return listOf(SubstackRun(SubstackText.collapse(node.wholeText)))
        if (node !is Element) return emptyList()
        if (node.normalName() == "br") return listOf(SubstackRun("\n"))
        if (node.hasClass("footnote-anchor")) {
            val number = SubstackHtml.textOf(node).trim()
            return listOf(SubstackRun(number, footnote = number.toIntOrNull(), href = attrOrNull(node, "href")))
        }
        val inner = node.childNodes().flatMap(::runsOf)
        return when (node.normalName()) {
            "strong", "b" -> inner.map { it.copy(bold = true) }
            "em", "i" -> inner.map { it.copy(italic = true) }
            "code" -> inner.map { it.copy(code = true) }
            "a" -> inner.map { it.copy(href = attrOrNull(node, "href") ?: it.href) }
            else -> inner
        }
    }

    private class Walker {
        val out = ArrayList<SubstackBlock>()
        private val loose = ArrayList<SubstackRun>()

        fun children(parent: Element, listDepth: Int) {
            for (node in parent.childNodes()) {
                if (node is Element && node.normalName() in blockTags) {
                    flushLoose()
                    block(node, listDepth)
                } else {
                    loose += runsOf(node)
                }
            }
            flushLoose()
        }

        private fun block(node: Element, listDepth: Int) {
            val tag = node.normalName()
            when {
                node.hasClass("xta-embed") -> embed(node)
                node.hasClass("xta-web-only") -> out += SubstackWebOnlyBlock
                node.hasClass("footnote") -> footnote(node)
                tag in headings -> text(node, SubstackTextBlock.Kind.HEADING, level = tag.substring(1).toInt())
                tag == "p" -> text(node, SubstackTextBlock.Kind.PARAGRAPH)
                tag == "pre" -> add(
                    SubstackTextBlock(
                        SubstackTextBlock.Kind.CODE,
                        listOf(SubstackRun(SubstackHtml.textOf(node), code = true)),
                        anchor = anchorOf(node),
                        anchors = anchorsIn(listOf(node)),
                    ),
                )
                tag == "blockquote" -> quote(node)
                tag == "ul" || tag == "ol" -> list(node, ordered = tag == "ol", depth = listDepth)
                tag == "figure" || tag == "img" || tag == "picture" -> image(node)
                tag == "hr" -> out += SubstackDividerBlock
                tag == "table" -> table(node)
                else -> children(node, listDepth)
            }
        }

        private fun text(node: Element, kind: SubstackTextBlock.Kind, level: Int = 0, marker: String? = null) = add(
            SubstackTextBlock(kind, trimmed(runsOf(node)), level, marker, anchorOf(node), anchorsIn(listOf(node))),
        )

        private fun quote(node: Element) {
            val paragraphs = node.children().filter { it.normalName() == "p" }
            if (paragraphs.isEmpty()) return text(node, SubstackTextBlock.Kind.QUOTE)
            paragraphs.forEach { text(it, SubstackTextBlock.Kind.QUOTE) }
        }

        private fun list(node: Element, ordered: Boolean, depth: Int) {
            node.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
                val nested = item.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }
                val own = item.childNodes().filter { it !is Element || it !in nested }
                add(
                    SubstackTextBlock(
                        SubstackTextBlock.Kind.LIST_ITEM,
                        trimmed(own.flatMap(::runsOf)),
                        level = depth,
                        marker = if (ordered) "${index + 1}." else "•",
                        anchor = anchorOf(item),
                        anchors = listOfNotNull(anchorOf(item)) + anchorsIn(own.filterIsInstance<Element>()),
                    ),
                )
                nested.forEach { list(it, ordered = it.normalName() == "ol", depth = depth + 1) }
            }
        }

        private fun footnote(node: Element) {
            val number = node.below(".footnote-number").firstOrNull()?.let { SubstackHtml.textOf(it).trim() }
            val content = node.below(".footnote-content").firstOrNull() ?: node
            content.below("p").ifEmpty { listOf(content) }.forEach { text(it, SubstackTextBlock.Kind.FOOTNOTE, marker = number) }
        }

        private fun image(node: Element) {
            val img = if (node.normalName() == "img") node else node.below("img").firstOrNull() ?: return
            val src = img.attr("src")
            if (src.isEmpty()) return
            out += SubstackImageBlock(
                src = src,
                alt = img.attr("alt"),
                width = img.attr("width").toIntOrNull(),
                height = img.attr("height").toIntOrNull(),
                caption = node.below("figcaption").firstOrNull()?.let { trimmed(runsOf(it)) }.orEmpty(),
                anchors = anchorsIn(listOf(node)),
            )
        }

        private fun embed(node: Element) {
            val frame = node.below("template iframe").firstOrNull() ?: node.below("iframe").firstOrNull()
            val src = frame?.attr("src").orEmpty()
            if (src.isNotEmpty()) out += SubstackEmbedBlock(node.attr("data-host"), src)
        }

        private fun table(node: Element) {
            val rows = node.below("tr")
                .map { row -> row.children().map { cell -> SubstackText.collapse(SubstackHtml.textOf(cell)).trim() } }
                .filter { row -> row.any { it.isNotEmpty() } }
            if (rows.isNotEmpty()) out += SubstackTableBlock(rows, anchorsIn(listOf(node)))
        }

        private fun add(block: SubstackTextBlock) {
            if (block.text.isNotBlank()) out += block
        }

        private fun flushLoose() {
            if (loose.isEmpty()) return
            add(SubstackTextBlock(SubstackTextBlock.Kind.PARAGRAPH, trimmed(loose.toList())))
            loose.clear()
        }
    }

    private fun attrOrNull(node: Element, name: String): String? = if (node.hasAttr(name)) node.attr(name) else null

    private fun anchorOf(node: Element): String? = attrOrNull(node, "data-xta-block")

    /** The anchors on [nodes] and under them, in reading order. */
    private fun anchorsIn(nodes: List<Element>): List<String> =
        nodes.flatMap { node -> listOfNotNull(anchorOf(node)) + node.below("[data-xta-block]").mapNotNull(::anchorOf) }

    /** [runs] without the whitespace the block starts or ends with. */
    private fun trimmed(runs: List<SubstackRun>): List<SubstackRun> {
        val kept = runs.filter { it.text.isNotEmpty() }.toMutableList()
        if (kept.isEmpty()) return emptyList()
        kept[0] = kept.first().let { it.copy(text = it.text.trimStart()) }
        kept[kept.lastIndex] = kept.last().let { it.copy(text = it.text.trimEnd()) }
        return kept.filter { it.text.isNotEmpty() }
    }
}
