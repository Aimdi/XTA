package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings

/**
 * What a player opens (_video.dart's TweetVideoMetadata and the settings it
 * reads): [url] to start on, the [qualities] its menu offers, whether it
 * loops and starts without sound, whether it is a GIF, which loops
 * silently without controls and pauses no other video, and where it starts
 * ([startMs], where the reader left it: [VideoPlaces]).
 */
internal data class VideoSource(
    val url: String,
    val qualities: List<VideoQuality> = emptyList(),
    val loop: Boolean = false,
    val muted: Boolean = true,
    val gif: Boolean = false,
    val startMs: Long = 0,
) {
    companion object {
        /**
         * [media] as Settings › Media plays it: at the video quality's
         * variant, looping as Loop videos says, with the sound as the app's
         * mute stands; a GIF loops without sound. Null with nothing to play.
         */
        fun of(media: XMedia, settings: Settings?): VideoSource? {
            val prefs = settings ?: Settings()
            val qualities = VideoRules.qualities(media)
            val url = VideoRules.defaultUrl(qualities, VideoRules.streamUrl(media), prefs[MediaPrefs.videoQuality]) ?: return null
            return of(url, media.kind == XMedia.Kind.GIF, settings).copy(qualities = qualities)
        }

        /** A network's file at [url] (one variant, no menu of qualities) as Settings › Media plays it, as XTA plays a plugin's video. */
        fun of(url: String, gif: Boolean, settings: Settings?): VideoSource {
            val prefs = settings ?: Settings()
            return VideoSource(url, loop = gif || prefs[MediaPrefs.loop], muted = gif || VideoMute.muted(prefs[MediaPrefs.mute]), gif = gif)
        }

        /**
         * A network's video offering [qualities] (highest first; [url] its
         * own file where it offers none) as Settings › Media plays it: at the
         * video quality's pick among them, with the quality menu where there
         * are several. Null with nothing to play.
         */
        fun of(url: String?, qualities: List<VideoQuality>, gif: Boolean, settings: Settings?): VideoSource? {
            val prefs = settings ?: Settings()
            val picked = VideoRules.defaultUrl(qualities, url, prefs[MediaPrefs.videoQuality]) ?: return null
            return of(picked, gif, settings).copy(qualities = qualities.takeIf { it.size > 1 }.orEmpty())
        }

        /** The silent looping preview at [url] a plugin's tile plays in a feed. */
        fun looping(url: String) = VideoSource(url, loop = true, muted = true, gif = true)
    }
}

/**
 * What a player is made with, from Settings › Media (_video.dart's _acquire
 * and build): how far ahead it reads, whether its frames go straight to the
 * screen, whether it plays on when the app is left, and whether other apps
 * play on beside it.
 */
internal data class VideoOptions(
    val prefetchSeconds: Int = 0,
    val directDecoding: Boolean = false,
    val backgroundPlay: Boolean = true,
    val mixWithOthers: Boolean = false,
) {
    companion object {
        fun of(settings: Settings?): VideoOptions {
            val prefs = settings ?: Settings()
            return VideoOptions(
                prefetchSeconds = prefs[MediaPrefs.prefetchSeconds],
                directDecoding = prefs[MediaPrefs.directHardwareDecoding],
                backgroundPlay = prefs[MediaPrefs.backgroundPlayback],
                mixWithOthers = prefs[MediaPrefs.backgroundPlaybackWithOtherApps],
            )
        }
    }
}

/**
 * How far ahead a player reads and what it keeps behind, in milliseconds and
 * bytes: Media3's buffer durations ([minMs] to [maxMs], [playbackMs] to
 * start, [rebufferMs] after running dry), [backMs] kept of what played, and
 * [targetBytes] in all.
 */
internal data class VideoBuffer(val minMs: Int, val maxMs: Int, val playbackMs: Int, val rebufferMs: Int, val backMs: Int, val targetBytes: Int)

/**
 * Which videos the app keeps on disk as they are read (Media3's cache on
 * Android), so a tile scrolled back to, a GIF's next loop and the same video
 * full screen or in the viewer read nothing again: a file a page's address
 * names, over the web. Never a playlist (X's live broadcasts and Spaces, a
 * network's HLS or DASH): a live one changes as it plays, and its kept copy
 * would stall it.
 */
internal object VideoCaching {
    /** What the cache may hold, the least recently read going first. */
    const val MAX_BYTES = 256L * 1024 * 1024

    private val files = setOf("mp4", "m4v", "mov", "webm", "gif")

    fun caches(url: String): Boolean {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme != "https" && scheme != "http") return false
        val path = url.substringAfter("://").substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        return '.' in name && name.substringAfterLast('.').lowercase() in files
    }
}

/** XTA's bounds on a feed video's reading (_video.dart's libmpv cache), in Media3's terms. */
internal object VideoBuffering {
    /** kVideoReadaheadSeconds: how far a video reads ahead when the reader has set no prefetch. */
    const val READAHEAD_SECONDS = 10

    /** kVideoDemuxerMaxBytes and kVideoDemuxerMaxBackBytes: the bytes ahead, and those kept of what played. */
    const val AHEAD_BYTES = 16 * 1024 * 1024
    const val BACK_BYTES = 4 * 1024 * 1024

    /**
     * How much is read before a video starts: a second, not Media3's two and
     * a half, so a feed's short clip starts as soon as it can; running dry
     * later waits for more ([REBUFFER_MS]).
     */
    private const val PLAYBACK_MS = 1_000
    private const val REBUFFER_MS = 5_000

    /**
     * The reader's prefetch ([prefetchSeconds], 0 for none set), else XTA's
     * ten seconds, read ahead and kept full; enough of what played to skip
     * back by a double tap.
     */
    fun of(prefetchSeconds: Int): VideoBuffer {
        val aheadMs = (if (prefetchSeconds > 0) prefetchSeconds else READAHEAD_SECONDS) * 1000
        return VideoBuffer(
            minMs = aheadMs,
            maxMs = aheadMs,
            playbackMs = minOf(PLAYBACK_MS, aheadMs),
            rebufferMs = minOf(REBUFFER_MS, aheadMs),
            backMs = VideoRules.SEEK_SECONDS * 1000,
            targetBytes = AHEAD_BYTES + BACK_BYTES,
        )
    }
}
