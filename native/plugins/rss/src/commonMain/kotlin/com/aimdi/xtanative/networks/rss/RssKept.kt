package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.RssParser
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The followed feeds as XTA keeps them in `plugin.rss.feeds`, the copy its
 * RSS tab reads: a JSON list, read as leniently as XTA reads it.
 */
object RssFeeds {
    fun decode(text: String?): List<RssFeed> =
        J.parse(text.orEmpty()).list.mapNotNull(::feedOf).filter { it.id.isNotEmpty() && it.feedUrl.isNotEmpty() }

    fun encode(feeds: List<RssFeed>): String = JsonArray(feeds.map(::jsonOf)).toString()

    /** [feed] followed: first, and once. */
    fun followed(feeds: List<RssFeed>, feed: RssFeed): List<RssFeed> = listOf(feed) + feeds.filter { it.id != feed.id }

    fun unfollowed(feeds: List<RssFeed>, id: String): List<RssFeed> = feeds.filter { it.id != id }

    /**
     * What a backup's rows ([rows]) leave followed: the feeds they name,
     * those followed already in their place, the others after them in the
     * rows' order.
     */
    fun restored(current: List<RssFeed>, rows: List<RssFeed>): List<RssFeed> {
        val byId = rows.associateBy { it.id }
        val kept = current.mapNotNull { byId[it.id]?.let { row -> it.copy(feedUrl = row.feedUrl, name = row.name, siteUrl = row.siteUrl, iconUrl = row.iconUrl) } }
        val ids = kept.map { it.id }.toSet()
        return kept + rows.filter { it.id !in ids }
    }

    /** XTA's camel case first, its table's snake case where a feed came from there. */
    private fun feedOf(row: J): RssFeed? {
        if (row.raw !is JsonObject) return null
        val feedUrl = row["feedUrl"].string ?: row["feed_url"].string ?: ""
        return RssFeed(
            id = row["id"].string ?: RssParser.feedId(feedUrl),
            feedUrl = feedUrl,
            name = row["name"].string ?: "",
            siteUrl = row["siteUrl"].string ?: row["site_url"].string,
            iconUrl = row["iconUrl"].string ?: row["icon_url"].string,
            description = row["description"].string,
        )
    }

    private fun jsonOf(feed: RssFeed): JsonObject = buildJsonObject {
        put("id", feed.id)
        put("feedUrl", feed.feedUrl)
        put("name", feed.name)
        put("siteUrl", feed.siteUrl)
        put("iconUrl", feed.iconUrl)
        put("description", feed.description)
    }
}

/** The items read, as XTA keeps them in `plugin.rss.read_ids`: newest first, the latest [CAP]. */
object RssReadIds {
    const val CAP = 800

    fun decode(text: String?): List<String> = J.parse(text.orEmpty()).list.mapNotNull { it.string?.ifEmpty { null } }

    fun encode(ids: List<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()

    /** [ids] with each of [read] not read yet in front; the same list when there is none. */
    fun marked(ids: List<String>, read: Iterable<String>): List<String> {
        val known = ids.toSet()
        val fresh = read.filter { it.isNotEmpty() && it !in known }.distinct()
        return if (fresh.isEmpty()) ids else (fresh + ids).take(CAP)
    }
}

/** Each feed's tags, as XTA keeps them in `plugin.rss.tags`: `{"<feed id>": ["News", "Blogs"]}`. */
object RssTags {
    private val separators = Regex("[,/]")

    fun decode(text: String?): Map<String, List<String>> {
        val root = J.parse(text.orEmpty())
        val map = root.raw as? JsonObject ?: return emptyMap()
        return map.keys.mapNotNull { id ->
            val tags = root[id].takeIf { it.raw is JsonArray }?.list ?: return@mapNotNull null
            id to tags.mapNotNull { it.string?.trim()?.ifEmpty { null } }
        }.toMap()
    }

    fun encode(tags: Map<String, List<String>>): String =
        JsonObject(tags.mapValues { (_, list) -> JsonArray(list.map(::JsonPrimitive)) }).toString()

    /** [tags] with [feedId]'s set to [chosen], trimmed and without empties; a feed left with none has no entry. */
    fun withTags(tags: Map<String, List<String>>, feedId: String, chosen: List<String>): Map<String, List<String>> {
        val cleaned = chosen.map { it.trim() }.filter { it.isNotEmpty() }
        return if (cleaned.isEmpty()) tags - feedId else tags + (feedId to cleaned)
    }

    /** Every tag, once each, sorted, as the filter row offers them. */
    fun all(tags: Map<String, List<String>>): List<String> = tags.values.flatten().toSortedSet().toList()

    /** What the reader typed in the tag dialog, split at commas and slashes. */
    fun parse(input: String): List<String> = input.split(separators).map { it.trim() }.filter { it.isNotEmpty() }

    /** What the tag dialog starts with: the feed's tags, comma-separated. */
    fun field(tags: Map<String, List<String>>, feedId: String): String = tags[feedId].orEmpty().joinToString(", ")
}

/** Which of the timeline's items the reader asked for: all, or the unread ones. */
enum class RssFilter { ALL, UNREAD }

/** What the RSS tab shows of its timeline, as XTA's RssTimelineStore and its filters decide. */
object RssTimelineRules {
    /** How long a timeline is fresh: a pane coming back into view reads nothing new before. */
    val FRESH_FOR: Duration = Duration.ofMinutes(10)

    fun fresh(fetchedAt: Instant?, now: Instant): Boolean = fetchedAt != null && Duration.between(fetchedAt, now) < FRESH_FOR

    /** Whether [item] passes [filter] and, when one is picked, [tag] (by its feed's tags). */
    fun matches(item: RssItem, filter: RssFilter, read: Set<String>, tag: String?, tagsByFeed: Map<String, List<String>>): Boolean {
        if (filter == RssFilter.UNREAD && item.id in read) return false
        if (tag.isNullOrEmpty()) return true
        return tag in tagsByFeed[item.feedId].orEmpty()
    }

    fun shown(items: List<RssItem>, filter: RssFilter, read: Set<String>, tag: String?, tagsByFeed: Map<String, List<String>>): List<RssItem> =
        items.filter { matches(it, filter, read, tag, tagsByFeed) }

    /** Mark all as read is offered while the home pane has an unread item. */
    fun anyUnread(items: List<RssItem>, read: Set<String>): Boolean = items.any { it.id !in read }
}
