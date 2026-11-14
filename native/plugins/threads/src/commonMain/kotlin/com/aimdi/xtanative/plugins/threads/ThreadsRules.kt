package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.media.VideoQuality
import com.aimdi.xtanative.ui.media.VideoRules
import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlin.math.min

/** The session the reader pasted, in the preferences XTA keeps it in, with their opt-in to spend it on followed accounts. */
internal object ThreadsSessions {
    fun of(settings: Settings): ThreadsSession =
        ThreadsSession(settings[ThreadsPrefs.directCookies], settings[ThreadsPrefs.directBearer], settings[ThreadsPrefs.useSessionApis])
}

/** What a profile card shows, as XTA's ThreadsProfileCard does. */
internal object ThreadsProfiles {
    /** [link] as the card shows it: without its scheme, "www." or a bare trailing slash; the tap still opens the whole address. */
    fun linkLabel(link: String): String =
        link.trim().replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").removePrefix("www.").trimEnd('/').ifEmpty { link.trim() }

    /** The counts Meta told, in XTA's order: followers, following, threads; a guest is not told whom a profile follows. */
    fun counts(profile: ThreadsProfile): List<Pair<Long, ThreadsCount>> = listOfNotNull(
        profile.followerCount?.let { it to ThreadsCount.FOLLOWERS },
        profile.followingCount?.let { it to ThreadsCount.FOLLOWING },
        profile.mediaCount?.let { it to ThreadsCount.POSTS },
    )
}

internal enum class ThreadsCount { FOLLOWERS, FOLLOWING, POSTS }

/**
 * A post's pictures and videos for the app's viewer (threads_media.dart): a
 * video plays the file the video-quality setting picks among every one Meta
 * offers, and its quality menu lists those named by their lines ("720p"); a
 * post kept before renditions were plays its one file. The viewer plays it
 * on Android and, through libmpv, on the desktop; a desktop without libmpv
 * opens the post on the web. The viewer and a download take the largest
 * picture Meta offers; a tile the app draws (Saved, a note) takes the size
 * that fills a phone's width, [TILE_WIDTH], rather than the original.
 */
internal object ThreadsMediaItems {
    /** As wide as a tile gets where the app lays it out: a large phone's width in pixels. */
    const val TILE_WIDTH = 1080

    fun items(post: ThreadsPost, quality: MediaQuality): List<MediaItem> = post.media.map { media -> item(post, media, quality) }

    /** Each item's qualities, highest first, for the viewer's menu. */
    fun qualities(post: ThreadsPost): List<List<VideoQuality>> = post.media.map(::qualities)

    /**
     * Where [post]'s [index]th picture or video sits among every one of
     * [among], for a viewer that swipes through a whole conversation's; null
     * when [post] is not one of them (a quote), or they hold nothing more.
     */
    fun galleryStart(among: List<ThreadsPost>, post: ThreadsPost, index: Int): Int? {
        val at = among.indexOfFirst { it.id == post.id }.takeIf { it >= 0 } ?: return null
        if (among.sumOf { it.media.size } <= post.media.size) return null
        return among.take(at).sumOf { it.media.size } + index
    }

    /** A GIF's one clip is no quality to choose. */
    fun qualities(media: ThreadsMedia): List<VideoQuality> =
        if (media.isGif) emptyList() else media.renditions.mapNotNull { rendition -> rendition.lines?.let { VideoQuality(rendition.url, "${it}p") } }

    /**
     * [media] of [post] as a feed tile [width] by [height] pixels draws it:
     * the picture at the size that fills the tile, and a video playing in
     * place the file the video-quality setting picks, as XTA's player does.
     */
    fun tile(post: ThreadsPost, media: ThreadsMedia, quality: MediaQuality, width: Int, height: Int): MediaItem {
        val whole = item(post, media, quality)
        return whole.copy(thumbUrl = media.tileUrl(width, height), inlineVideoUrl = whole.videoUrl)
    }

    /** A picture or video of [post], the viewer's Share giving the post, as XTA's (threads_models.dart). */
    private fun item(post: ThreadsPost, media: ThreadsMedia, quality: MediaQuality): MediaItem {
        val tile = media.tileUrl(TILE_WIDTH).takeIf { it != media.url }
        if (media.isGif && media.isVideo) {
            return MediaItem(imageUrl = media.url, kind = MediaItem.Kind.GIF, videoUrl = media.videoUrl, aspectRatio = media.aspectRatio?.toFloat(), alt = media.alt, shareUrl = post.url)
        }
        if (!media.isVideo) {
            return MediaItem(imageUrl = media.url, aspectRatio = media.aspectRatio?.toFloat(), alt = media.alt, thumbUrl = tile, shareUrl = post.url)
        }
        return MediaItem(
            imageUrl = media.url,
            thumbUrl = tile,
            kind = MediaItem.Kind.VIDEO,
            videoUrl = VideoRules.defaultUrl(qualities(media), media.videoUrl, quality),
            aspectRatio = media.aspectRatio?.toFloat(),
            alt = media.alt,
            inlineVideoUrl = media.renditions.lastOrNull()?.url,
            webUrl = post.url,
            shareUrl = post.url,
        )
    }
}

/**
 * Several pictures or videos along one row at one height, the next one's
 * edge in view, as X lays them out and the Threads app does (media_strip.dart):
 * one keeps the full width at its own shape; several share a height of 0.62
 * of the width, each as wide as its shape there and at most 0.86 of it.
 */
internal object ThreadsStrip {
    data class Layout(val height: Float, val widths: List<Float>)

    const val GAP = 8f

    fun layout(width: Float, aspects: List<Double?>): Layout {
        val shapes = aspects.map(::plugin)
        return when (shapes.size) {
            0 -> Layout(0f, emptyList())
            1 -> Layout(width / shapes.single(), listOf(width))
            else -> {
                val height = width * HEIGHT_FACTOR
                Layout(height, shapes.map { min(height * card(it), width * WIDEST) })
            }
        }
    }

    /**
     * Which tile of a row the dots mark: the [first] in view, the next once
     * more than half of the first ([firstWidth] wide, scrolled [offset]) has
     * left, and the last when the row cannot scroll further ([atEnd]).
     */
    fun shown(first: Int, offset: Int, firstWidth: Float, atEnd: Boolean, count: Int): Int {
        if (count <= 0) return 0
        val index = if (atEnd && (first > 0 || offset > 0)) count - 1 else if (offset > firstWidth / 2) first + 1 else first
        return index.coerceIn(0, count - 1)
    }

    /** The shape a lone picture is shown at: its own, within the bounds a plugin's media keeps (clampPluginMediaAspect). */
    fun plugin(aspect: Double?): Float = when {
        aspect == null || !aspect.isFinite() || aspect <= 0 -> 16f / 9f
        else -> aspect.coerceIn(0.45, 2.4).toFloat()
    }

    /** A card's shape in a row: 4:5 at the tallest, 1.91:1 at the widest. */
    private fun card(aspect: Float): Float = aspect.coerceIn(0.8f, 1.91f)

    private const val HEIGHT_FACTOR = 0.62f
    private const val WIDEST = 0.86f
}
