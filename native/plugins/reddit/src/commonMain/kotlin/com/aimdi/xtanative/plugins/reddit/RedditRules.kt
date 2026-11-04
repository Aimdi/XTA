package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.NetworkFailure
import com.aimdi.xtanative.networks.reddit.RedditMedia
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditSort
import com.aimdi.xtanative.networks.reddit.RedditTimeFilter
import com.aimdi.xtanative.networks.reddit.filterRedditPosts
import com.aimdi.xtanative.networks.reddit.normaliseSubreddit
import com.aimdi.xtanative.store.settings.RedditFeedMode
import com.aimdi.xtanative.store.settings.RedditNsfwMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSource
import com.aimdi.xtanative.store.settings.Settings
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.aimdi.xtanative.store.settings.RedditSort as StoredSort
import com.aimdi.xtanative.store.settings.RedditTimeFilter as StoredWindow

/** The listing order Reddit is asked for, for the one the reader chose. */
internal fun StoredSort.listing(): RedditSort = when (this) {
    StoredSort.HOT -> RedditSort.HOT
    StoredSort.NEWEST -> RedditSort.NEW
    StoredSort.TOP -> RedditSort.TOP
    StoredSort.RISING -> RedditSort.RISING
    StoredSort.CONTROVERSIAL -> RedditSort.CONTROVERSIAL
}

internal fun StoredWindow.listing(): RedditTimeFilter = RedditTimeFilter.valueOf(name)

/** Top and controversial are ranked within a time window; the others are not. */
internal val StoredSort.usesTimeFilter: Boolean get() = this == StoredSort.TOP || this == StoredSort.CONTROVERSIAL

/** Whether [name] is among these communities, compared as Reddit compares names: whatever the case. */
internal fun List<String>.follows(name: String): Boolean = any { it.equals(name, ignoreCase = true) }

/** What the reader chose of how Reddit is read and shown, as every listing asks it. */
internal data class RedditChoices(
    val sort: StoredSort,
    val window: StoredWindow,
    val nsfw: RedditNsfwMode,
    val showSpoilers: Boolean,
    val preferPublic: Boolean,
) {
    val hidesNsfw: Boolean get() = nsfw == RedditNsfwMode.HIDE

    /** [posts] without the over-18 ones when the reader hides them. */
    fun shown(posts: List<RedditPost>): List<RedditPost> = filterRedditPosts(posts, hidesNsfw)

    /** What covers [post]'s media until it is asked for: a spoiler while spoilers are hidden, then the NSFW rule; null when nothing does. */
    fun gate(post: RedditPost): MediaGate? = when {
        post.spoiler && !showSpoilers -> MediaGate.SPOILER
        post.over18 && nsfw != RedditNsfwMode.SHOW -> MediaGate.NSFW
        else -> null
    }

    companion object {
        fun of(settings: Settings) = RedditChoices(
            sort = settings[RedditPrefs.sort],
            window = settings[RedditPrefs.timeFilter],
            nsfw = settings[RedditPrefs.nsfwMode],
            showSpoilers = settings[RedditPrefs.showSpoilers],
            preferPublic = settings[RedditPrefs.source] == RedditSource.PUBLIC,
        )
    }
}

internal enum class MediaGate { NSFW, SPOILER }

/** What a post shows under its words, in XTA's order of preference. */
internal sealed interface PostMedia {
    data class Video(val stream: String) : PostMedia

    data class Gallery(val images: List<String>) : PostMedia

    data class Image(val url: String) : PostMedia

    /** A link whose pictures are in the post's own JSON: a gallery an old.reddit listing carried as a link. */
    data class GalleryLink(val url: String) : PostMedia

    data class Link(val url: String) : PostMedia
}

internal object RedditMediaRules {
    /** The tallest shape a framed piece of media may take, and the widest. */
    const val MIN_RATIO = 9.0 / 16.0
    const val MAX_RATIO = 16.0 / 9.0

    fun of(post: RedditPost): PostMedia? {
        post.playableVideoUrl?.let { return PostMedia.Video(stream(it)) }
        val gallery = RedditMedia.collapseImageUrls(post.galleryImages)
        if (gallery.size > 1) return PostMedia.Gallery(gallery)
        post.imageUrl?.let { return PostMedia.Image(it) }
        val link = post.url?.takeIf { !post.isSelf } ?: return null
        return if (RedditMedia.isGalleryUrl(link)) PostMedia.GalleryLink(link) else PostMedia.Link(link)
    }

