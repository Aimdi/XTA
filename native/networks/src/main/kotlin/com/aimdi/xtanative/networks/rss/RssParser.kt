package com.aimdi.xtanative.networks.rss

import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * RSS 2.0, RDF (RSS 1.0) and Atom documents read into a [RssChannel], with
 * the fields XTA keeps. Pure and tolerant: a mangled feed yields whatever
 * items could be read, never a throw.
 *
 * XTA read feeds with patterns over the raw text; this reads the document
 * with jsoup's XML parser and takes each field where XTA's patterns found
 * it, so entities are decoded as XML decodes them and a field is read from
 * its own element rather than from the first one of that name anywhere.
 */
object RssParser {
    private val commonPaths = listOf("/feed", "/rss", "/atom.xml", "/feed.xml", "/index.xml", "/rss.xml")
    private val feedStart = Regex("""<(?:rss|feed|rdf:RDF)\b""", RegexOption.IGNORE_CASE)
    private val pictureWords = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", "image")

    /** [xml], the feed at [feedUrl], as a channel and its items. */
    fun parse(xml: String, feedUrl: String): RssChannel {
        val document = runCatching { Jsoup.parse(xml, "", Parser.xmlParser()) }.getOrNull() ?: return RssChannel()
        document.outputSettings().prettyPrint(false)
        val root = document.children().firstOrNull() ?: return RssChannel()
        val feedId = Rss.feedId(feedUrl)
        return when (root.normalName().substringAfter(':')) {
            "feed" -> atom(root, feedId)
            // RSS 1.0 keeps its items beside the channel, not in it: XTA looked
            // inside the channel and found none.
            "rdf" -> rss(root.child("channel"), root, feedId)
            else -> (root.child("channel") ?: root).let { rss(it, it, feedId) }
        }
    }

    /**
     * Whether [body] is a feed document rather than a web page: RSS, Atom or
     * RDF opening within its first 800 characters.
     */
    fun looksLikeFeed(body: String): Boolean {
        val head = body.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        if (head.startsWith("<!DOCTYPE html", ignoreCase = true) || head.startsWith("<html", ignoreCase = true)) return false
        return feedStart.containsMatchIn(head.take(800))
    }

    /** The feeds a web page names in `<link rel="alternate" type="application/rss+xml" href="…">`, resolved against [page]. */
    fun discoverFeedUrls(html: String, page: HttpUrl): List<String> =
        Jsoup.parse(html).select("link[href]").filter(::namesAFeed).mapNotNull { page.resolve(it.attr("href"))?.toString() }.distinct()

    /** Where sites usually keep their feed: `/feed`, `/rss`, `/atom.xml`… on [site]'s host. */
    fun commonFeedPaths(site: HttpUrl): List<String> =
        commonPaths.map { site.newBuilder().encodedPath(it).query(null).fragment(null).build().toString() }

    private fun namesAFeed(link: Element): Boolean {
        val rel = link.attr("rel").lowercase()
        val type = link.attr("type").lowercase()
        return link.attr("href").isNotBlank() &&
            ("alternate" in rel || "feed" in rel) &&
            ("rss" in type || "atom" in type || "xml" in type)
    }

    private fun rss(channel: Element?, itemsRoot: Element, feedId: String): RssChannel {
        val title = channel?.field("title")
        val feedTitle = title ?: feedId
        return RssChannel(
            title = title,
            description = channel?.field("description"),
            link = channel?.field("link"),
            imageUrl = channel?.let { channelImage(it, itemsRoot) },
            items = itemsRoot.getElementsByTag("item").mapNotNull { rssItem(it, feedId, feedTitle) },
        )
    }

    private fun atom(feed: Element, feedId: String): RssChannel {
        val title = feed.field("title")
        val feedTitle = title ?: feedId
        return RssChannel(
            title = title,
            description = feed.field("subtitle"),
            link = atomLink(feed),
            imageUrl = feed.field("logo") ?: feed.field("icon"),
            items = feed.children().filter { it.normalName() == "entry" }.mapNotNull { atomItem(it, feedId, feedTitle) },
        )
    }

    private fun rssItem(item: Element, feedId: String, feedTitle: String): RssItem? {
        val title = item.field("title").orEmpty()
        val link = item.field("link")
        val id = itemId(feedId, item.field("guid"), link, title) ?: return null
        val content = item.field("content:encoded") ?: item.field("description")
        val enclosures = item.children().filter { it.normalName() == "enclosure" }.mapNotNull { enclosure(it, "url") }
        return RssItem(
            id = id,
            title = title.ifEmpty { feedTitle },
            feedId = feedId,
            feedTitle = feedTitle,
            link = link,
            excerpt = RssHtml.excerpt(content),
            bodyHtml = content,
            publishedAt = listOf("pubdate", "dc:date", "date").firstNotNullOfOrNull { RssDates.parse(item.field(it)) },
            author = item.field("dc:creator") ?: item.field("author"),
            imageUrl = itemImage(item, enclosures, content),
            categories = item.children().filter { it.normalName() == "category" }.mapNotNull { it.wholeText().trim().ifEmpty { null } },
            enclosures = enclosures,
        )
    }

