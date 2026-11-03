package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.networks.Page
import java.time.Instant

/** A picture or a video on a post, as the AppView hydrates `app.bsky.embed.images` and `app.bsky.embed.video`. */
data class BlueskyMedia(
    /** The picture itself (its full size when the AppView gives one), or a video's poster. */
    val url: String,
    val aspectRatio: Double? = null,
    val alt: String? = null,
    val isVideo: Boolean = false,
    /** The HLS playlist a video plays; null for a picture. */
    val videoUrl: String? = null,
    /** A video Bluesky presents as a GIF: silent and looping. */
    val isGif: Boolean = false,
) {
    val isPlayable: Boolean get() = isVideo && !videoUrl.isNullOrEmpty()
}

/** The link preview of an `app.bsky.embed.external`. */
data class BlueskyLinkCard(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

/**
 * One Bluesky post as a feed, a thread or a search hands it out. A repost is
 * the reposted post, with who reposted it and when.
 */
data class BlueskyPost(
    /** The post's `at://` URI: what every read and every de-duplication keys on. */
    val uri: String,
    val cid: String,
    val handle: String,
    val did: String,
    /** The display name, or the handle when there is none. */
    val authorName: String,
    val text: String,
    /** Where the post lives on bsky.app. */
    val url: String,
    val avatarUrl: String? = null,
    val media: List<BlueskyMedia> = emptyList(),
    /** Links, mentions and tags in [text], at UTF-8 byte offsets ([BlueskyFacets.runs] cuts the text at them). */
    val facets: List<BlueskyFacet> = emptyList(),
    val publishedAt: Instant? = null,
    val repostedAt: Instant? = null,
    val replyCount: Long = 0,
    val repostCount: Long = 0,
    val likeCount: Long = 0,
    val quoteCount: Long = 0,
    val repostedByName: String? = null,
    val repostedByHandle: String? = null,
    val repostedByDid: String? = null,
    /** The post a record or record-with-media embed quotes, read without a quote of its own. */
    val quotedPost: BlueskyPost? = null,
    val linkCard: BlueskyLinkCard? = null,
    /** The handle of the author this replies to, when the feed says. */
    val replyToHandle: String? = null,
    val isReply: Boolean = false,
    /** The post this replies to: from the record, else from where it sits in a thread. */
    val replyToUri: String? = null,
    /** Moderation labels of the post, its author and its record, negations left out. */
    val labels: List<String> = emptyList(),
) {
    val images: List<String> get() = media.map { it.url }

    val hasMedia: Boolean get() = media.isNotEmpty()

    val hasQuote: Boolean get() = quotedPost != null

    val hasLinkCard: Boolean get() = linkCard != null

    /** When the post reached a timeline: when it was reposted, else when it was written. */
    val timelineDate: Instant? get() = repostedAt ?: publishedAt

    /** Who reposted it: their DID when known, else their handle. */
    val reposterActor: String? get() = repostedByDid?.takeIf { it.isNotEmpty() } ?: repostedByHandle

    val isRepost: Boolean get() = !reposterActor.isNullOrEmpty()

    /** Labelled as media a reader may not want to see unasked. */
    val isSensitive: Boolean get() = labels.any { it in SENSITIVE_LABELS }

    /**
     * Labelled so it cannot be shown at all: `!hide`, or `!no-unauthenticated`,
     * its author's wish that readers without an account (all of XTA's) not see it.
     */
    val isUnavailable: Boolean get() = "!hide" in labels || "!no-unauthenticated" in labels

    /** Shown only behind a warning the reader opens, or not at all. */
    val warnsContent: Boolean get() = isUnavailable || "!warn" in labels

    private companion object {
        val SENSITIVE_LABELS = setOf(
            "porn", "sexual", "sexual-cartoon", "nudity", "graphic-media", "sensitive", "self-harm", "gore", "!warn", "!hide",
        )
    }
}

/** An opened post: the posts above it, root first, and the replies below it, depth first. */
data class BlueskyThread(
    val post: BlueskyPost,
    val ancestors: List<BlueskyPost> = emptyList(),
    val replies: List<BlueskyPost> = emptyList(),
)

/** A Bluesky account as the public AppView reports it. */
data class BlueskyProfile(
    val did: String,
    val handle: String,
    /** The display name, or the handle when there is none. */
    val displayName: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val createdAt: Instant? = null,
    val followersCount: Long = 0,
    val followsCount: Long = 0,
    val postsCount: Long = 0,
    val pinnedPostUri: String? = null,
) {
    /** What the AppView takes as `actor`: the DID when known, since a handle can change. */
    val actor: String get() = did.ifEmpty { handle }

    /** What tells two people apart in a list: the DID, else the handle in lower case. */
    val identity: String get() = did.ifEmpty { handle.lowercase() }
}

/** A public list's metadata (`app.bsky.graph.defs#listView`). */
data class BlueskyListInfo(
    val uri: String,
    val name: String,
    val itemCount: Long = 0,
    val description: String = "",
    val avatarUrl: String? = null,
    val creatorHandle: String? = null,
)

/** One page of a list's members, and the list itself when the AppView sent it. */
data class BlueskyListMembers(val list: BlueskyListInfo?, val members: Page<BlueskyProfile>)

/** A custom feed (`app.bsky.feed.defs#generatorView`). */
data class BlueskyFeedGenerator(
    val uri: String,
    /** The feed's name, or its record key when it has none. */
    val displayName: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val creatorHandle: String? = null,
)

/**
 * A list, custom feed or starter pack as a pasted link names it: by its
 * `at://` URI, or by its owner and record key from a bsky.app URL, which
 * [BlueskyClient] turns into a URI through the owner's DID.
 */
sealed interface BlueskyRef {
    data class AtUri(val uri: String) : BlueskyRef

    data class Web(val actor: String, val rkey: String) : BlueskyRef
}

/** The one post a search query names, by a bsky.app post link or an `at://` URI. */
data class BlueskyPostTarget(val actor: String, val rkey: String)

/** An account met in a feed that the reader does not follow yet; [fromRepost] when someone they follow reposted it. */
data class BlueskyFeedPerson(val handle: String, val name: String, val avatarUrl: String?, val fromRepost: Boolean)
