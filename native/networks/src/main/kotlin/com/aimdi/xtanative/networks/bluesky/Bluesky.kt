package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.networks.NetworkRequestException

/** What XTA's Bluesky reader is fixed on: where it reads, and how much it asks for at once. */
object Bluesky {
    /** The network a [NetworkRequestException] names, as XTA's plugin id. */
    const val NETWORK = "bluesky"

    /** Bluesky's public AppView: read-only XRPC without a login. */
    const val DEFAULT_APP_VIEW = "https://public.api.bsky.app"

    /**
     * Where post search goes from the default AppView, which has refused
     * `searchPosts` to readers without an account since 2026.
     */
    const val SEARCH_APP_VIEW = "https://api.bsky.app"

    /** Bluesky's Discover feed ("What's Hot"), readable without an account. */
    const val DISCOVER_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

    /** The `getAuthorFeed` filter of bsky.app's Posts tab: posts, and the author's own threads. */
    const val AUTHOR_FEED_POSTS = "posts_and_author_threads"

    /** The `getAuthorFeed` filter of bsky.app's Replies tab, and the AppView's default. */
    const val AUTHOR_FEED_REPLIES = "posts_with_replies"

    /** The `getAuthorFeed` filter of bsky.app's Media tab. */
    const val AUTHOR_FEED_MEDIA = "posts_with_media"

    /** The `getAuthorFeed` filter that leaves out every reply, the author's thread continuations too, and keeps reposts. */
    const val AUTHOR_FEED_NO_REPLIES = "posts_no_replies"

    /** How many posts one followed account adds to the Following timeline. */
    const val POSTS_PER_ACCOUNT = 20

    /**
     * How many followed accounts one Following read asks for. There is no
     * "my following feed" to ask a public AppView for, so a timeline is one
     * request per account; past this many, the rest wait for the next read.
     */
    const val MAX_ACCOUNTS_PER_LOAD = 30
}

/** Why a Bluesky read failed, in the terms XTA's screens explain it. */
enum class BlueskyErrorKind {
    NETWORK,
    NOT_FOUND,
    RATE_LIMITED,
    BAD_RESPONSE,
    ;

    companion object {
        /**
         * The kind of [error] as [BlueskyClient] throws it: a refusal by its
         * status, anything else (no connection, a timeout) the network's.
         */
        fun of(error: Throwable): BlueskyErrorKind = when {
            error !is NetworkRequestException -> NETWORK
            error.code == 404 -> NOT_FOUND
            error.code == 429 -> RATE_LIMITED
            else -> BAD_RESPONSE
        }
    }
}
