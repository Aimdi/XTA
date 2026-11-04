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

/** The searches kept to offer again (plugin_search_history.dart): newest first, each once, twenty of them. */
internal object ThreadsSearchHistory {
    const val CAP = 20

    fun remembered(history: List<String>, query: String): List<String> {
        val value = query.trim()
        if (value.isEmpty()) return history
        return (listOf(value) + history.filter { it != value }).take(CAP)
    }
}

/** What a profile card shows, as XTA's ThreadsProfileCard does. */
internal object ThreadsProfiles {
    /**
     * The link the profile names, unless it is its own Threads page: the bar
     * already opens that, and a guest page has no other.
     */
    fun outsideLink(profile: ThreadsProfile): String? {
        val url = profile.externalUrl?.trim()?.ifEmpty { null } ?: return null
        val own = (ThreadsLinks.parse(url) as? ThreadsLink.Profile)?.handle == profile.username.lowercase()
        return url.takeIf { !own }
    }

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
 * post kept before renditions were plays its one file. The desktop, which
 * has no player of its own, opens the post on the web.
 */
internal object ThreadsMediaItems {
    fun items(post: ThreadsPost, quality: MediaQuality): List<MediaItem> = post.media.map { media -> item(post, media, quality) }

    /** Each item's qualities, highest first, for the viewer's menu. */
    fun qualities(post: ThreadsPost): List<List<VideoQuality>> = post.media.map(::qualities)

    fun qualities(media: ThreadsMedia): List<VideoQuality> =
        media.renditions.mapNotNull { rendition -> rendition.lines?.let { VideoQuality(rendition.url, "${it}p") } }

    private fun item(post: ThreadsPost, media: ThreadsMedia, quality: MediaQuality): MediaItem {
        if (!media.isVideo) return MediaItem(imageUrl = media.url, aspectRatio = media.aspectRatio?.toFloat(), alt = media.alt)
        return MediaItem(
            imageUrl = media.url,
            kind = MediaItem.Kind.VIDEO,
            videoUrl = VideoRules.defaultUrl(qualities(media), media.videoUrl, quality),
            aspectRatio = media.aspectRatio?.toFloat(),
            alt = media.alt,
            inlineVideoUrl = media.renditions.lastOrNull()?.url,
            webUrl = post.url,
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
