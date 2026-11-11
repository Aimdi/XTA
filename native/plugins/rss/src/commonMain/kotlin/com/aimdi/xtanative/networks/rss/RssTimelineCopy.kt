package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.RssEnclosure
import com.aimdi.xtanative.networks.RssItem
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The timeline as last read: its newest items, when, and from which feeds. */
data class RssTimelineCopy(val items: List<RssItem>, val fetchedAt: Instant, val readFrom: Set<String>)

/**
 * The timeline kept on the device, so a start shows the items last read
 * while the feeds are read again: the newest [KEPT] items, read as leniently
 * as anything else kept here.
 */
object RssTimelineCopies {
    const val KEPT = 200

    fun encode(copy: RssTimelineCopy): String = buildJsonObject {
        put("fetchedAt", copy.fetchedAt.toString())
        put("readFrom", JsonArray(copy.readFrom.sorted().map(::JsonPrimitive)))
        put("items", JsonArray(copy.items.take(KEPT).map(::jsonOf)))
    }.toString()

    /** What was kept; null for nothing, or for a file that does not read. */
    fun decode(text: String?): RssTimelineCopy? {
        val root = J.parse(text.orEmpty())
        val fetchedAt = root["fetchedAt"].string?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return RssTimelineCopy(
            items = root["items"].list.mapNotNull(::itemOf),
            fetchedAt = fetchedAt,
            readFrom = root["readFrom"].list.mapNotNull { it.string }.toSet(),
        )
    }

    private fun jsonOf(item: RssItem): JsonObject = buildJsonObject {
        put("id", item.id)
        put("title", item.title)
        put("feedId", item.feedId)
        put("feedTitle", item.feedTitle)
        put("link", item.link)
        put("excerpt", item.excerpt)
        put("bodyHtml", item.bodyHtml)
        put("publishedAt", item.publishedAt?.toString())
        put("author", item.author)
        put("imageUrl", item.imageUrl)
        put("categories", JsonArray(item.categories.map(::JsonPrimitive)))
        put("enclosures", JsonArray(item.enclosures.map(::jsonOf)))
    }

    private fun jsonOf(enclosure: RssEnclosure): JsonObject = buildJsonObject {
        put("url", enclosure.url)
        put("type", enclosure.type)
        put("length", enclosure.length)
    }

    private fun itemOf(row: J): RssItem? {
        val id = row["id"].string?.ifEmpty { null } ?: return null
        val feedId = row["feedId"].string?.ifEmpty { null } ?: return null
        return RssItem(
            id = id,
            title = row["title"].string.orEmpty(),
            feedId = feedId,
            feedTitle = row["feedTitle"].string.orEmpty(),
            link = row["link"].string,
            excerpt = row["excerpt"].string,
            bodyHtml = row["bodyHtml"].string,
            publishedAt = row["publishedAt"].string?.let { runCatching { Instant.parse(it) }.getOrNull() },
            author = row["author"].string,
            imageUrl = row["imageUrl"].string,
            categories = row["categories"].list.mapNotNull { it.string },
            enclosures = row["enclosures"].list.mapNotNull { e -> e["url"].string?.let { RssEnclosure(it, e["type"].string, e["length"].long) } },
        )
    }
}
