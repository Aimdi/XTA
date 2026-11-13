package com.aimdi.xtanative.networks.substack

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.math.ceil
import kotlin.math.max
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

/** A passage of an article that contents and find can lead to, by its [anchor]. */
data class SubstackPassage(val anchor: String, val text: String, val headingLevel: Int = 0) {
    /** The passage around the first word of [query], cut to a row's length. */
    fun excerpt(query: String): String {
        val term = SubstackText.terms(query).firstOrNull().orEmpty()
        val at = text.lowercase().indexOf(term)
        val start = if (at > 72) text.lastIndexOf(' ', at - 48) + 1 else 0
        val lead = if (start > 0) "…" else ""
        if (text.length - start <= 240) return lead + text.substring(start)
        val end = text.lastIndexOf(' ', start + 240)
        return "$lead${text.substring(start, if (end > start) end else start + 240)}…"
    }
}

/**
 * A post body parsed once, as XTA's SubstackArticleDocument: the sanitized
 * [body] with its passages marked and its embeds held, its [passages] and
 * [headings] for contents and find, its [plainText] for speech, search and
 * read time, and its [blocks] for the reader.
 */
class SubstackArticle private constructor(
    val body: String,
    val passages: List<SubstackPassage>,
    val headings: List<SubstackPassage>,
    val plainText: String,
    val blocks: List<SubstackBlock>,
) {
    /** The passages that hold every word of [query], in any order. */
    fun search(query: String): List<SubstackPassage> {
        val terms = SubstackText.terms(query)
        if (terms.isEmpty()) return emptyList()
        return passages.filter { passage -> passage.text.lowercase().let { text -> terms.all { it in text } } }
    }

    /** What the contents sheet lists: the headings while nothing is asked, else the passages found; empty is "no headings" or "no matches". */
    fun contents(query: String): List<SubstackPassage> = if (query.isBlank()) headings else search(query)

    /** The block [passage] is drawn in, for a contents or find result to scroll to. */
    fun blockOf(passage: SubstackPassage): Int? = SubstackBlocks.blockOfPassage(blocks, passages.map { it.anchor }, passage.anchor)

    companion object {
        private const val PASSAGES = "h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption,td,th"
        private val hiddenStyle = Regex("(?:^|;)\\s*(display\\s*:\\s*none|visibility\\s*:\\s*hidden)\\b", RegexOption.IGNORE_CASE)

        /** [raw] sanitized and indexed. Markers a publisher left in the markup are dropped, so none collides with the reader's. */
        fun parse(raw: String): SubstackArticle {
            val root = SubstackHtml.fragment(SubstackHtml.sanitize(raw))
            root.select("[data-xta-block]").forEach { it.removeAttr("data-xta-block") }
            val plainText = SubstackHtml.plainTextOf(root)
            holdEmbeds(root)
            val passages = leafBlocks(root).mapNotNull { node ->
                val text = SubstackText.collapse(SubstackHtml.textOf(node)).trim()
                if (text.isEmpty() || hidden(node)) null else node to text
            }.mapIndexed { index, (node, text) ->
                val anchor = "xta-passage-$index"
                node.attr("data-xta-block", anchor)
                val tag = node.normalName()
                SubstackPassage(anchor, text, if (tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6') tag[1].digitToInt() else 0)
            }
            val body = root.html()
            return SubstackArticle(body, passages, passages.filter { it.headingLevel > 0 }, plainText, SubstackBlocks.ofBody(body))
        }

        /** Blocks with no block inside; text with no block at all becomes one paragraph. */
        private fun leafBlocks(root: Element): List<Element> {
            val leaves = root.below(PASSAGES).filter { it.below(PASSAGES).isEmpty() }
            if (leaves.isNotEmpty() || SubstackHtml.textOf(root).isBlank()) return leaves
            val paragraph = Element("p")
            root.childNodes().toList().forEach(paragraph::appendChild)
            root.appendChild(paragraph)
            return listOf(paragraph)
        }

        /** Each iframe goes into an inert template behind a placeholder naming its host, so nothing loads until asked. */
        private fun holdEmbeds(root: Element) {
            for (frame in root.select("iframe")) {
                val src = frame.attr("src").trim()
                val host = (if (src.startsWith("//")) "https:$src" else src).toHttpUrlOrNull()?.host.orEmpty()
                val held = Element("div").addClass("xta-embed").attr("data-host", SubstackHosts.withoutWww(host))
                frame.replaceWith(held)
                held.appendChild(Element("template").appendChild(frame))
            }
        }

        private fun hidden(element: Element): Boolean = generateSequence(element) { it.parent() }.any { node ->
            node.hasAttr("hidden") || node.attr("aria-hidden") == "true" || hiddenStyle.containsMatchIn(node.attr("style"))
        }
    }
}

/**
 * The last [capacity] parsed articles, least recently used out first, as
 * XTA's SubstackParsedArticles. An entry is reused only for the very same
 * body, so an edited post is parsed again; key it by post key and date.
 */
class SubstackParsedArticles(private val capacity: Int = 16) {
    private class Entry(val body: String, val article: SubstackArticle)

    private val entries = LinkedHashMap<String, Entry>()

    /** The parse kept under [key] for this very [body]; one kept for another body is dropped. */
    @Synchronized
    fun get(key: String, body: String): SubstackArticle? {
        val entry = entries.remove(key)?.takeIf { it.body == body } ?: return null
        entries[key] = entry
        return entry.article
    }

    @Synchronized
    fun put(key: String, body: String, article: SubstackArticle) {
        entries.remove(key)
        entries[key] = Entry(body, article)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    /** The parse of [post]'s body, from here when it is the same body, else parsed and kept. */
    fun of(post: SubstackPost): SubstackArticle? {
        val body = post.bodyHtml?.takeIf { it.isNotBlank() } ?: return null
        val key = "${post.key}\n${post.postDate}"
        return get(key, body) ?: SubstackArticle.parse(body).also { put(key, body, it) }
    }
}

/** What the reader shows for a post. */
enum class SubstackReaderBody {
    /** Its body, rendered; a paid post's free opening is one ([SubstackReading.isPreview]). */
    ARTICLE,

    /** A paid post without a body: the paid pane with its excerpt, never the publisher's page. */
    PAYWALL,

    /** The post's own page on the web, when there is no body but an address. */
    LIVE_SITE,

    /** Nothing to show. */
    EMPTY,
}

/** What the reader decides about a post, as XTA's reader does. */
object SubstackReading {
    const val WORDS_PER_MINUTE = 230

    /**
     * A body wins; then a paid post gets the paid pane rather than the
     * publisher's page, which runs the publisher's scripts (BUG-11); then the
     * live page; else nothing.
     */
    fun bodyFor(post: SubstackPost): SubstackReaderBody = when {
        !post.bodyHtml.isNullOrBlank() -> SubstackReaderBody.ARTICLE
        post.isPaywalled -> SubstackReaderBody.PAYWALL
        post.canonicalUrl != null -> SubstackReaderBody.LIVE_SITE
        else -> SubstackReaderBody.EMPTY
    }

    /**
     * Whether a post that could not be read at all is shown as its own page
     * instead: XTA's reader loads the publication's page in its web view
     * rather than say the read failed (substack_reader_screen.dart's _load),
     * and Listen reads that page; XTA's desktop, without a web view, opens the
     * page in the browser (pushWebViewScreen). Only a post with no address
     * keeps the failure.
     */
    fun fallsBackToSite(post: SubstackPost): Boolean = post.canonicalUrl != null

    /**
     * Whether [post] is shown as the free part of a paid post: the reader ends
     * it with a note that the preview stops here and a link to the rest.
     */
    fun isPreview(post: SubstackPost): Boolean = post.isPaywalled && bodyFor(post) == SubstackReaderBody.ARTICLE

    /**
     * Minutes to read [post], at [WORDS_PER_MINUTE] rounded up, at least one: the
     * words of [plainText] when the body is at hand, else the API's count. None
     * for an episode or a video, or when neither count is known.
     */
    fun readMinutes(post: SubstackPost, plainText: String? = null): Int? {
        if (post.isPodcast || post.isVideo) return null
        val words = if (plainText == null) post.wordCount else SubstackText.words.findAll(plainText).count().toLong()
        if (words == null || words <= 0) return null
        return max(1, ceil(words / WORDS_PER_MINUTE.toDouble()).toInt())
    }

    /**
     * The fragment [url] jumps to inside the article at [canonical], decoded;
     * null when it leads anywhere else: another article, another origin, or
     * no fragment at all.
     */
    fun articleFragment(url: String, canonical: String?): String? {
        if (url.startsWith("#")) return decodeFragment(url.substring(1))
        val base = canonical?.toHttpUrlOrNull() ?: return null
        val target = base.resolve(url) ?: return null
        val fragment = target.encodedFragment ?: return null
        if (target.scheme != base.scheme || target.host != base.host || target.port != base.port) return null
        if (target.encodedPath.removeSuffix("/") != base.encodedPath.removeSuffix("/")) return null
        return decodeFragment(fragment)
    }

    /** A percent-encoded fragment, decoded; null when it is empty or its escapes are no UTF-8. */
    private fun decodeFragment(raw: String): String? {
        if (raw.isEmpty()) return null
        val out = StringBuilder()
        val pending = ByteArrayOutputStream()
        fun flush(): Boolean {
            if (pending.size() == 0) return true
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(pending.toByteArray()))
            } catch (_: CharacterCodingException) {
                return false
            }
            out.append(text)
            pending.reset()
            return true
        }
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '%') {
                val byte = raw.substring(i + 1, minOf(i + 3, raw.length)).takeIf { it.length == 2 }?.toIntOrNull(16) ?: return null
                pending.write(byte)
                i += 3
            } else {
                if (!flush()) return null
                out.append(raw[i++])
            }
        }
        return if (flush()) out.toString() else null
    }
}
