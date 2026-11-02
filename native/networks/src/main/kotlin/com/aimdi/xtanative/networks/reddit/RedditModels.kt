package com.aimdi.xtanative.networks.reddit

import java.time.Instant

/**
 * One Reddit post, as a listing, a search, a profile or its own embed card
 * serves it. Only what a reader needs, every field read defensively, because
 * Reddit adds and removes keys without notice (XTA's `RedditPost`).
 */
data class RedditPost(
    /** The bare id, `abc123`: the `t3_` prefix is the fullname, not the id. */
    val id: String,
    val title: String,
    val subreddit: String,
    /** Path on reddit.com, e.g. `/r/dartlang/comments/abc123/title/`. */
    val permalink: String,
    val author: String? = null,
    val score: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Instant? = null,
    /** What the post points at: the article of a link post; null for a self post. */
    val url: String? = null,
    val isSelf: Boolean = false,
    val selfText: String? = null,
    val over18: Boolean = false,
    val spoiler: Boolean = false,
    /** Pinned to the top of its community. */
    val stickied: Boolean = false,
    val thumbnail: String? = null,
    /** The post's own label within its community, e.g. `Elon Criticism`. */
    val flair: String? = null,
    /** Where the post points, as Reddit summarises it: `i.redd.it`, `self.dartlang`, an article's domain. */
    val domain: String? = null,
    /** A gallery's pictures in the author's order; empty for anything else. */
    val galleryImages: List<String> = emptyList(),
    /** Reddit's own preview of what the post points at: a video's poster frame, an article's lead image. */
    val previewImage: String? = null,
    /** A v.redd.it stream a player can open whole: DASH from the API, HLS from the website. */
    val videoManifestUrl: String? = null,
    /** The progressive, video-only file of the same stream, kept as the download target. */
    val videoFallbackUrl: String? = null,
    /** The video's width over its height; null reads as 16:9. */
    val videoAspectRatio: Double? = null,
) {
    /** A thumbnail worth showing: Reddit writes `self`, `default` or `nsfw` where there is none. */
    val thumbnailUrl: String? get() = thumbnail?.takeIf { it.startsWith("http") }

    /**
     * The full-size picture to show edge to edge, or null. Deliberately not
     * [thumbnailUrl]: a 70px listing thumbnail stretched across a phone is a smear.
     */
    val imageUrl: String? get() = RedditMedia.imageUrl(url) ?: galleryImages.firstOrNull()

    val isVideo: Boolean get() = RedditMedia.isVideoHost(domain ?: url?.let(::hostOf))

    val hasPlayableVideo: Boolean get() = videoManifestUrl != null || videoFallbackUrl != null

    /** A picture, a gallery or a playable video the card shows, so a title that only says `<image>` can stay hidden. */
    val hasVisualMedia: Boolean get() = hasPlayableVideo || imageUrl != null || galleryImages.size > 1

    /** The title without the CDN links of a picture the card already shows. */
    val displayTitle: String get() = if (hasVisualMedia) RedditMedia.stripMediaLinks(title) else title

    /** The self text without those same links. */
    val displaySelfText: String? get() = selfText?.let { if (hasVisualMedia) RedditMedia.stripMediaLinks(it) else it }

    /** Whether the title is worth showing above the card. */
    val showsTitle: Boolean
        get() = displayTitle.isNotEmpty() && (!hasVisualMedia || !RedditMedia.isPlaceholderTitle(displayTitle))

    /** Self text that is more than a `>image>` beside a picture the card already shows. */
    val showsSelfText: Boolean
        get() = displaySelfText?.trim().orEmpty().let { it.isNotEmpty() && !RedditMedia.isPlaceholderTitle(it) }

    /** The stream to play: the post's own, or the DASH manifest of a bare v.redd.it link an HTML listing carried. */
    val playableVideoUrl: String? get() = videoManifestUrl ?: videoFallbackUrl ?: RedditMedia.vRedditDashUrl(url)

    /** The banner of a link or video card: Reddit's preview, else the listing thumbnail. */
    val cardPreviewImage: String? get() = previewImage ?: thumbnailUrl

    /** Nothing to show but a title: how a post arrives from search, and from a link that names only an id. */
    val arrivedBare: Boolean
        get() = selfText == null && url == null && galleryImages.isEmpty() && previewImage == null && !hasPlayableVideo

    /**
     * This post with what [fetched], the same post as Reddit serves it on its
     * own, knows and this one does not. Reddit's title and counts replace a
     * stub's; everything else only fills a gap, so a feed post keeps the flair
     * and video it came with.
     */
    fun filledFrom(fetched: RedditPost): RedditPost = copy(
        title = if (fetched.title.isEmpty()) title else fetched.title,
        subreddit = subreddit.ifEmpty { fetched.subreddit },
        author = author ?: fetched.author,
        score = if (fetched.score != 0) fetched.score else score,
        commentCount = if (fetched.commentCount != 0) fetched.commentCount else commentCount,
        createdAt = createdAt ?: fetched.createdAt,
        url = url ?: fetched.url,
        isSelf = if (url == null) fetched.isSelf else isSelf,
        selfText = selfText ?: fetched.selfText,
        over18 = over18 || fetched.over18,
        spoiler = spoiler || fetched.spoiler,
        thumbnail = thumbnail ?: fetched.thumbnail,
        flair = flair ?: fetched.flair,
        domain = domain ?: fetched.domain,
        galleryImages = galleryImages.ifEmpty { fetched.galleryImages },
        previewImage = previewImage ?: fetched.previewImage,
        videoManifestUrl = videoManifestUrl ?: fetched.videoManifestUrl,
        videoFallbackUrl = videoFallbackUrl ?: fetched.videoFallbackUrl,
        videoAspectRatio = videoAspectRatio ?: fetched.videoAspectRatio,
    )
}