    private fun atomItem(entry: Element, feedId: String, feedTitle: String): RssItem? {
        val title = entry.field("title").orEmpty()
        val link = atomLink(entry)
        val id = itemId(feedId, entry.field("id"), link, title) ?: return null
        val content = atomContent(entry, "content") ?: atomContent(entry, "summary")
        val enclosures = entry.links().filter { it.attr("rel").lowercase() == "enclosure" }.mapNotNull { enclosure(it, "href") }
        return RssItem(
            id = id,
            title = title.ifEmpty { feedTitle },
            feedId = feedId,
            feedTitle = feedTitle,
            link = link,
            excerpt = RssHtml.excerpt(content),
            bodyHtml = content,
            publishedAt = listOf("published", "updated").firstNotNullOfOrNull { RssDates.parse(entry.field(it)) },
            author = entry.child("author")?.let { it.field("name") ?: it.wholeText().trim().ifEmpty { null } },
            imageUrl = itemImage(entry, enclosures, content),
            categories = entry.children().filter { it.normalName() == "category" }.mapNotNull { it.attr("term").trim().ifEmpty { null } },
            enclosures = enclosures,
        )
    }

    /** The feed's id and the item's guid, else its link, else its title: null when it has none of them. */
    private fun itemId(feedId: String, guid: String?, link: String?, title: String): String? =
        (guid ?: link ?: title.ifEmpty { null })?.let { "$feedId::$it" }

    /**
     * Atom content as markup. XHTML content is the markup inside its `<div>`;
     * XTA flattened it to text, so a body lost its paragraphs and pictures.
     */
    private fun atomContent(entry: Element, name: String): String? {
        val element = entry.child(name) ?: return null
        if (element.attr("type").lowercase() != "xhtml") return element.wholeText().trim().ifEmpty { null }
        val markup = element.children().singleOrNull()?.takeIf { it.normalName().substringAfter(':') == "div" } ?: element
        return markup.html().trim().ifEmpty { null }
    }

    /** RSS 2.0's `<image><url>`, RDF's `<image>` beside the channel, or the channel's `rdf:resource` for it. */
    private fun channelImage(channel: Element, root: Element): String? =
        channel.child("image")?.field("url")
            ?: root.child("image")?.field("url")
            ?: channel.child("image")?.attr("rdf:resource")?.trim()?.ifEmpty { null }

    /**
     * The link to open: the first `alternate` (or unnamed) link that is HTML
     * or untyped, else the first `alternate` or `self` link, else the text of
     * a `<link>`.
     */
    private fun atomLink(element: Element): String? {
        val links = element.links().mapNotNull { link ->
            val href = link.attr("href").trim().ifEmpty { link.attr("url").trim() }.ifEmpty { null } ?: return@mapNotNull null
            val rel = link.attr("rel").trim().lowercase().ifEmpty { "alternate" }
            if (rel != "alternate" && rel != "self") null else href to link.attr("type").lowercase()
        }
        return links.firstOrNull { (_, type) -> "html" in type || type.isEmpty() }?.first
            ?: links.firstOrNull()?.first
            ?: element.field("link")
    }

    private fun enclosure(element: Element, urlAttribute: String): RssEnclosure? {
        val url = element.attr(urlAttribute).trim().ifEmpty { null } ?: return null
        return RssEnclosure(url, element.attr("type").trim().ifEmpty { null }, element.attr("length").trim().toLongOrNull())
    }

    /**
     * The item's picture: an enclosure that is one, else Media RSS content
     * that is one, else a Media RSS thumbnail, else the first `<img>` of the
     * body. An enclosure or `media:content` whose type names something else
     * (audio, video, a player) is not taken: XTA took the first of each
     * whatever it was, so a podcast's audio could stand as its picture.
     */
    private fun itemImage(item: Element, enclosures: List<RssEnclosure>, content: String?): String? =
        enclosures.firstOrNull(::isPicture)?.url
            ?: item.getElementsByTag("media:content").firstOrNull(::isPictureMedia)?.attr("url")?.trim()
            ?: item.getElementsByTag("media:thumbnail").firstNotNullOfOrNull { it.attr("url").trim().ifEmpty { null } }
            ?: content?.let(::firstImage)

    /** Typed enclosures are what their type says; untyped ones are pictures when their address looks like one, as in XTA. */
    private fun isPicture(enclosure: RssEnclosure): Boolean = when (val type = enclosure.type?.lowercase()) {
        null -> pictureWords.any { it in enclosure.url.lowercase() }
        else -> type.startsWith("image/")
    }

    /** Media RSS content is a picture when its medium or type says so, or when it says nothing. */
    private fun isPictureMedia(media: Element): Boolean {
        val medium = media.attr("medium").lowercase()
        val type = media.attr("type").lowercase()
        return media.attr("url").isNotBlank() &&
            (medium == "image" || type.startsWith("image/") || (medium.isEmpty() && type.isEmpty()))
    }

    private fun firstImage(html: String): String? =
        Jsoup.parseBodyFragment(html).selectFirst("img[src]")?.attr("src")?.ifEmpty { null }

    private fun Element.child(name: String): Element? = children().firstOrNull { it.normalName() == name }

    private fun Element.links(): List<Element> = children().filter { it.normalName() == "link" }

    /** The text of the child [name], as XML decodes it and trimmed; null when it is missing or blank. */
    private fun Element.field(name: String): String? = child(name)?.wholeText()?.trim()?.ifEmpty { null }
}
