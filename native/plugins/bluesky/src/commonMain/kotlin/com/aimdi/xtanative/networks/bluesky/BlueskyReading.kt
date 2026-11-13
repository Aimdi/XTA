package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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

/**
 * Where Following was left (XTA's PluginReadingPosition): the posts kept
 * around it, the post first on screen ([anchor], its URI) and where that
 * post's top was against the list's ([leading], at or above it).
 */
data class BlueskyReadPoint(val posts: List<BlueskyPost>, val anchor: String, val leading: Double = 0.0)

object BlueskyReadingPrefs {
    private const val VERSION = 1

    /** XTA reads nothing from a value longer than this. */
    private const val MAX_LENGTH = 2_500_000

    /** XTA writes no posts once the value would pass this. */
    private const val MAX_WRITTEN = 2_000_000

    /** blueskyReadingLimit: the posts a place keeps. */
    const val POSTS = 144

    /** The posts a place keeps from before its anchor, as XTA's reading view does. */
    private const val BEFORE = 24

    private const val MAX_LEADING = 10_000.0

    /**
     * The place in [text] when it was kept for [source] (the AppView read);
     * null for another, or none, or a damaged one, as XTA's reader store
     * reads it.
     */
    fun point(text: String, source: String): BlueskyReadPoint? {
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        val json = J.parse(text)
        if (json["version"].long != VERSION.toLong() || json["source"].string != source) return null
        val posts = json["posts"].list.take(POSTS).mapNotNull { BlueskySnapshot.post(it) }
            .filter { it.uri.startsWith("at://") && it.url.startsWith("https://") }
        if (posts.isEmpty()) return null
        val leading = json["leading"].double?.takeIf { it.isFinite() }?.coerceIn(-MAX_LEADING, MAX_LEADING) ?: 0.0
        return BlueskyReadPoint(posts, json["anchor"].string ?: posts.first().uri, leading)
    }

    /** [point] written over [text] for [source], keeping the tab and the filters; null clears it. Its posts go when they would make it too long, as XTA drops them. */
    fun write(text: String, source: String, point: BlueskyReadPoint?): String {
        val kept = (J.parse(text).raw as? JsonObject)?.takeIf { it["version"] == JsonPrimitive(VERSION) }.orEmpty()
        fun written(posts: List<BlueskyPost>) = JsonObject(
            kept + mapOf(
                "version" to JsonPrimitive(VERSION),
                "source" to JsonPrimitive(source),
                "anchor" to (point?.anchor?.let(::JsonPrimitive) ?: JsonNull),
                "leading" to JsonPrimitive(point?.leading ?: 0.0),
                "posts" to JsonArray(posts.map(BlueskySnapshot::json)),
            ),
        ).toString()
        val full = written(point?.posts.orEmpty().take(POSTS))
        return if (full.length > MAX_WRITTEN) written(emptyList()) else full
    }

    /** What a place keeps of [posts] (all Following holds) around its [anchor]: from two dozen before it, at most [POSTS]. */
    fun window(posts: List<BlueskyPost>, anchor: String): List<BlueskyPost> {
        val at = posts.indexOfFirst { it.uri == anchor }.coerceAtLeast(0)
        return posts.drop((at - BEFORE).coerceAtLeast(0)).take(POSTS)
    }

    /** Of a place's [posts], those by or reposted by an account still followed ([follows] tells by handle or DID), as XTA's restorePosts keeps them. */
    fun kept(posts: List<BlueskyPost>, follows: (String) -> Boolean): List<BlueskyPost> =
        posts.filter { post -> listOfNotNull(post.handle, post.did, post.repostedByHandle, post.repostedByDid).any { it.isNotEmpty() && follows(it) } }

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