/**
 * One comment with the replies that hang off it, or a stub row standing for
 * replies Reddit held back (XTA's `RedditComment`).
 */
data class RedditComment(
    val id: String,
    val body: String,
    val author: String? = null,
    /** Null when Reddit hides the score, as it does for new comments. */
    val score: Int? = null,
    val createdAt: Instant? = null,
    /** Reddit marks the post's own author. */
    val isSubmitter: Boolean = false,
    /** The label the author wears in this community. */
    val authorFlair: String? = null,
    /** Pictures and GIFs the body carries, in order: Reddit has no comment-image field of its own. */
    val mediaUrls: List<String> = emptyList(),
    val replies: List<RedditComment> = emptyList(),
    /** This comment's own page, which opens its subtree when replies were held back. */
    val permalink: String? = null,
    /**
     * On a "more comments" or "continue this thread" stub: how many replies
     * Reddit held back, -1 when it did not say; [permalink] is the page that
     * carries them.
     */
    val moreCount: Int? = null,
    /** On a stub for comments held back at a thread's top: where www.reddit.com serves the next of them. */
    val nextPage: RedditCommentsCursor? = null,
) {
    val isStub: Boolean get() = moreCount != null || (body.isEmpty() && permalink != null && replies.isEmpty())

    /** Reddit kept the row but took the words: deleted by its author or removed by a moderator. */
    val isRemoved: Boolean get() = !isStub && body.trim().lowercase() in REMOVED_BODIES

    /** Somebody to open a profile for: not a deleted account, not a row without one. */
    val hasAuthor: Boolean get() = !author.isNullOrEmpty() && author != "[deleted]"

    /** This comment and everything under it. */
    val totalCount: Int get() = 1 + replies.sumOf { it.totalCount }

    /** The comments under this one at any depth; a stub standing for held-back replies is not one. */
    val replyCount: Int get() = replies.sumOf { if (it.isStub) 0 else 1 + it.replyCount }

    companion object {
        /** What Reddit puts in a comment's place once its words are gone. */
        val REMOVED_BODIES = setOf("[deleted]", "[removed]", "[unavailable]", "[ removed by reddit ]")
    }
}

