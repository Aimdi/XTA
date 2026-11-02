package com.aimdi.xtanative.store.reading

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.StoredJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Where a reader stands in an article: how far down ([fraction], 0 to 1),
 * and the block at the top of the screen with how far its top sits above (a
 * negative [leading]) or below the top, so the place survives a change of
 * text size. [updatedAt] orders the journal.
 */
data class ArticleReadPoint(
    val fraction: Double = 0.0,
    val paragraph: Int = 0,
    val leading: Double = 0.0,
    val completed: Boolean = false,
    /** Epoch milliseconds. */
    val updatedAt: Long = 0,
) {
    /** XTA's JSON for it, as its journal and reader bridge write it. */
    fun toJson(): JsonObject = buildJsonObject {
        put("fraction", fraction)
        put("paragraph", paragraph)
        put("leading", leading)
        put("completed", completed)
        put("updatedAt", updatedAt)
    }

    companion object {
        /** A place from JSON anyone may have written: every number is bounded, and anything else reads as the start. */
        fun parse(raw: J): ArticleReadPoint = ArticleReadPoint(
            fraction = number(raw["fraction"], 0.0).coerceIn(0.0, 1.0),
            paragraph = number(raw["paragraph"], 0.0).toInt().coerceIn(0, 100_000),
            leading = number(raw["leading"], 0.0).coerceIn(-100_000.0, 100_000.0),
            completed = raw["completed"].boolean == true,
            updatedAt = number(raw["updatedAt"], 0.0).toLong(),
        )

        private fun number(value: J, fallback: Double): Double =
            (value.raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() } ?: fallback
    }
}

/** The article readers' type: size from 16 to 28 and line height from 1.4 to 2.2, shared by every network's reader. */
data class ReadingAppearance(val fontSize: Double = 18.0, val lineHeight: Double = 1.7) {
    /** This appearance within the bounds. */
    fun bounded(): ReadingAppearance = ReadingAppearance(fontSize.coerceIn(16.0, 28.0), lineHeight.coerceIn(1.4, 2.2))

    fun toJson(): JsonObject = buildJsonObject {
        put("fontSize", fontSize)
        put("lineHeight", lineHeight)
    }

    companion object {
        /** XTA's JSON for it (`reading.appearance.v1`); out-of-range values are brought within bounds. */
        fun parse(raw: J): ReadingAppearance {
            fun number(key: String, fallback: Double) =
                (raw[key].raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() } ?: fallback
            return ReadingAppearance(number("fontSize", 18.0), number("lineHeight", 1.7)).bounded()
        }
    }
}

/**
 * The places readers left articles at, by article id, in
 * `reading/<name>.json`: the newest [LIMIT] are kept. Readers whose places
 * must never leave the device keep a journal of their own [name], out of
 * backups.
 */
class ReadingJournal(dir: StoreDir, name: String = DEFAULT) {
    private val stored = StoredJson(dir, "reading/$name.json", emptyMap(), ::decode, ::encode)

    suspend fun point(articleId: String): ArticleReadPoint? = stored.get()[articleId]

    /** Notes [point] for [articleId], keeping only the [LIMIT] places updated last. */
    suspend fun put(articleId: String, point: ArticleReadPoint) {
        stored.update { places -> newest(places + (articleId to point)) }
    }

    /** Empties this journal. */
    suspend fun forget() {
        stored.update { emptyMap() }
    }

    /** Every place, for the backup. */
    suspend fun content(): Map<String, ArticleReadPoint> = stored.get()

    suspend fun replace(places: Map<String, ArticleReadPoint>) {
        stored.update { newest(places) }
    }

    private fun encode(places: Map<String, ArticleReadPoint>) = buildJsonObject {
        put("v", 1)
        put("places", toXta(places))
    }

    private fun decode(root: J): Map<String, ArticleReadPoint>? = if (root["v"].int != 1) null else fromXta(root["places"])

    companion object {
        /** XTA's article journal, `reading.articles.v1`. */
        const val DEFAULT = "articles"
        const val LIMIT = 300

        fun newest(places: Map<String, ArticleReadPoint>): Map<String, ArticleReadPoint> =
            places.entries.sortedByDescending { it.value.updatedAt }.take(LIMIT).associate { it.key to it.value }

        /** XTA's journal, a JSON object of places by article id. */
        fun fromXta(journal: J): Map<String, ArticleReadPoint> = journal.keys.associateWith { ArticleReadPoint.parse(journal[it]) }

        fun toXta(places: Map<String, ArticleReadPoint>): JsonObject = JsonObject(places.mapValues { it.value.toJson() })
    }
}

/** The readers' type, in `reading/appearance.json`. */
class ReadingAppearanceStore(dir: StoreDir) {
    private val stored = StoredJson(
        dir,
        "reading/appearance.json",
        ReadingAppearance(),
        { root -> if (root.raw is JsonObject) ReadingAppearance.parse(root) else null },
        { it.toJson() },
    )

    suspend fun get(): ReadingAppearance = stored.get()

    suspend fun set(appearance: ReadingAppearance) {
        val bounded = appearance.bounded()
        stored.update { if (it == bounded) it else bounded }
    }
}
