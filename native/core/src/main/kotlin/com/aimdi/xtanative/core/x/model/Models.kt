package com.aimdi.xtanative.core.x.model

import java.time.Instant

/** An X account as a post or a profile shows it. */
data class XUser(
    val id: String,
    val screenName: String,
    val name: String,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    val protected: Boolean = false,
    val description: String? = null,
    val bannerUrl: String? = null,
    val location: String? = null,
    val website: String? = null,
    val followers: Long? = null,
    val following: Long? = null,
    val posts: Long? = null,
    val createdAt: Instant? = null,
    /** The `t.co` links in [description], with where they lead. */
    val descriptionLinks: List<XEntity.Link> = emptyList(),
) {
    /** The avatar at 200 × 200 instead of X's 48 × 48 `_normal`. */
    val largeAvatarUrl: String? get() = avatarUrl?.replace("_normal.", "_200x200.")
}

/** A span of a post's text, in code points of the text as X sends it (HTML-escaped). */
sealed interface XEntity {
    val start: Int
    val end: Int

    data class Link(override val start: Int, override val end: Int, val url: String, val expanded: String, val display: String) : XEntity
    data class Mention(override val start: Int, override val end: Int, val screenName: String) : XEntity
    data class Hashtag(override val start: Int, override val end: Int, val tag: String) : XEntity
    data class Cashtag(override val start: Int, override val end: Int, val symbol: String) : XEntity
}

data class XVideoVariant(val url: String, val bitrate: Int?, val contentType: String)

data class XMedia(
    val kind: Kind,
    /** The picture, or a video's poster. */
    val url: String,
    val width: Int?,
    val height: Int?,
    val alt: String? = null,
    val variants: List<XVideoVariant> = emptyList(),
    val durationMs: Long? = null,
    /** The `t.co` link X put in the text for it, cut from what is shown. */
    val shortUrl: String? = null,
) {
    enum class Kind { PHOTO, VIDEO, GIF }

    val aspectRatio: Float? get() = if (width != null && height != null && height > 0) width.toFloat() / height else null

    /** The progressive MP4s, highest bitrate first: what the player picks among. */
    val mp4s: List<XVideoVariant> get() = variants.filter { it.contentType == "video/mp4" }.sortedByDescending { it.bitrate ?: 0 }
}

/** What X attaches below a post's text besides media: a link preview or a poll. */
sealed interface XCard {
    /** A link preview. [large] draws the picture above the title, as `summary_large_image` and `player` do. */
    data class Link(
        val url: String,
        val title: String,
        val description: String?,
        val domain: String?,
        val imageUrl: String?,
        val imageWidth: Int?,
        val imageHeight: Int?,
        val large: Boolean,
    ) : XCard

    /** A poll, as counted when the post was read. Read only: XTA Native never votes. */
    data class Poll(val choices: List<Choice>, val endsAt: Instant?, val final: Boolean) : XCard {
        data class Choice(val label: String, val votes: Long, val imageUrl: String? = null)

        val total: Long get() = choices.sumOf { it.votes }
    }
}

/**
 * A post. A repost is the reposter's post with [reposted] holding the original,
 * as X sends it; [shown] is what a card draws.
 */
data class XPost(
    val id: String,
    val author: XUser,
    val createdAt: Instant?,
    /** The text as X sends it, HTML-escaped; [entities] index into it in code points. */
    val text: String,
    val displayStart: Int = 0,
    val displayEnd: Int? = null,
    val entities: List<XEntity> = emptyList(),
    val media: List<XMedia> = emptyList(),
    val card: XCard? = null,
    val quoted: XPost? = null,
    val reposted: XPost? = null,
    val replyCount: Long = 0,
    val repostCount: Long = 0,
    val quoteCount: Long = 0,
    val likeCount: Long = 0,
    val bookmarkCount: Long = 0,
    val viewCount: Long? = null,
    val replyToScreenName: String? = null,
    val conversationId: String? = null,
) {
    val shown: XPost get() = reposted ?: this
    val isRepost: Boolean get() = reposted != null
    val url: String get() = "https://x.com/${author.screenName}/status/$id"
}

/** A post X would not give, in its place in a conversation. */
data class XWithheld(val id: String, val reason: String?)

/** One row of a timeline: a post, or a thread X groups as a conversation. */
data class XThread(val id: String, val posts: List<XPost>)

data class XTimelinePage(
    val threads: List<XThread>,
    val bottomCursor: String?,
    val topCursor: String?,
    val pinnedIds: Set<String> = emptySet(),
)
