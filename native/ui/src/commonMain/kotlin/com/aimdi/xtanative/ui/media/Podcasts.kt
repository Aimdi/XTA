package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.json.J
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The podcast episode playing now, and where (podcast_checkpoint.dart):
 * written as it plays, so a restart picks it up. Kept as Substack's
 * `podcast.v1` setting.
 */
data class PodcastCheckpoint(val url: String, val title: String, val positionMs: Long, val durationMs: Long) {
    fun encode(): String = buildJsonObject {
        put("version", 1)
        put("url", url)
        put("title", title.take(MAX_TITLE))
        put("position", positionMs.coerceIn(0, MAX_DURATION_MS))
        put("duration", durationMs.coerceIn(0, MAX_DURATION_MS))
    }.toString()

    companion object {
        private const val MAX_TITLE = 512
        private const val MAX_TEXT = 16_384

        /** A week: no episode is longer, so a longer place is no place. */
        const val MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000

        /** An episode's address: http(s) with a host, of a sane length. */
        fun validUrl(url: String): Boolean {
            if (url.isEmpty() || url.length > 4096) return false
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrEmpty()
        }

        /** The checkpoint in [raw]; null for anything damaged, foreign or out of bounds. */
        fun decode(raw: String?): PodcastCheckpoint? {
            if (raw.isNullOrEmpty() || raw.length > MAX_TEXT) return null
            val json = J.parse(raw)
            val url = json["url"].string.orEmpty()
            val title = json["title"].string.orEmpty()
            val position = json["position"].long ?: -1
            val duration = json["duration"].long ?: -1
            val valid = json["version"].long == 1L && validUrl(url) && title.length <= MAX_TITLE &&
                position in 0..MAX_DURATION_MS && duration in 0..MAX_DURATION_MS && !(duration > 0 && position >= duration)
            return if (valid) PodcastCheckpoint(url, title, position, duration) else null
        }
    }
}

/** Where an episode was left: how far in, of how long. */
data class PodcastPlace(val positionMs: Long, val durationMs: Long)

/**
 * Where each recent episode was left, newest first, by its address
 * (podcast_places.dart): the episode playing now is in the checkpoint, and
 * this is what lets another one resume. Kept as Substack's
 * `podcast.places` setting.
 */
class PodcastPlaces private constructor(private val places: LinkedHashMap<String, PodcastPlace>) {
    fun of(url: String): PodcastPlace? = places[url]

    val size: Int get() = places.size

    /** The places, newest first. */
    val urls: List<String> get() = places.keys.toList()

    /** With [url] left at [positionMs], first, and the oldest beyond the limit dropped; a place at the start is no place. */
    fun leftAt(url: String, positionMs: Long, durationMs: Long): PodcastPlaces {
        if (!PodcastCheckpoint.validUrl(url) || positionMs <= 0) return without(url)
        val next = LinkedHashMap<String, PodcastPlace>()
        next[url] = PodcastPlace(positionMs, durationMs)
        places.entries.filter { it.key != url }.take(LIMIT - 1).forEach { next[it.key] = it.value }
        return PodcastPlaces(next)
    }

    fun without(url: String): PodcastPlaces = if (url in places) PodcastPlaces(LinkedHashMap(places).apply { remove(url) }) else this

    fun encode(): String = JsonArray(
        places.map { (url, place) ->
            buildJsonObject {
                put("url", url)
                put("position", place.positionMs)
                put("duration", place.durationMs)
            }
        },
    ).toString()

    override fun equals(other: Any?): Boolean = other is PodcastPlaces && other.places == places

    override fun hashCode(): Int = places.hashCode()

    companion object {
        const val LIMIT = 50
        private const val MAX_TEXT = 262_144

        val EMPTY = PodcastPlaces(LinkedHashMap())

        /** The places in [raw]: a damaged entry is skipped, a damaged list is empty, and only the first of an address counts. */
        fun decode(raw: String?): PodcastPlaces {
            if (raw.isNullOrEmpty() || raw.length > MAX_TEXT) return EMPTY
            val places = LinkedHashMap<String, PodcastPlace>()
            for (entry in J.parse(raw).list) {
                val url = entry["url"].string.orEmpty()
                val position = entry["position"].long ?: -1
                val duration = entry["duration"].long ?: 0
                if (!PodcastCheckpoint.validUrl(url) || position <= 0 || duration < 0 || url in places) continue
                places[url] = PodcastPlace(position, duration)
                if (places.size == LIMIT) break
            }
            return PodcastPlaces(places)
        }
    }
}
