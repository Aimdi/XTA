package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The tabs of XTA's Bluesky screen, in its order. */
enum class BlueskyTab { FOLLOWING, DISCOVER, LISTS, LIKED }

/**
 * What XTA's Bluesky screen remembers between launches
 * (`plugin.bluesky.reader.v1`, bluesky_reader_store.dart): the tab open, and
 * the filters of the Following and Liked lists. A filter's words are not
 * kept, as XTA keeps none.
 */
data class BlueskyReading(
    val tab: BlueskyTab = BlueskyTab.FOLLOWING,
    val following: BlueskyReaderFilter = BlueskyReaderFilter(),
    val likes: BlueskyReaderFilter = BlueskyReaderFilter(),
)

object BlueskyReadingPrefs {
    private const val VERSION = 1

    /** XTA reads nothing from a value longer than this. */
    private const val MAX_LENGTH = 2_500_000

    fun parse(text: String): BlueskyReading {
        if (text.isEmpty() || text.length > MAX_LENGTH) return BlueskyReading()
        val json = J.parse(text)
        if (json["version"].long != VERSION.toLong()) return BlueskyReading()
        val tab = (json["tab"].long ?: 0).coerceIn(0, BlueskyTab.entries.size - 1L).toInt()
        return BlueskyReading(BlueskyTab.entries[tab], filter(json["options"]["following"]), filter(json["options"]["likes"]))
    }

    /** [reading] written over [text], keeping what else XTA wrote there: its reading place and its posts. */
    fun write(text: String, reading: BlueskyReading): String {
        val kept = (J.parse(text).raw as? JsonObject)?.takeIf { it["version"] == JsonPrimitive(VERSION) }.orEmpty()
        val options = JsonObject(mapOf("following" to json(reading.following), "likes" to json(reading.likes)))
        return JsonObject(kept + mapOf("version" to JsonPrimitive(VERSION), "tab" to JsonPrimitive(reading.tab.ordinal), "options" to options)).toString()
    }

    private fun filter(json: J): BlueskyReaderFilter = BlueskyReaderFilter(
        content = BlueskyReaderFilter.Content.entries.firstOrNull { it.name.equals(json["content"].string, ignoreCase = true) } ?: BlueskyReaderFilter.Content.ALL,
        order = BlueskyReaderFilter.Order.entries.firstOrNull { it.name.equals(json["order"].string, ignoreCase = true) } ?: BlueskyReaderFilter.Order.FEED,
        hideReplies = json["hideReplies"].boolean ?: false,
        hideReposts = json["hideReposts"].boolean ?: false,
    )

    private fun json(filter: BlueskyReaderFilter): JsonObject = JsonObject(
        mapOf(
            "content" to JsonPrimitive(filter.content.name.lowercase()),
            "order" to JsonPrimitive(filter.order.name.lowercase()),
            "hideReplies" to JsonPrimitive(filter.hideReplies),
            "hideReposts" to JsonPrimitive(filter.hideReposts),
        ),
    )
}
