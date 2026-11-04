package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.RssItem

/** What went wrong reading a page, as XTA tells the reader: off the source's sites, an error, too large, or no answer. */
enum class WebSourceFailureKind { BLOCKED, STATUS, TOO_LARGE, UNREACHABLE }

/** How a tapped post opens, as XTA's openWebSourceItem decides it. */
sealed interface WebSourceOpening {
    /** In the RSS reader, with the full text it carries. */
    data class Reader(val html: String) : WebSourceOpening

    /** In the RSS reader once the page its source's article rule names is read; at its address when that gives nothing. */
    data object Article : WebSourceOpening

    /** At its link, else its discussion; nowhere when it has neither. */
    data class Address(val url: String?) : WebSourceOpening
}

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

    /** The reader's stand-in for post [key] of [sourceId] (named [title], at [link]) until its kept copy is read. */
    fun stub(source: WebSourceDefinition?, sourceId: String, key: String, title: String, link: String?): RssItem = RssItem(
        id = key,
        title = title,
        feedId = feedId(sourceId),
        feedTitle = source?.name ?: sourceId,
        link = link,
    )

    /** Where a tapped post goes when it has no full text: its link, else its discussion. */
    fun address(item: WebSourceItem): String? = item.link ?: item.discussion

    /** How [item] of [source] opens when tapped: its own full text, else its source's article, else its address. */
    fun opening(source: WebSourceDefinition, item: WebSourceItem): WebSourceOpening {
        val html = item.html
        return when {
            !html.isNullOrEmpty() -> WebSourceOpening.Reader(html)
            source.article != null -> WebSourceOpening.Article
            else -> WebSourceOpening.Address(address(item))
        }
    }

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
