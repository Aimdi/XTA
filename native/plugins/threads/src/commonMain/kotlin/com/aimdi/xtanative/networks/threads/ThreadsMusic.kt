package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject

/**
 * The song a post plays, as Meta names it: its title and artists, its cover
 * small ([imageUrl]) and large ([largeImageUrl]), the file its preview plays
 * from ([audioUrl]: one audio file, never a manifest), how long the song is
 * ([durationMs]), where its preview starts ([startMs]) and how long it lasts
 * ([lengthMs]), whether it is explicit, and its own page ([pageUrl]). Every
 * address is https; a song without [audioUrl] is named and not played.
 */
data class ThreadsMusic(
    val title: String,
    val artist: String? = null,
    val imageUrl: String? = null,
    val largeImageUrl: String? = null,
    val audioUrl: String? = null,
    val durationMs: Long? = null,
    val startMs: Long = 0,
    val lengthMs: Long? = null,
    val explicit: Boolean = false,
    val pageUrl: String? = null,
) {
    /** "Title · Artist", or the title alone. */
    val line: String get() = listOfNotNull(title, artist).joinToString(" · ")

    /** The cover as large as Meta gave it, for the song's card. */
    val cover: String? get() = largeImageUrl ?: imageUrl

    val playable: Boolean get() = audioUrl != null
}

/**
 * Reads `text_app_music_info` into a [ThreadsMusic], every key as if it
 * might be gone. Seen live (CI's media probe on topic pages):
 * `music_consumable_video.audio_metadata.audio_asset` with `display_title.text`,
 * `display_artist.text`, `display_image.downloadable_uri`,
 * `large_display_image.downloadable_uri`, `dash_manifest`, `song_id`, `tags`
 * and `lyrics`, beside `music_consumable_video.audio_start_time_in_ms`,
 * `is_available_for_consumption` and `muted_reason`. Guessed from
 * Instagram's shape: `music_asset_info` (`title`, `display_artist`,
 * `cover_artwork_uri`, `cover_artwork_thumbnail_uri`,
 * `progressive_download_url`, `duration_in_ms`,
 * `highlight_start_times_in_ms`, `is_explicit`) and
 * `music_consumption_info` (`audio_asset_start_time_in_ms`,
 * `overlap_duration_in_ms`, `should_mute_audio`).
 */
object ThreadsMusicReader {
    fun of(post: J): ThreadsMusic? {
        val info = post["text_app_music_info"]
        if (info.raw !is JsonObject) return null
        val consumable = info["music_consumable_video"]
        val asset = listOf(
            consumable["audio_metadata"]["audio_asset"],
            info["audio_metadata"]["audio_asset"],
            info["music_asset_info"],
            info["music_info"]["music_asset_info"],
        ).firstOrNull { it.raw is JsonObject } ?: return null
        val consumption = listOf(info["music_consumption_info"], info["music_info"]["music_consumption_info"]).firstOrNull { it.raw is JsonObject } ?: J(null)
        val title = said(asset["display_title"]) ?: said(asset["title"]) ?: return null
        val artist = said(asset["display_artist"]) ?: said(asset["artist_name"]) ?: said(asset["display_artist_name"])
        val manifest = asset["dash_manifest"].string ?: consumable["dash_manifest"].string
        val muted = info["is_available_for_consumption"].boolean == false || consumption["should_mute_audio"].boolean == true
        val duration = whole(asset["duration_in_ms"]) ?: whole(consumable["audio_duration_in_ms"]) ?: manifest?.let(ThreadsDash::durationMs)
        return ThreadsMusic(
            title = title,
            artist = artist,
            imageUrl = image(asset["display_image"]) ?: https(asset["cover_artwork_thumbnail_uri"]) ?: image(asset["large_display_image"]) ?: https(asset["cover_artwork_uri"]),
            largeImageUrl = image(asset["large_display_image"]) ?: https(asset["cover_artwork_uri"]),
            audioUrl = if (muted) null else audio(asset, consumable, manifest),
            durationMs = duration,
            startMs = start(consumable, consumption, asset)?.takeIf { duration == null || it < duration } ?: 0,
            lengthMs = whole(consumption["overlap_duration_in_ms"]) ?: whole(consumable["audio_clip_duration_in_ms"]),
            explicit = asset["is_explicit"].boolean == true,
            pageUrl = PAGE_KEYS.firstNotNullOfOrNull { https(asset[it]) ?: https(info[it]) },
        )
    }

