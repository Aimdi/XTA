package com.aimdi.xtanative.networks.threads

import java.time.Instant

/** One file Meta offers for a video, with its size when Meta gave it. */
data class ThreadsVideoRendition(val url: String, val width: Int? = null, val height: Int? = null) {
    /**
     * The lines a quality is named by: the shorter side, so a 720 × 1280 video
     * is "720p" as a 1280 × 720 one is; the height when Meta gave no width.
     */
    val lines: Int?
        get() = when {
            width != null && height != null -> minOf(width, height)
            width == null && height != null -> height
            else -> null
        }

    val area: Long get() = (width ?: 0).toLong() * (height ?: 0)
}

/**
 * One size of a picture Meta offers (a candidate of `image_versions2`). A side
 * is null when Meta did not say it: the size its address names then stands in
 * where sizes are compared ([ThreadsImages]), never where a shape is drawn.
 */
data class ThreadsImage(val url: String, val width: Int? = null, val height: Int? = null)

/** A picture a post shows, or a video shown by its poster. */
data class ThreadsMedia(
    /** The picture at the largest size Meta offers, or the video's poster: what the viewer shows and a download saves. */
    val url: String,
    /** Width over height, when Meta gave a size. */
    val aspectRatio: Double? = null,
    /** Meta's description of the picture. */
    val alt: String? = null,
    /** Every file Meta offers for a video, largest first: empty for a still. */
    val renditions: List<ThreadsVideoRendition> = emptyList(),
    /** Every size Meta offers of the picture, largest first; empty when only [url] is known. */
    val images: List<ThreadsImage> = emptyList(),
    /** A GIF from GIPHY: [url] its picture, its one rendition the MP4 that loops silently. */
    val isGif: Boolean = false,
) {
    /**
     * The address a tile [width] by [height] pixels loads: the smallest size
     * that still fills it, so a feed neither blurs a picture nor downloads
     * the original for a thumbnail; [url] when no size is known to fit.
     */
    fun tileUrl(width: Int, height: Int = 0): String = ThreadsImages.fitting(images, width, height)?.url ?: url

    /** The file a video plays: the largest Meta offers. */
    val videoUrl: String? get() = renditions.firstOrNull()?.url

    val isVideo: Boolean get() = videoUrl != null
}

/**
 * One run of a caption, as Meta's `text_fragments` split it: the words shown,
 * and where tapping them goes. Meta resolves mentions to accounts and links to
 * their real destination, which a pattern cannot tell from a shortened
 * `example.com/a…` display.
 */
data class ThreadsTextFragment(val kind: Kind, val text: String, val target: String? = null, val spoiler: Boolean = false) {
    /** [target] is the handle for a mention, the unwrapped URL for a link, the tag's name for a tag; a [spoiler] stays covered until tapped. */
    enum class Kind { TEXT, MENTION, LINK, TAG }
}

/** The link preview Meta attaches to a post (`link_preview_attachment`). */
data class ThreadsLinkCard(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val providerName: String? = null,
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrEmpty()
}

/**
 * One Threads post, as much of it as the answer carried. Counts are null when
 * the source did not say (an RSSHub feed never does), so a card never shows a
 * zero it was not given.
 */
