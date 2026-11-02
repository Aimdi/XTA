package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Public RSS and Atom feeds, read as XTA's RSS plugin reads them: no login,
 * nothing written anywhere. A feed answers with one page: its items.
 */
class RssClient(private val http: XHttp) {
    /** The channel at [feedUrl], with its items. */
    suspend fun channel(feedUrl: String): RssChannel = parsed(read(address(feedUrl)), feedUrl)

    /**
     * [feed]'s items, named after the feed as followed: an item without a
     * picture shows the feed's icon. One page: a feed has no next.
     */
    suspend fun items(feed: RssFeed): Page<RssItem> {
        val items = channel(feed.feedUrl).items.map {
            it.copy(imageUrl = it.imageUrl ?: feed.iconUrl, feedId = feed.id, feedTitle = feed.name)
        }
        return Page(items, next = null)
    }

    /**
     * A pasted site or feed address as a feed to follow, named after its
     * channel (else its host). The feed is read once, while it is found.
     */
    suspend fun lookup(input: String): RssFeed {
        val (feedUrl, body) = resolve(input)
        val channel = parsed(body, feedUrl)
        return RssFeed(
            id = Rss.feedId(feedUrl),
            feedUrl = feedUrl,
            name = channel.title ?: feedUrl.toHttpUrlOrNull()?.host ?: feedUrl,
            siteUrl = channel.link,
            iconUrl = channel.imageUrl,
            description = channel.description,
        )
    }

    /**
     * Where the feed of [input] is: the address itself when it answers with a
     * feed; else the first feed its page names in `<link rel="alternate">`;
     * else the first of the usual paths (`/feed`, `/rss`, `/atom.xml`…) that
     * answers with one. [RssException] when none does.
     */
    suspend fun resolveFeedUrl(input: String): String = resolve(input).first

    /**
     * Every followed feed's items as one list, newest first. Feeds are read
     * together; one that cannot be read is counted, not thrown, so it does
     * not empty the rest.
     */
    suspend fun timeline(feeds: List<RssFeed>): RssTimeline = coroutineScope {
        val read = feeds.map { feed -> async { orNull { items(feed).items } } }.awaitAll()
        RssTimeline(Rss.merge(emptyList(), read.filterNotNull().flatten()), failed = read.count { it == null })
    }

    private suspend fun resolve(input: String): Pair<String, String> {
        val trimmed = input.trim()
        val start = (if ("://" in trimmed) trimmed else "https://$trimmed").toHttpUrlOrNull()
            ?: throw RssException(RssException.Reason.NOT_A_URL, "Not a web address: $input")
        val body = read(start)
        if (RssParser.looksLikeFeed(body)) return start.toString() to body
        val candidates = (RssParser.discoverFeedUrls(body, start) + RssParser.commonFeedPaths(start)).distinct()
        return candidates.firstNotNullOfOrNull { candidate -> feedAt(candidate)?.let { candidate to it } }
            ?: throw RssException(RssException.Reason.NO_FEED, "No RSS or Atom feed at $input")
    }

    /** The feed document at [url], or null when it cannot be read or is not a feed. */
    private suspend fun feedAt(url: String): String? =
        orNull { read(address(url)) }?.takeIf(RssParser::looksLikeFeed)

    private suspend fun read(url: HttpUrl): String {
        val response = http.get(url, HEADERS, followRedirects = true)
        if (!response.isSuccessful) throw NetworkRequestException(Rss.NETWORK, response.code, "HTTP ${response.code}")
        return response.body
    }

    private fun address(url: String): HttpUrl =
        url.trim().toHttpUrlOrNull() ?: throw RssException(RssException.Reason.NOT_A_URL, "Not a web address: $url")

    /** Feeds can be long: they are parsed away from the caller's thread, which may be the UI's. */
    private suspend fun parsed(body: String, feedUrl: String): RssChannel =
        withContext(Dispatchers.Default) { RssParser.parse(body, feedUrl) }

    private suspend fun <T> orNull(read: suspend () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        const val USER_AGENT = "XTA RSS plugin"
        const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5"

        private val HEADERS = mapOf("User-Agent" to USER_AGENT, "Accept" to ACCEPT)
    }
}
