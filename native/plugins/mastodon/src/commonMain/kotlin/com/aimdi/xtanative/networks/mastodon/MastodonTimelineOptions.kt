package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Each timeline's filters and order as XTA keeps them (`timeline.v1`, by the
 * timeline's number: 0 Explore, 1 Local, 2 Federated, 3 Following). The
 * search of loaded posts is not kept, as in XTA.
 */
object MastodonTimelineOptions {
    /** XTA reads no more than this; a longer value leaves every timeline whole. */
    private const val MAX_STORED = 10_000

    /** The options in [raw], every timeline's; anything damaged reads as none set. */
    fun parse(raw: String?): Map<Int, MastodonTimelineFilter> {
        if (raw.isNullOrEmpty() || raw.length > MAX_STORED) return emptyMap()
        val json = J.parse(raw)
        if (json.raw !is JsonObject) return emptyMap()
        return MastodonTimeline.entries.associate { it.ordinal to filter(json["${it.ordinal}"]) }
    }

    fun encode(options: Map<Int, MastodonTimelineFilter>): String =
        JsonObject(options.toSortedMap().entries.associate { (tab, filter) -> "$tab" to json(filter) }).toString()

    private fun filter(json: J): MastodonTimelineFilter = MastodonTimelineFilter(
        content = MastodonTimelineFilter.Content.entries.firstOrNull { it.name.lowercase() == json["content"].string } ?: MastodonTimelineFilter.Content.ALL,
        order = MastodonTimelineFilter.Order.entries.firstOrNull { it.name.lowercase() == json["order"].string } ?: MastodonTimelineFilter.Order.FEED,
        hideBoosts = json["hideBoosts"].boolean ?: false,
        hideReplies = json["hideReplies"].boolean ?: false,
    )

    private fun json(filter: MastodonTimelineFilter): JsonObject = buildJsonObject {
        put("content", filter.content.name.lowercase())
        put("order", filter.order.name.lowercase())
        put("hideBoosts", filter.hideBoosts)
        put("hideReplies", filter.hideReplies)
    }
}

/** The timelines of XTA's Mastodon reader, in its order and by its numbers. */
enum class MastodonTimeline { EXPLORE, LOCAL, FEDERATED, FOLLOWING }
