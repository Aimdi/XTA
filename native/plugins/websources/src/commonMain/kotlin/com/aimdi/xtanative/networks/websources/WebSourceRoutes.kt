package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.RssItem
import java.net.URLDecoder
import java.net.URLEncoder

/** A screen of the web sources plugin, as its route names it. */
sealed interface WebSourceRoute {
    /** A source's lists, opened on [listId] (the first when absent). */
    data class Source(val sourceId: String, val listId: String?) : WebSourceRoute

    data class Search(val sourceId: String) : WebSourceRoute

    /** A post in the RSS reader: enough of it to open its kept copy or its page again after a restart. */
    data class Reader(val sourceId: String, val key: String, val title: String, val link: String?) : WebSourceRoute

    data object Add : WebSourceRoute
}

/** The web sources plugin's routes: `source?…`, `search?…`, `reader?…`, `add`. */
object WebSourceRoutes {
    const val ADD = "add"

    fun source(sourceId: String, listId: String? = null): String = "source?" + query("id" to sourceId, "list" to listId)

    fun search(sourceId: String): String = "search?" + query("id" to sourceId)

    fun reader(sourceId: String, item: RssItem): String = "reader?" + query("source" to sourceId, "key" to item.id, "title" to item.title, "link" to item.link)

    fun parse(route: String): WebSourceRoute? {
        val values = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        return when (route.substringBefore('?')) {
            "source" -> values["id"]?.let { WebSourceRoute.Source(it, values["list"]) }
            "search" -> values["id"]?.let { WebSourceRoute.Search(it) }
            "reader" -> {
                val source = values["source"] ?: return null
                WebSourceRoute.Reader(source, values["key"] ?: return null, values["title"].orEmpty(), values["link"])
            }
            ADD -> WebSourceRoute.Add
            else -> null
        }
    }

    private fun query(vararg values: Pair<String, String?>): String =
        values.mapNotNull { (key, value) -> value?.let { "$key=" + URLEncoder.encode(it, Charsets.UTF_8) } }.joinToString("&")
}

/** What went wrong reading a page, as XTA tells the reader: off the source's sites, an error, too large, or no answer. */
enum class WebSourceFailureKind { BLOCKED, STATUS, TOO_LARGE, UNREACHABLE }

/** How a web source's post reads in the RSS reader, as XTA hands it there. */
object WebSourceReading {
    /** The RSS reader's feed id for [sourceId]'s posts: `websources:<source>`. */
    fun feedId(sourceId: String): String = "${WebSources.NETWORK}:$sourceId"

    /** [item] of [source] with [html], its full text, as the RSS reader shows it. */
    fun itemOf(source: WebSourceDefinition, item: WebSourceItem, html: String): RssItem = RssItem(
        id = item.key,
        title = item.title ?: item.text ?: source.name,
        feedId = feedId(source.id),
        feedTitle = source.name,
        link = item.link ?: item.discussion,
        excerpt = item.text,
        bodyHtml = html,
        publishedAt = item.date,
        author = item.author,
        imageUrl = item.image,
    )

    /** The reader's stand-in for a post its route names, until its kept copy is read. */
    fun stub(source: WebSourceDefinition?, route: WebSourceRoute.Reader): RssItem = RssItem(
        id = route.key,
        title = route.title,
        feedId = feedId(route.sourceId),
        feedTitle = source?.name ?: route.sourceId,
        link = route.link,
    )

    /** Where a tapped post goes when it has no full text: its link, else its discussion. */
    fun address(item: WebSourceItem): String? = item.link ?: item.discussion

    /** The posts of a gallery with a picture, whose first pictures the viewer pages through, and [item]'s place among them; null when it has none. */
    fun pictured(items: List<WebSourceItem>, item: WebSourceItem): Pair<List<WebSourceItem>, Int>? {
        val pictured = items.filter { it.pictures.isNotEmpty() }
        val start = pictured.indexOf(item).takeIf { it >= 0 } ?: return null
        return pictured to start
    }

    /** How many columns a gallery takes [widthDp] wide: wider pictures at large text, as XTA's pluginGalleryColumns. */
    fun columns(widthDp: Float, fontScale: Float): Int {
        val minWidth = if (fontScale * 14 > 20) 240f else 152f
        return (widthDp / minWidth).toInt().coerceIn(1, 4)
    }

    /** What a failed read says, as XTA's webSourceFailureMessage chooses it, and the status when a site answered with one. */
    fun failure(error: Throwable?): Pair<WebSourceFailureKind, Int?> = when (error) {
        is WebSourceLimitException -> (if (error.kind == WebSourceLimitException.Kind.BLOCKED) WebSourceFailureKind.BLOCKED else WebSourceFailureKind.TOO_LARGE) to null
        is NetworkRequestException -> WebSourceFailureKind.STATUS to error.code
        else -> WebSourceFailureKind.UNREACHABLE to null
    }
}