data class ThreadsPost(
    /** The post's own id: Meta's `pk`, or the item id an RSSHub feed gave. */
    val id: String,
    /** The author's handle, without the `@`; the original author's for a repost. */
    val handle: String,
    /** The display name, the handle when the source had none. */
    val authorName: String,
    val text: String,
    val avatarUrl: String? = null,
    /** Pictures and videos, in the order the post shows them. */
    val media: List<ThreadsMedia> = emptyList(),
    val publishedAt: Instant? = null,
    /** Where the post lives on Threads. */
    val url: String? = null,
    val likeCount: Long? = null,
    val replyCount: Long? = null,
    val repostCount: Long? = null,
    val quoteCount: Long? = null,
    val linkCard: ThreadsLinkCard? = null,
    /** The post this one quotes, one level deep. */
    val quoted: ThreadsPost? = null,
    /** The topic the author filed the post under, without its `#`: Threads' one tag per post. */
    val topicTag: String? = null,
    /** The caption as Meta split it; empty when it did not, or when the split no longer spells [text]. */
    val fragments: List<ThreadsTextFragment> = emptyList(),
    /** How many more posts the author chained under this one. */
    val selfThreadCount: Int = 0,
    /** Set on a repost: who shared [handle]'s post. */
    val repostedByHandle: String? = null,
    val repostedByName: String? = null,
    val isVerified: Boolean = false,
    val replyToHandle: String? = null,
    val isReply: Boolean = false,
    val facts: ThreadsPostFacts = ThreadsPostFacts.NONE,
    /** Meta lays a cover over the media ([ThreadsMediaParts.covered]): drawn behind a tap where the reader hides sensitive media. */
    val mediaCovered: Boolean = false,
) {
    val hasMedia: Boolean get() = media.isNotEmpty()

    val hasVideo: Boolean get() = media.any { it.isVideo }

    val hasEngagement: Boolean get() = listOf(likeCount, replyCount, repostCount, quoteCount).any { it != null }

    val isRepost: Boolean get() = !repostedByHandle.isNullOrEmpty()

    val hasSpoiler: Boolean get() = fragments.any { it.spoiler }

    /** The account whose page this post came from: the reposter for a repost. */
    val sourceHandle: String get() = repostedByHandle?.takeIf { isRepost } ?: handle

    /** The short code in [url], the one identifier links, shares and parsed posts agree on. */
    val shortcode: String? get() = ThreadsLinks.shortcode(url)

    /** Who a "reposted" line names: the reposter's name, else their handle. */
    val reposterDisplayName: String get() = repostedByName?.trim()?.ifEmpty { null } ?: repostedByHandle.orEmpty()

    /** This post as [handle]'s repost of it, under the repost's own [id] and time. */
    fun repostedBy(id: String, handle: String, name: String, at: Instant? = null): ThreadsPost =
        copy(id = id, publishedAt = at ?: publishedAt, repostedByHandle = handle, repostedByName = name)

    fun withSelfThreadCount(count: Int): ThreadsPost = copy(selfThreadCount = count)

    companion object {
        /** A post known only by its link, as a pasted or shared URL gives it; its conversation reads the rest. */
        fun linkStub(url: String, handle: String = ""): ThreadsPost =
            ThreadsPost(id = url, handle = handle, authorName = handle, text = "", url = url)
    }
}

/**
 * A Threads profile. A count is null when it was not said: a guest page tells
 * no following count, and a profile pieced together from posts knows none.
 */
data class ThreadsProfile(
    val pk: String,
    val id: String,
    val username: String,
    val fullName: String,
    val isVerified: Boolean = false,
    val isPrivate: Boolean = false,
    val profilePicUrl: String = "",
    val biography: String = "",
    val followerCount: Long? = null,
    val followingCount: Long? = null,
    val mediaCount: Long? = null,
    val externalUrl: String? = null,
    /** The largest picture Meta offers of the profile (`hd_profile_pic_versions`); null when it gave only [profilePicUrl]. */
    val hdPictureUrl: String? = null,
    /** The bio as Meta split it and the links under it, when the profile's own page said (ThreadsProfileOwner). */
    val facts: ThreadsProfileFacts = ThreadsProfileFacts(),
) {
    /** The name to show: the display name, or the handle when there is none. */
    val displayName: String get() = fullName.trim().ifEmpty { username }

    /** The picture a profile's own page shows, and its viewer: the largest known. Rows keep [profilePicUrl], the small one. */
    val pictureUrl: String get() = hdPictureUrl?.trim()?.ifEmpty { null } ?: profilePicUrl

    companion object {
        /**
         * The little a profile's own posts say about it, for when its page gave no
         * card: the name and picture of its first post, and no count.
         */
        fun fromPosts(handle: String, posts: List<ThreadsPost>): ThreadsProfile? {
            val key = (ThreadsLinks.normaliseHandle(handle) ?: handle).trim().lowercase()
            if (key.isEmpty() || posts.isEmpty()) return null
            val sample = posts.firstOrNull { it.handle == key } ?: posts.first()
            return ThreadsProfile(pk = "", id = "", username = key, fullName = sample.authorName, profilePicUrl = sample.avatarUrl.orEmpty())
        }
    }
}

/** Someone to suggest following, from the posts already read. */
data class ThreadsPerson(val handle: String, val name: String, val avatarUrl: String?, val fromRepost: Boolean)
