package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where a timeline was left, as XTA keeps it (mastodon_reading_store.dart):
 * the posts it showed, as snapshots, the one at the top ([anchor], its
 * address) and how far it was scrolled past ([leading], negative once its top
 * is above the screen's), the [instance] that served it and Explore's [tags].
 */
data class MastodonReadPoint(
    val posts: List<MastodonPost>,
    val anchor: String,
    val leading: Double = 0.0,
    val instance: String? = null,
    val tags: List<MastodonTag> = emptyList(),
)

/** The timeline open last, whether Following showed its accounts, and each timeline's [MastodonReadPoint] by "<surface>:<number>". */
data class MastodonReadingPlaces(val tab: Int = 0, val people: Boolean = false, val points: Map<String, MastodonReadPoint> = emptyMap())

/**
 * XTA's `reading.v1`: kept for one set of instances ([scope]: the reader's
 * instances joined by "|"), which a change of instances forgets. Each point
 * keeps at most [LIMIT] posts and twenty tags; the whole stays under
 * [BUDGET] characters, the points written last kept first.
 */
object MastodonReadingCodec {
    const val LIMIT = 144
    private const val BEFORE = 24
    private const val BUDGET = 2_500_000
    private const val MAX_STORED = 3_000_000
    private const val MAX_TAGS = 20
    private val surfaces = listOf("home", "client")

    fun scope(configured: List<String>): String = configured.joinToString("|")

    /** What a place keeps of [posts] (all a timeline holds) around its [anchor]: from two dozen before it, at most [LIMIT]. */
    fun window(posts: List<MastodonPost>, anchor: String): List<MastodonPost> {
        val at = posts.indexOfFirst { it.url == anchor }.coerceAtLeast(0)
        return posts.drop((at - BEFORE).coerceAtLeast(0)).take(LIMIT)
    }

    /** The places in [raw] when they were kept for [scope]; none for anything else. */
    fun parse(raw: String?, scope: String): MastodonReadingPlaces {
        if (raw.isNullOrEmpty() || raw.length > MAX_STORED) return MastodonReadingPlaces()
        val json = J.parse(raw)
        if (json["scope"].string != scope || json["version"].int != 1) return MastodonReadingPlaces()
        val points = surfaces.flatMap { surface -> MastodonTimeline.entries.map { "$surface:${it.ordinal}" } }
            .mapNotNull { key -> point(json["points"][key])?.let { key to it } }
            .toMap()
        return MastodonReadingPlaces((json["tab"].int ?: 0).coerceIn(0, MastodonTimeline.entries.size - 1), json["people"].boolean ?: false, points)
    }

    /** [places] as kept for [scope]: the points written last first, while they fit. */
    fun encode(places: MastodonReadingPlaces, scope: String): String {
        var budget = BUDGET
        val points = places.points.entries.reversed().mapNotNull { (key, point) ->
            val json = json(point)
            val length = json.toString().length
            if (length > budget) null else (key to json).also { budget -= length }
        }
        return buildJsonObject {
            put("version", 1)
            put("scope", scope)
            put("tab", places.tab)
            put("people", places.people)
            put("points", JsonObject(points.toMap()))
        }.toString()
    }

    private fun point(json: J): MastodonReadPoint? {
        val posts = json["posts"].list.take(LIMIT).mapNotNull { MastodonSnapshot.decode(it.raw) }
        if (posts.isEmpty()) return null
        val leading = json["leading"].double?.takeIf { it.isFinite() }?.coerceIn(-10_000.0, 10_000.0) ?: 0.0
        val tags = json["tags"].list.take(MAX_TAGS).mapNotNull { tag ->
            tag["name"].string?.ifEmpty { null }?.let { MastodonTag(it, tag["url"].string, tag["uses"].long ?: 0) }
        }
        return MastodonReadPoint(posts, json["anchor"].string ?: posts.first().url, leading, json["instance"].string, tags)
    }

    private fun json(point: MastodonReadPoint): JsonObject = buildJsonObject {
        put("posts", JsonArray(point.posts.take(LIMIT).map(MastodonSnapshot::encode)))
        put("anchor", point.anchor)
        put("leading", point.leading)
        put("instance", point.instance)
        put("tags", JsonArray(point.tags.take(MAX_TAGS).map { buildJsonObject { put("name", it.name); put("url", it.url); put("uses", it.uses) } }))
    }
}
