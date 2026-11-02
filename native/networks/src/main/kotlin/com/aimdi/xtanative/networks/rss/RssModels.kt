package com.aimdi.xtanative.networks.rss

import java.time.Instant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A public RSS or Atom feed the reader follows: where it is read from, and
 * what its channel said about itself when it was added.
 */
data class RssFeed(
    val id: String,
    val feedUrl: String,
    val name: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val description: String? = null,
)

/** A file an item carries: a podcast's audio, a video, a picture. */
data class RssEnclosure(val url: String, val type: String?, val length: Long?)

/**
 * One item of a feed. [id] is the feed's id and the item's own guid, link or
 * title, so the same post read twice is the same item.
 */
data class RssItem(
    val id: String,
    val title: String,
    val feedId: String,
    val feedTitle: String,
    val link: String? = null,
    /** The body as plain text, at most 240 characters. */
    val excerpt: String? = null,
    /** The full body as the feed sent it: HTML, to be sanitized before it is shown ([RssHtml.sanitize]). */
    val bodyHtml: String? = null,
    val publishedAt: Instant? = null,
    val author: String? = null,
    /** The item's picture, else (once read through [RssClient.items]) the feed's. */
    val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
    val enclosures: List<RssEnclosure> = emptyList(),
) {
    /** Whether the body says more than the excerpt: what makes the reader worth opening. */
    val hasReadableBody: Boolean
        get() = bodyHtml?.trim().orEmpty().let { it.isNotEmpty() && it != excerpt?.trim() }
}

/**
 * What a feed document says about itself, and its items. A mangled feed
 * still yields whatever could be read, never a throw.
 */
data class RssChannel(
    val title: String? = null,
    val description: String? = null,
    val link: String? = null,
    val imageUrl: String? = null,
    val items: List<RssItem> = emptyList(),
)

/** Every followed feed's items, newest first, and how many feeds could not be read. */
data class RssTimeline(val items: List<RssItem>, val failed: Int)

/**
 * Why a pasted address could not be turned into a feed: it is no web
 * address, or neither it nor its site offers RSS or Atom.
 */
class RssException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_A_URL, NO_FEED }
}

/** The rules XTA applies to feed addresses and item lists. */
object Rss {
    /** How the network names itself in a [com.aimdi.xtanative.networks.NetworkRequestException]. */
    const val NETWORK = "rss"

    private val feedPath = Regex("""(?:^|/)(?:feed|rss|atom)(?:[./]|$)|\.(?:xml|rss|atom)$""")

    /**
     * A stable id for a feed address: its scheme, host, port when not the
     * default, and path without trailing slashes. Query and fragment are left
     * out, so the same feed pasted twice is followed once.
     */
    fun feedId(url: String): String {
        val trimmed = url.trim()
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        val host = if (':' in parsed.host) "[${parsed.host}]" else parsed.host
        val port = if (parsed.port == HttpUrl.defaultPort(parsed.scheme)) "" else ":${parsed.port}"
        return "${parsed.scheme}://$host$port${parsed.encodedPath.trimEnd('/')}"
    }

    /** Whether [raw] already names a feed document rather than a site's home page. */
    fun looksLikeFeedUrl(raw: String): Boolean {
        val url = raw.trim().toHttpUrlOrNull() ?: return false
        return feedPath.containsMatchIn(url.encodedPath.lowercase())
    }

    /**
     * [existing] and [incoming] as one list, newest first, one item per id: a
     * later copy of an id replaces the earlier, so a refresh can update a
     * title without growing the list. Undated items sink below dated ones.
     */
    fun merge(existing: Iterable<RssItem>, incoming: Iterable<RssItem>): List<RssItem> {
        val byId = LinkedHashMap<String, RssItem>()
        (existing + incoming).forEach { byId[it.id] = it }
        return byId.values.sortedByDescending { it.publishedAt ?: Instant.EPOCH }
    }
}