/** Where www.reddit.com serves the next comments at a thread's top: the piece to POST to, and the cursor it wants. */
data class RedditCommentsCursor(val path: String, val cursor: String)

/**
 * A post's comments, with what its page says about the post itself: a self
 * post's words and a link post's target and pictures, which the listing that
 * led here may not have carried.
 */
data class RedditThread(
    val comments: List<RedditComment>,
    val selfText: String? = null,
    val postUrl: String? = null,
    val postImages: List<String> = emptyList(),
)

/** What a community says about itself: read-only facts from its sidebar. */
data class RedditSubredditAbout(
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val subscribers: Int? = null,
    val activeUsers: Int? = null,
    val over18: Boolean = false,
    /** `community_icon` or `icon_img`, unescaped; null when the community has no artwork of its own. */
    val iconUrl: String? = null,
)

/** A community a search turned up. */
data class RedditSubredditResult(
    val name: String,
    val subscribers: Int? = null,
    val description: String? = null,
    val iconUrl: String? = null,
)

/** An account a search turned up. */
data class RedditUserResult(val name: String, val karma: Int? = null)

/** The orders a community's listing comes in, by the path segment Reddit names each with. */
enum class RedditSort(val path: String) {
    HOT("hot"),
    NEW("new"),
    TOP("top"),
    RISING("rising"),
    CONTROVERSIAL("controversial"),
    ;

    /** Top and controversial are ranked within a time window; the others are not. */
    val usesTimeFilter: Boolean get() = this == TOP || this == CONTROVERSIAL
}

/** The window Reddit ranks top and controversial listings within. */
enum class RedditTimeFilter(val query: String) {
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ALL("all"),
}

/** The orders a thread's comments come in; best is Reddit's default and sends nothing. */
enum class RedditCommentSort(val query: String) {
    BEST(""),
    TOP("top"),
    NEW("new"),
    CONTROVERSIAL("controversial"),
    OLD("old"),
    QA("qa"),
}

/** The orders a search comes in, as XTA offers them. */
enum class RedditSearchSort(val query: String) {
    RELEVANCE("relevance"),
    TOP("top"),
    NEW("new"),
    COMMENTS("comments"),
}

/** [posts] without the over-18 ones when the reader hides them. */
fun filterRedditPosts(posts: Iterable<RedditPost>, hideNsfw: Boolean): List<RedditPost> =
    if (hideNsfw) posts.filterNot { it.over18 } else posts.toList()

/** [next] after [existing], each post once: a page can repeat a post the one before it ended with. */
fun appendRedditPosts(existing: List<RedditPost>, next: List<RedditPost>): List<RedditPost> {
    val seen = existing.mapTo(HashSet()) { it.id }
    return existing + next.filter { seen.add(it.id) }
}

/** A comment of a flattened thread, with how deep it sat. */
data class FlatComment(val comment: RedditComment, val depth: Int)

/** [comments] depth first, each with its depth, as a list a screen can lay out row by row. */
fun flattenComments(comments: List<RedditComment>, depth: Int = 0): List<FlatComment> =
    comments.flatMap { listOf(FlatComment(it, depth)) + flattenComments(it.replies, depth + 1) }

/**
 * [incoming] without the threads [shown] already holds at its top level,
 * replies and all: a page of held-back comments can repeat one the thread
 * loaded before, and each top-level comment is shown once.
 */
fun withoutShownThreads(shown: List<FlatComment>, incoming: List<FlatComment>): List<FlatComment> {
    val ids = shown.filter { it.depth == 0 }.mapTo(HashSet()) { it.comment.id }
    var repeated = false
    return incoming.filter { entry ->
        if (entry.depth == 0) repeated = entry.comment.id in ids
        !repeated
    }
}
