package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * How a feed orders what it read. [CUSTOM] reads as [RECENT]; it is the mode
 * the reader picks to set the rules, which apply in every mode.
 */
enum class FeedSort(val stored: String) {
    RECENT("recent"),
    POPULAR("popular"),
    CUSTOM("custom"),
    ;

    companion object {
        fun parse(raw: String?): FeedSort = entries.firstOrNull { it.stored == raw } ?: RECENT
    }
}

/**
 * What one feed asks for beyond its members, as XTA keeps it on a group's row
 * (the Following feed's on its `-1` row): its own reply and repost choices,
 * null following the session's; its order; its rules; and catch-up mode.
 */
data class FeedSettings(
    val includeReplies: Boolean? = null,
    val includeReposts: Boolean? = null,
    val sort: FeedSort = FeedSort.RECENT,
    val rules: FeedRules = FeedRules(),
    /** Catch-up mode: the feed shows what is new since the last read post and stops there. */
    val catchUp: Boolean = false,
) {
    val isDefault: Boolean get() = this == FeedSettings()

    companion object {
        internal fun json(settings: FeedSettings): JsonObject = buildJsonObject {
            settings.includeReplies?.let { put("replies", it) }
            settings.includeReposts?.let { put("reposts", it) }
            put("sort", settings.sort.stored)
            put("rules", FeedRules.json(settings.rules))
            put("catchUp", settings.catchUp)
        }

        internal fun of(row: J) = FeedSettings(
            includeReplies = row["replies"].boolean,
            includeReposts = row["reposts"].boolean,
            sort = FeedSort.parse(row["sort"].string),
            rules = FeedRules.of(row["rules"]),
            catchUp = row["catchUp"].boolean ?: false,
        )
    }
}
