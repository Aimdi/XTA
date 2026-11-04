package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.store.settings.MediaQuality
import java.util.Locale

/** One MP4 a video can play at, as the quality sheet lists it: `720p`. */
data class VideoQuality(val url: String, val label: String)

/** The rules of the media viewer's videos (_video.dart, _video_controls.dart, video_quality.dart). */
object VideoRules {
    /** Seconds a double tap on either half of a video skips. */
    const val SEEK_SECONDS = 10

    /** The playback speeds offered, slowest first; the sheet lists them fastest first. */
    val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

    /** How long the controls stay after the last touch. */
    const val CONTROLS_SHOWN_MS = 3_000L

    /** The progressive MP4s, highest bitrate first: X's HLS playlist plays poorly, and cannot be saved as a file. */
    fun qualities(media: XMedia): List<VideoQuality> =
        media.mp4s.filter { it.bitrate != null }.map { VideoQuality(it.url, label(it.url, it.bitrate)) }

    /** A variant's name: the height in its address (`…/1280x720/…` is `720p`), else its bitrate, else a dash. */
    fun label(url: String, bitrate: Int?): String {
        resolution.find(url)?.let { return it.groupValues[2] + "p" }
        if (bitrate != null) return String.format(Locale.ROOT, "%.1f Mbps", bitrate / 1_000_000.0)
        return "—"
    }

    private val resolution = Regex("/(\\d+)x(\\d+)/")

    /** What a player streams with no quality to pick from: the best MP4, else X's first variant (a live playlist). */
    fun streamUrl(media: XMedia): String? = qualities(media).firstOrNull()?.url ?: media.variants.firstOrNull()?.url

    /** The variant the video-quality setting picks among [qualities] (highest first); [fallback] when there are none. */
    fun defaultUrl(qualities: List<VideoQuality>, fallback: String?, quality: MediaQuality): String? {
        if (qualities.isEmpty()) return fallback
        val index = when (quality) {
            MediaQuality.THUMB -> qualities.size - 1
            MediaQuality.SMALL -> qualities.size * 3 / 4
            MediaQuality.MEDIUM -> qualities.size / 2
            MediaQuality.LARGE -> 0
        }
        return qualities[index.coerceIn(0, qualities.size - 1)].url
    }

    /** The place [deltaMs] away from [positionMs], kept within the video. */
    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long = (positionMs + deltaMs).coerceIn(0, durationMs.coerceAtLeast(0))

    /** "1:05", or "1:02:05" past an hour, as the controls write a time. */
    fun timeText(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** A speed as the sheet writes it beside its "x": 0.25, 1.0, 1.75. */
    fun speedText(speed: Double): String = speed.toString()

    /** The share of the video [ms] stands for, 0 to 1. */
    fun fraction(ms: Long, durationMs: Long): Float = if (durationMs <= 0) 0f else (ms.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** What the media viewer saves: the file, and the name it is saved under. */
data class MediaSave(val url: String, val fileName: String, val kind: XMedia.Kind, val postId: String)

/** Saves a picture or a video; the downloads give the viewer one. */
fun interface MediaSaver {
    fun save(save: MediaSave)
}

/** Which file of a post's media the viewer saves and shares (_media.dart). */
object MediaFiles {
    /** The size the viewer shows photos at, as the image-quality setting says. */
    fun viewerSize(quality: MediaQuality): PhotoSize = when (quality) {
        MediaQuality.THUMB -> PhotoSize.THUMB
        MediaQuality.SMALL -> PhotoSize.SMALL
        MediaQuality.MEDIUM -> PhotoSize.MEDIUM
        MediaQuality.LARGE -> PhotoSize.LARGE
    }

    /**
     * The file a download saves: a photo as posted; a video's or GIF's MP4,
     * the variant [playingUrl] when it is one (the reader keeps the quality
     * they watch), else the best. On X a GIF is a looping MP4, and its media
     * address only the poster. Null when there is no file to save, as for a
     * stream only.
     */
    fun downloadUrl(media: XMedia, playingUrl: String? = null): String? {
        if (media.kind == XMedia.Kind.PHOTO) return photoUrl(media.url, PhotoSize.ORIGINAL)
        val qualities = VideoRules.qualities(media)
        return if (qualities.any { it.url == playingUrl }) playingUrl else qualities.firstOrNull()?.url
    }

    /** `author-name.ext`, as XTA names a saved file: the author, then the last part of the file's address. */
    fun fileName(username: String, url: String): String {
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val base = path.substringAfterLast('/').ifEmpty { "media" }
        val format = Regex("[?&]format=([A-Za-z0-9]+)").find(url)?.groupValues?.get(1)
        val named = if (format != null && '.' !in base) "$base.$format" else base
        return if (username.isEmpty()) named else "$username-$named"
    }

    /** What a download of [media] in post [postId] by [username] saves; null when there is nothing to save. */
    fun save(media: XMedia, postId: String, username: String, playingUrl: String? = null): MediaSave? {
        val url = downloadUrl(media, playingUrl) ?: return null
        return MediaSave(url, fileName(username, url), media.kind, postId)
    }
}
