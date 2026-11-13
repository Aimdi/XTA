package com.aimdi.xtanative.networks.substack

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * A post body as XTA's substack_html.dart cleans it. A post is written by
 * whoever runs the publication, so what would run rather than be read goes:
 * scripts, every `on…` handler, addresses that are code, and any form, since
 * a reader posts nothing. Substack's chrome (expand buttons, subscribe
 * widgets, the paywall marker) goes too.
 */
object SubstackHtml {
    private val removable = listOf(
        "script", "style", "meta", "link", "base", "object", "embed", "math", "button",
        "form", "input", "textarea", "select", "svg", "noscript",
        ".image-link-expand", ".restack-image", ".icon-container",
        "[data-component-name=\"AudioPlayer\"]", "[data-component-name=\"SubscribeWidget\"]",
        "[data-component-name=\"SubscribePrompt\"]", ".subscription-widget-wrap", ".paywall",
    ).joinToString(", ")
    private val executableUrl = Regex("^\\s*(javascript|vbscript|data)\\s*:", RegexOption.IGNORE_CASE)
    private val controls = Regex("[\\u0000-\\u0020]")
    private val urlAttributes = setOf("href", "src", "srcset", "action", "formaction", "poster", "background", "data")
    private val blocks = setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "figure", "tr")
    private val unspoken = setOf("script", "style", "svg", "button")

    /**
     * [raw] without what runs and without Substack's chrome. An empty block
     * Substack's own scripts would draw into (LaTeX, a gallery, a poll) is
     * kept as an `xta-web-only` placeholder, so the reader can say something
     * was there (BUG-08); other empty blocks go.
     */
    fun sanitize(raw: String): String = sanitized(raw).html()

    /** Plain text for search and speech, paragraph breaks kept, footnote numbers left out. */
    fun plainText(raw: String): String = plainTextOf(fragment(sanitize(raw)))

    /** What the reader speaks: the title, where it is from, the excerpt, then the body as plain text. */
    fun speakText(
        title: String,
        subtitle: String? = null,
        authorName: String? = null,
        publicationName: String? = null,
        bodyHtml: String? = null,
        bodyPlain: String? = null,
    ): String {
        val body = SubstackText.nonBlank(bodyPlain) ?: bodyHtml?.takeIf { it.isNotBlank() }?.let(::plainText).orEmpty()
        return listOf(title.trim(), publicationName?.trim(), authorName?.trim(), subtitle?.trim(), body)
            .filter { !it.isNullOrEmpty() }
            .joinToString("\n\n")
    }

    internal fun fragment(html: String): Element =
        Jsoup.parseBodyFragment(html).also { it.outputSettings().prettyPrint(false) }.body()

    private fun sanitized(raw: String): Element {
        val body = fragment(raw)
        body.select(removable).forEach { if (it.parent() != null) it.remove() }
        for (node in body.select("div, span")) {
            if (node.parent() == null || textOf(node).isNotBlank() || node.below("img, iframe, video, picture, table").isNotEmpty()) continue
            if (node.hasAttr("data-component-name") || node.hasAttr("data-attrs")) node.replaceWith(webOnly()) else node.remove()
        }
        body.allElements.forEach(::stripExecutable)
        return body
    }

    private fun webOnly(): Element = Element("div").addClass("xta-web-only").attr("data-component-name", "XtaWebOnly")

    /** Drops `on…` handlers, `srcdoc`, and URL attributes whose address is code (`javascript:`, `vbscript:`, `data:`). */
    private fun stripExecutable(element: Element) {
        val doomed = element.attributes().asList().map { it.key }.filter { key ->
            val name = key.lowercase().substringAfterLast(':')
            name.startsWith("on") || name == "srcdoc" ||
                (name in urlAttributes && executableUrl.containsMatchIn(element.attr(key).replace(controls, "")))
        }
        doomed.forEach(element::removeAttr)
    }

    /** The speech text of an already sanitized [root]. */
    internal fun plainTextOf(root: Element): String {
        val out = StringBuilder()
        fun walk(node: Node) {
            when {
                node is TextNode -> SubstackText.collapse(node.wholeText).takeIf { it.isNotBlank() }?.let(out::append)
                node !is Element -> Unit
                node.normalName() in unspoken -> Unit
                // A footnote's number read aloud runs into the sentence: "a claim1" (BUG-14).
                node.normalName() == "a" && (node.hasClass("footnote-anchor") || node.hasClass("footnote-number")) -> Unit
                node.normalName() == "br" -> out.append('\n')
                else -> {
                    val block = node.normalName() in blocks
                    if (block && out.isNotEmpty() && out.last() != '\n') out.append('\n')
                    node.childNodes().forEach(::walk)
                    if (block) out.append('\n')
                }
            }
        }
        root.childNodes().forEach(::walk)
        return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /** All the text under [node], as the DOM's `textContent` gives it: nothing collapsed, `<br>` not counted. */
    internal fun textOf(node: Node): String = buildString {
        fun walk(child: Node) {
            when (child) {
                is TextNode -> append(child.wholeText)
                is DataNode -> append(child.wholeData)
                else -> child.childNodes().forEach(::walk)
            }
        }
        node.childNodes().forEach(::walk)
    }
}

/** Elements under this one that match [query], this one not included (jsoup's `select` counts it). */
internal fun Element.below(query: String): List<Element> = children().select(query)
