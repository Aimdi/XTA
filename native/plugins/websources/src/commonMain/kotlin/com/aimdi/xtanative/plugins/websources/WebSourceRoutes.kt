package com.aimdi.xtanative.plugins.websources

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.ui.plugins.PluginRoute

/** A screen of the web sources plugin, as its route names it. */
internal sealed interface WebSourceRoute {
    /** A source's lists, opened on [listId] (the first when absent). */
    data class Source(val sourceId: String, val listId: String?) : WebSourceRoute

    data class Search(val sourceId: String) : WebSourceRoute

    /** A post in the RSS reader: enough of it to open its kept copy or its page again after a restart. */
    data class Reader(val sourceId: String, val key: String, val title: String, val link: String?) : WebSourceRoute {
        /** The reader's stand-in for the post until its kept copy is read, its source being [source] where it is still installed. */
        fun stub(source: WebSourceDefinition?): RssItem = WebSourceReading.stub(source, sourceId, key, title, link)
    }

    data object Add : WebSourceRoute
}

/** The web sources plugin's routes on the app's back stack ([PluginRoute]): `source?…`, `search?…`, `reader?…`, `add`. */
internal object WebSourceRoutes {
    private const val SOURCE = "source"
    private const val SEARCH = "search"
    private const val READER = "reader"
    const val ADD = "add"

    fun source(sourceId: String, listId: String? = null): String = PluginRoute(SOURCE, "id" to sourceId, "list" to listId).toString()

    fun search(sourceId: String): String = PluginRoute(SEARCH, "id" to sourceId).toString()

    fun reader(sourceId: String, item: RssItem): String =
        PluginRoute(READER, "source" to sourceId, "key" to item.id, "title" to item.title, "link" to item.link).toString()

    fun parse(route: String): WebSourceRoute? {
        val opened = PluginRoute.parse(route)
        return when (opened.name) {
            SOURCE -> opened["id"]?.let { WebSourceRoute.Source(it, opened["list"]) }
            SEARCH -> opened["id"]?.let { WebSourceRoute.Search(it) }
            READER -> {
                val source = opened["source"] ?: return null
                WebSourceRoute.Reader(source, opened["key"] ?: return null, opened["title"].orEmpty(), opened["link"])
            }
            ADD -> WebSourceRoute.Add
            else -> null
        }
    }
}