    /** A file the app's players take: Meta's progressive download first, else the one audio file the DASH manifest names. */
    private fun audio(asset: J, consumable: J, manifest: String?): String? =
        AUDIO_KEYS.firstNotNullOfOrNull { https(asset[it]) ?: https(consumable[it]) } ?: manifest?.let(ThreadsDash::audioFile)

    private fun start(consumable: J, consumption: J, asset: J): Long? =
        whole(consumable["audio_start_time_in_ms"]) ?: whole(consumption["audio_asset_start_time_in_ms"]) ?: whole(asset["highlight_start_times_in_ms"][0])

    private fun whole(node: J): Long? = node.integer?.takeIf { it in 0..MAX_MS }

    private fun image(node: J): String? = https(node["downloadable_uri"]) ?: https(node["uri"]) ?: https(node["url"]) ?: https(node)

    private fun https(node: J): String? = node.string?.trim()?.takeIf { it.startsWith("https://") && it.length <= MAX_URL }

    /** Words Meta gives as a string or as `{ "text": … }`. */
    private fun said(node: J): String? = (node.string ?: node["text"].string)?.trim()?.ifEmpty { null }

    private val AUDIO_KEYS = listOf("progressive_download_url", "fast_start_progressive_download_url", "audio_url", "preview_url")
    private val PAGE_KEYS = listOf("song_url", "music_url", "external_url", "permalink", "audio_page_url")

    /** A day: no song is longer, so a longer time is no time. */
    private const val MAX_MS = 24L * 60 * 60 * 1000
    private const val MAX_URL = 4096
}

/**
 * What a DASH manifest (MPD) Meta inlines for a song says, read as text
 * without an XML parser: the address of its audio file, where the audio
 * comes as one file (`BaseURL`), and its length. The app's players take a
 * file, not a manifest, so a song whose manifest names only segments has no
 * file here and is not played.
 */
object ThreadsDash {
    /** The https file of the manifest's best audio representation, or null. */
    fun audioFile(manifest: String): String? {
        if (manifest.length > MAX_MANIFEST || !manifest.contains("<MPD", ignoreCase = true)) return null
        val sets = ADAPTATION.findAll(manifest).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val audio = if (sets.isEmpty()) {
            listOf(manifest)
        } else {
            sets.filter { (open, body) -> AUDIO.containsMatchIn(open) || AUDIO.containsMatchIn(body) }.map { it.second }
        }
        return audio.flatMap { body ->
            val inSet = sets.isNotEmpty()
            REPRESENTATION.findAll(body).mapNotNull { match ->
                val open = match.groupValues[1]
                if (!inSet && !AUDIO.containsMatchIn(open)) return@mapNotNull null
                val url = BASE_URL.find(match.groupValues[2])?.groupValues?.get(1)?.let(::unescape)?.trim() ?: return@mapNotNull null
                if (!url.startsWith("https://") || url.length > MAX_URL) return@mapNotNull null
                url to (BANDWIDTH.find(open)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
            }.toList()
        }.maxByOrNull { it.second }?.first
    }

    /** `mediaPresentationDuration` (`PT1M2.5S`) in milliseconds, or null. */
    fun durationMs(manifest: String): Long? {
        val value = DURATION.find(manifest.take(MAX_MANIFEST))?.groupValues?.get(1) ?: return null
        val parts = ISO.matchEntire(value) ?: return null
        val (hours, minutes, seconds) = parts.destructured
        val ms = (hours.toDoubleOrNull() ?: 0.0) * 3_600_000 + (minutes.toDoubleOrNull() ?: 0.0) * 60_000 + (seconds.toDoubleOrNull() ?: 0.0) * 1_000
        return ms.toLong().takeIf { it > 0 }
    }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#38;", "&").replace("&amp;", "&")

    private const val MAX_MANIFEST = 262_144
    private const val MAX_URL = 4096
    private val ADAPTATION = Regex("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val REPRESENTATION = Regex("<Representation\\b([^>]*)>(.*?)</Representation>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BASE_URL = Regex("<BaseURL[^>]*>(.*?)</BaseURL>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val AUDIO = Regex("""(contentType|mimeType)\s*=\s*"audio""", RegexOption.IGNORE_CASE)
    private val BANDWIDTH = Regex("""bandwidth\s*=\s*"(\d{1,12})"""", RegexOption.IGNORE_CASE)
    private val DURATION = Regex("""mediaPresentationDuration\s*=\s*"([^"]{1,40})"""", RegexOption.IGNORE_CASE)
    private val ISO = Regex("""PT(?:([\d.]+)H)?(?:([\d.]+)M)?(?:([\d.]+)S)?""")
}
