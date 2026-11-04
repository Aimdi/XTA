package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.ui.plugins.PluginRoute

/** A screen of the RSS plugin, as its route names it. */
internal sealed interface RssRoute {
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

/** The RSS plugin's routes on the app's back stack ([PluginRoute]): `item?…`, `feed?id=…`, `add`. */
internal object RssRoutes {
    private const val ITEM = "item"
    private const val FEED = "feed"
    const val ADD = "add"

    fun item(item: RssItem): String =
        PluginRoute(ITEM, "id" to item.id, "feed" to item.feedId, "source" to item.feedTitle, "title" to item.title, "link" to item.link).toString()

    fun feed(feed: RssFeed): String = PluginRoute(FEED, "id" to feed.id, "url" to feed.feedUrl, "name" to feed.name).toString()

    fun parse(route: String): RssRoute? {
        val opened = PluginRoute.parse(route)
        return when (opened.name) {
            ITEM -> opened["id"]?.let { id -> RssRoute.Item(id, opened["feed"].orEmpty(), opened["source"].orEmpty(), opened["title"].orEmpty(), opened["link"]) }
            FEED -> opened["id"]?.let { id -> RssRoute.Feed(id, opened["url"] ?: id, opened["name"].orEmpty()) }
            ADD -> RssRoute.Add
            else -> null
        }
    }
}
