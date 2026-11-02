package com.aimdi.xtanative.networks.reddit

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A comment's words, and the pictures it carries, in order. */
internal data class CommentContent(val body: String, val mediaUrls: List<String>)

/**
 * The words and pictures of a comment's rendered markdown: the `.md` block
 * old.reddit.com and www.reddit.com both render a comment into (XTA's
 * `redditCommentContent`), and the same of a raw markdown body from the JSON.
 */
internal object RedditCommentContent {
    fun of(markdown: Element?): CommentContent {
        val media = mediaIn(markdown)
        return CommentContent(bodyOf(asMarkdown(markdown), media), media.urls)
    }

    /**
     * The pictures in a JSON comment's markdown [raw], and the words left once
     * media-only announcements are taken out (XTA's `mediaFromCommentBody`).
     */
    fun ofMarkdown(raw: String): CommentContent {
        val tokens = RedditMedia.MEDIA_TOKEN.findAll(raw).toList()
        val linked = bareUrl.findAll(raw).map { it.value }.filter { RedditMedia.embeddableImage(it) != null }.toList()
        val urls = (tokens.mapNotNull(RedditMedia::tokenImage) + linked.mapNotNull(RedditMedia::embeddableImage)).distinct()
        val text = tokens.fold(raw) { acc, token -> acc.replace(token.value, "") }.trim()
        if (text.isEmpty() || urls.isEmpty()) return CommentContent(RedditMedia.stripPlaceholderTokens(text), urls)
        val withoutLinks = (linked + urls).fold(RedditMedia.stripMediaLinks(text)) { acc, link -> acc.replace(link, "") }
        return CommentContent(RedditMedia.stripPlaceholderTokens(withoutLinks), urls)
    }

    private val bareUrl = Regex("""https?://[^$SPACE\])>]+""")

    /** The pictures a comment carries, and the media tokens that only announced them. */
    private data class Media(val urls: List<String>, val tokens: List<String>)

    /**
     * Three places carry a picture: a link someone pasted, an `img` the old
     * site inlined, and Reddit's `![gif](giphy|…)` token, which the old site
     * prints as text and is otherwise the picture's only trace.
     */
    private fun mediaIn(markdown: Element?): Media {
        if (markdown == null) return Media(emptyList(), emptyList())
        val tokens = RedditMedia.MEDIA_TOKEN.findAll(markdown.rawText()).toList()
        val urls = markdown.below("a[href]").map { RedditMedia.embeddableImage(it.attr("href")) } +
            markdown.below("img[src]").map { RedditMedia.embeddableImage(absolute(it.attr("src"))) } +
            tokens.map(RedditMedia::tokenImage)
        return Media(urls.filterNotNull().distinct(), tokens.map { it.value })
    }

    /** Reddit writes protocol-relative sources; a loader needs a scheme. */
    private fun absolute(src: String): String = if (src.startsWith("//")) "https:$src" else src

    /**
     * The words, without what only announced media. A token is always noise;
     * a bare URL is not: printing `https://i.redd.it/x.gif` above that very
     * picture is pointless, but prose that merely holds a link keeps every word.
     */
    private fun bodyOf(markdown: String, media: Media): String {
        val text = media.tokens.fold(markdown) { acc, token -> acc.replace(token, "") }.trim()
        if (text.isEmpty() || media.urls.isEmpty()) return RedditMedia.stripPlaceholderTokens(text)
        val withoutLinks = media.urls.fold(RedditMedia.stripMediaLinks(text)) { acc, url -> acc.replace(url, "") }
        return RedditMedia.stripPlaceholderTokens(withoutLinks)
    }

    /**
     * The visible words of a `.md` block, links to pages kept as
     * `[label](url)` so a screen can shorten and open them. The markup's own
     * line breaks are layout; the breaks in the text are the ones paragraphs,
     * lists and `<br>`s make.
     */
    private fun asMarkdown(markdown: Element?): String =
        markdown?.let { block -> buildString { block.childNodes().forEach { write(it, preformatted = false) } } }.orEmpty()

    private val paragraphs = setOf("p", "blockquote", "pre", "ul", "ol", "table", "hr", "h1", "h2", "h3", "h4", "h5", "h6")
    private val layout = Regex("[$SPACE]+")

    private fun StringBuilder.write(node: Node, preformatted: Boolean) {
        when {
            // Code keeps its indentation as no-break spaces, which the later folding of spaces leaves alone.
            node is TextNode -> append(if (preformatted) node.wholeText.replace(' ', ' ') else node.wholeText.replace(layout, " "))
            node !is Element -> Unit
            node.normalName() in paragraphs -> {
                if (isNotEmpty()) append("\n\n")
                writeChildren(node, preformatted || node.normalName() == "pre")
                append("\n\n")
            }
            node.normalName() == "li" -> {
                append("\n${bulletOf(node)} ")
                writeChildren(node, preformatted)
            }
            else -> writeInline(node, preformatted)
        }
    }

    private fun StringBuilder.writeChildren(element: Element, preformatted: Boolean) =
        element.childNodes().forEach { write(it, preformatted) }

    private fun StringBuilder.writeInline(element: Element, preformatted: Boolean) {
        when (element.normalName()) {
            "a" -> if (!writeLink(element)) writeChildren(element, preformatted)
            "br" -> append('\n')
            else -> writeChildren(element, preformatted)
        }
    }

    /**
     * A link to a picture as its label alone, the picture being shown anyway;
     * a link to a page as `[label](url)`. False for a link to neither, whose
     * words are written as they are.
     */
    private fun StringBuilder.writeLink(link: Element): Boolean {
        val href = link.attr("href")
        val label = link.rawText().trim()
        when {
            RedditMedia.embeddableImage(href) != null -> append(label)
            href.startsWith("http") -> append(if (label.isEmpty() || label == href) href else "[$label]($href)")
            else -> return false
        }
        return true
    }

    /** `1.` in a numbered list, from where it starts; `•` in any other. */
    private fun bulletOf(item: Element): String {
        val list = item.parent()?.takeIf { it.normalName() == "ol" } ?: return "•"
        val start = list.attr("start").trim().toIntOrNull() ?: 1
        return "${start + list.children().filter { it.normalName() == "li" }.indexOf(item)}."
    }
}
