package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssItem
import java.time.Instant
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
