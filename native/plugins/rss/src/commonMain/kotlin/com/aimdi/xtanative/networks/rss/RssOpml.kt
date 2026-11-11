package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssParser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** How an OPML import went: each feed [added], [alreadyFollowed] or [failed], out of [total]. */
data class RssOpmlImport(val added: Int = 0, val alreadyFollowed: Int = 0, val failed: Int = 0, val total: Int) {
    val done: Int get() = added + alreadyFollowed + failed
}

/**
 * The followed feeds as OPML 2.0, the file every feed reader exports and
 * imports, and an OPML file's feeds followed here.
 */
object RssOpml {
    /** [feeds] as OPML 2.0 titled [title]: one RSS outline each, with its feed and its site. */
    fun export(feeds: List<RssFeed>, title: String): String {
        val outlines = feeds.joinToString("\n") { feed ->
            val name = attribute(feed.name)
            val site = feed.siteUrl?.let { " htmlUrl=\"${attribute(it)}\"" }.orEmpty()
            "    <outline type=\"rss\" text=\"$name\" title=\"$name\" xmlUrl=\"${attribute(feed.feedUrl)}\"$site/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<opml version=\"2.0\">\n" +
            "  <head><title>${element(title)}</title></head>\n" +
            "  <body>\n" +
            (if (outlines.isEmpty()) "" else "$outlines\n") +
            "  </body>\n" +
            "</opml>\n"
    }

    /**
     * The feeds an OPML file lists, once each by feed id, in its order: an
     * outline's `xmlUrl`, nested outlines included. An outline without one is
     * a folder, or a site without a feed, and is left out.
     */
    fun feedUrls(opml: String): List<String> {
        val document = runCatching { Jsoup.parse(opml, "", Parser.xmlParser()) }.getOrNull() ?: return emptyList()
        val urls = LinkedHashMap<String, String>()
        for (outline in document.getElementsByTag("outline")) {
            val url = outline.attributes().asList().firstOrNull { it.key.equals("xmlUrl", ignoreCase = true) }?.value?.trim()
            if (url.isNullOrEmpty() || url.toHttpUrlOrNull() == null) continue
            urls.putIfAbsent(RssParser.feedId(url), url)
        }
        return urls.values.toList()
    }

    /**
     * Follows each of [urls]: one already followed ([isFollowed], by feed id)
     * stays as it is; another is read through [lookup], [AT_ONCE] side by
     * side, and goes to [follow] in the file's order. One that cannot be read
     * counts as failed and the rest go on. [progress] hears after each.
     */
    suspend fun import(
        urls: List<String>,
        isFollowed: (String) -> Boolean,
        lookup: suspend (String) -> RssFeed,
        follow: suspend (RssFeed) -> Unit,
        progress: (RssOpmlImport) -> Unit = {},
    ): RssOpmlImport = coroutineScope {
        val reading = Semaphore(AT_ONCE)
        val found = urls.map { url ->
            if (isFollowed(RssParser.feedId(url))) null else async { reading.withPermit { found(url, lookup) } }
        }
        var result = RssOpmlImport(total = urls.size)
        progress(result)
        for (each in found) {
            val feed = each?.await()
            result = when {
                each == null -> result.copy(alreadyFollowed = result.alreadyFollowed + 1)
                feed == null || !followed(feed, follow) -> result.copy(failed = result.failed + 1)
                else -> result.copy(added = result.added + 1)
            }
            progress(result)
        }
        result
    }

    /** How many feeds an import reads at once. */
    const val AT_ONCE = 4

    private suspend fun found(url: String, lookup: suspend (String) -> RssFeed): RssFeed? = attempt { lookup(url) }

    private suspend fun followed(feed: RssFeed, follow: suspend (RssFeed) -> Unit): Boolean = attempt { follow(feed) } != null

    private suspend fun <T> attempt(work: suspend () -> T): T? = try {
        work()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun attribute(text: String): String = element(text).replace("\"", "&quot;")

    private fun element(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
