package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssItem
import java.net.URLDecoder
import java.net.URLEncoder

/** A screen of the RSS plugin, as its route names it. */
sealed interface RssRoute {
    /** An item in the reader: enough of it to open it again, from the offline copy or its page, after a restart. */
    data class Item(val id: String, val feedId: String, val feedTitle: String, val title: String, val link: String?) : RssRoute {
        /** What the route keeps of the item, when the item itself is no longer at hand. */
        val stub: RssItem get() = RssItem(id = id, title = title, feedId = feedId, feedTitle = feedTitle, link = link)
    }

    /** One followed (or found) feed's items. */
    data class Feed(val id: String, val feedUrl: String, val name: String) : RssRoute {
        /** What the route keeps of the feed, when the feed itself is no longer at hand. */
        val stub: RssFeed get() = RssFeed(id = id, feedUrl = feedUrl, name = name)
    }

    data object Add : RssRoute
}

/** The RSS plugin's routes: `item?…`, `feed?id=…`, `add`. */
object RssRoutes {
    const val ADD = "add"

    fun item(item: RssItem): String = "item?" + query(
        "id" to item.id,
        "feed" to item.feedId,
        "source" to item.feedTitle,
        "title" to item.title,
        "link" to item.link,
    )

    fun feed(feed: RssFeed): String = "feed?" + query("id" to feed.id, "url" to feed.feedUrl, "name" to feed.name)

    fun parse(route: String): RssRoute? {
        val name = route.substringBefore('?')
        val values = route.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        return when (name) {
            "item" -> RssRoute.Item(
                id = values["id"] ?: return null,
                feedId = values["feed"].orEmpty(),
                feedTitle = values["source"].orEmpty(),
                title = values["title"].orEmpty(),
                link = values["link"],
            )
            "feed" -> values["id"]?.let { id -> RssRoute.Feed(id, values["url"] ?: id, values["name"].orEmpty()) }
            ADD -> RssRoute.Add
            else -> null
        }
    }

    private fun query(vararg values: Pair<String, String?>): String =
        values.mapNotNull { (key, value) -> value?.let { "$key=" + URLEncoder.encode(it, Charsets.UTF_8) } }.joinToString("&")
}