    /**
     * What the app's players play of a v.redd.it video: its HLS playlist,
     * which carries the sound as well. A DASH manifest or the silent fallback
     * file names the same video by its id.
     */
    fun stream(url: String): String {
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.host == "v.redd.it" } ?: return url
        if (parsed.encodedPath.endsWith(".m3u8")) return url
        val id = parsed.pathSegments.firstOrNull { it.isNotEmpty() } ?: return url
        return "https://v.redd.it/$id/HLSPlaylist.m3u8"
    }

    /** A shape safe to lay a card out around: Reddit reports one for its own videos only. */
    fun aspectRatio(reported: Double?, fallback: Double = MAX_RATIO): Double =
        reported?.takeIf { it.isFinite() && it > 0 }?.coerceIn(MIN_RATIO, MAX_RATIO) ?: fallback

    /** The site a link card names: Reddit's own summary, else the link's host, without `www.`. */
    fun domain(post: RedditPost, url: String): String = (post.domain ?: url.toHttpUrlOrNull()?.host ?: url).removePrefix("www.")
}

/** What the Reddit tab shows: a discovery rail, or one followed community (XTA's `RedditHomeSource`). */
internal data class RedditHomeSource(val mode: RedditFeedMode = RedditFeedMode.FOLLOWING, val subreddit: String? = null) {
    val viewingSubreddit: Boolean get() = !subreddit.isNullOrEmpty()

    /** The open feed's stable id: a followed community is `r/<name>`, never a rail. */
    val key: String get() = subreddit?.takeIf { it.isNotEmpty() }?.let { "r/" + it.lowercase() } ?: mode.stored

    fun railSelected(rail: RedditFeedMode): Boolean = !viewingSubreddit && mode == rail

    fun isSelected(name: String): Boolean = subreddit?.equals(name, ignoreCase = true) == true

    companion object {
        fun of(settings: Settings) = RedditHomeSource(
            mode = settings[RedditPrefs.feedMode],
            subreddit = settings[RedditPrefs.selectedSubreddit].takeIf { it.isNotEmpty() }?.let(::normaliseSubreddit),
        )

        fun selectingMode(settings: Settings, mode: RedditFeedMode): Settings =
            settings.with(RedditPrefs.feedMode, mode).with(RedditPrefs.selectedSubreddit, "")

        fun selectingSubreddit(settings: Settings, name: String): Settings =
            normaliseSubreddit(name)?.let { settings.with(RedditPrefs.selectedSubreddit, it) } ?: settings

        /** A community kept open that is no longer followed does not open again. */
        fun reconciled(settings: Settings, followed: List<String>): Settings {
            val open = of(settings).subreddit ?: return settings
            return if (followed.any { it.equals(open, ignoreCase = true) }) settings else settings.with(RedditPrefs.selectedSubreddit, "")
        }
    }
}

/** How a Reddit read failed, in terms the reader can act on (XTA's `RedditErrorKind`). */
internal enum class RedditTrouble {
    CLIENT_ID,
    BLOCKED,
    NOT_FOUND,
    RATE_LIMITED,
    RESPONSE,
    NETWORK,
    ;

    companion object {
        fun of(error: Throwable): RedditTrouble = when (error) {
            is NetworkFailure -> ofStatus(error.code)
            is IOException -> NETWORK
            else -> RESPONSE
        }

        fun ofStatus(code: Int?): RedditTrouble = when (code) {
            401 -> CLIENT_ID
            403 -> BLOCKED
            404 -> NOT_FOUND
            429 -> RATE_LIMITED
            else -> RESPONSE
        }
    }
}

/** Colours readable on the page they are drawn on (XTA's `ensureContrast`), as ARGB. */
internal object RedditContrast {
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [foreground], or the nearest darker or lighter shade of it that reaches [minRatio] against [background]. */
    fun ensure(foreground: Int, background: Int, minRatio: Double = 4.5): Int {
        if (ratio(foreground, background) >= minRatio) return foreground
        val target = if (luminance(background) > 0.18) BLACK else WHITE
        return (1..20).asSequence().map { mix(foreground, target, it / 20.0) }.firstOrNull { ratio(it, background) >= minRatio } ?: target
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun channel(shift: Int): Int = Math.round(((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
