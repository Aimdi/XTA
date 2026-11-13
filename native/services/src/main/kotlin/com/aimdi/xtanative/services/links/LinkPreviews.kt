package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.services.util.WebLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** How a link preview inside a post is drawn. */
enum class LinkPreviewLayout {
    /** One short row: a small tile, the domain and the title. */
    COMPACT,

    /** The picture across the card, for links where the picture is the point. */
    LARGE,
}

/**
 * A link's preview: what the page says about itself. [kind] is its type as
 * the network or the page names it (`video`, `photo`, `article`, …).
 */
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val kind: String? = null,
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrBlank()

    val layout: LinkPreviewLayout get() = LinkPreviews.layoutFor(url, hasImage, kind)

    val domain: String get() = LinkPreviews.domain(url)

    /** The title to show; the address without its scheme when the page gave none. */
    val shownTitle: String get() = LinkPreviews.title(url, title)
}

/**
 * The rules every network's link preview follows (link_preview_card.dart),
 * and a reader for a page's own preview, for a link whose network sent no
 * card: its Open Graph tags, then Twitter's, then the page title, as XTA's
 * Threads and Substack readers look for them.
 */
object LinkPreviews {
    private val pictureKinds = setOf("video", "photo", "player")
    private val videoHosts = setOf("youtube.com", "youtu.be", "vimeo.com", "twitch.tv", "dailymotion.com")

    /**
     * Compact for an article, large only when the picture is what the link
     * is: a video, a photo page or a player, or a page on a video site. A
     * news story's share image is decoration.
     */
    fun layoutFor(url: String, hasImage: Boolean, kind: String? = null): LinkPreviewLayout {
        if (!hasImage) return LinkPreviewLayout.COMPACT
        if (kind?.lowercase() in pictureKinds) return LinkPreviewLayout.LARGE
        val host = domain(url)
        return if (videoHosts.any { host == it || host.endsWith(".$it") }) LinkPreviewLayout.LARGE else LinkPreviewLayout.COMPACT
    }

    /** The host a reader recognises: lower case, without `www.`; the text itself when it has no host. */
    fun domain(url: String): String {
        val host = WebLink.parse(url.trim())?.host.orEmpty()
        return if (host.isEmpty()) url.trim() else host.removePrefix("www.")
    }

    /** [title] trimmed, or the address without `http(s)://` when there is none. */
    fun title(url: String, title: String?): String = title?.trim()?.ifEmpty { null } ?: url.replace(Regex("^https?://"), "")

    /**
     * The preview a page gives itself, read from its HTML; null when it names
     * neither a title, a description nor a picture. Relative picture
     * addresses are read against [url].
     */
    fun parse(html: String, url: String): LinkPreview? {
        val document = Jsoup.parse(html, url)
        val title = meta(document, "og:title", "twitter:title") ?: document.title().trim().ifEmpty { null }
        val description = meta(document, "og:description", "twitter:description", "description")
        val image = metaUrl(document, "og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")
        if (title == null && description == null && image == null) return null
        return LinkPreview(
            url = metaUrl(document, "og:url") ?: url,
            title = title,
            description = description,
            imageUrl = image,
            siteName = meta(document, "og:site_name"),
            kind = meta(document, "og:type")?.substringBefore('.')?.lowercase(),
        )
    }

    /** The first of [names] the page sets, by `property` or `name`, its entities decoded. */
    private fun meta(document: Document, vararg names: String): String? =
        first(document, names) { it.attr("content") }

    /** [meta] for an address, read against the page's own. */
    private fun metaUrl(document: Document, vararg names: String): String? =
        first(document, names) { it.absUrl("content").ifEmpty { it.attr("content") } }

    private fun first(document: Document, names: Array<out String>, value: (Element) -> String): String? = names.firstNotNullOfOrNull { name ->
        document.select("meta[property=\"$name\"], meta[name=\"$name\"]").firstNotNullOfOrNull { value(it).trim().ifEmpty { null } }
    }
}

/**
 * Fetches a page's own preview through [http]. Only http(s) pages are
 * asked, redirects are followed, and an answer that is not a page (an
 * image, a PDF, an error) gives no preview rather than a failure.
 */
class LinkPreviewReader(private val http: XHttp) {
    suspend fun read(url: String): LinkPreview? {
        val target = url.trim().toHttpUrlOrNull() ?: return null
        val response = http.get(target, HEADERS, followRedirects = true)
        val type = response.header("content-type")?.lowercase().orEmpty()
        if (!response.isSuccessful || (type.isNotEmpty() && "html" !in type)) return null
        return withContext(Dispatchers.Default) { LinkPreviews.parse(response.body, target.toString()) }
    }

    companion object {
        /** A page answers a browser with its preview tags; some answer nothing else. */
        val HEADERS = mapOf(
            "accept" to "text/html,application/xhtml+xml",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36",
        )
    }
}
