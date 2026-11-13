package com.aimdi.xtanative.networks.bluesky

import java.time.Instant

/** How XTA orders, de-duplicates and draws people from Bluesky timelines. */
object BlueskyTimeline {
    /** Newest activity first (a repost by when it was reposted), undated last, then by URI so equal times do not reshuffle. */
    val newestFirst: Comparator<BlueskyPost> =
        compareByDescending<BlueskyPost> { it.timelineDate ?: Instant.EPOCH }.thenBy { it.uri }

    /**
     * One row per post URI, in the order each was first met: the original
     * wins over reposts of it, and among reposts the newest.
     */
    fun dedupe(posts: Iterable<BlueskyPost>): List<BlueskyPost> {
        val rows = LinkedHashMap<String, BlueskyPost>()
        posts.filter { it.uri.isNotEmpty() }.forEach { post ->
            val previous = rows[post.uri]
            val wins = previous == null ||
                (previous.isRepost && !post.isRepost) ||
                (previous.isRepost && post.isRepost && newestFirst.compare(post, previous) < 0)
            if (wins) rows[post.uri] = post
        }
        return rows.values.toList()
    }

    /** [dedupe]d and [newestFirst]: a timeline merged from several accounts. */
    fun stabilize(posts: Iterable<BlueskyPost>): List<BlueskyPost> = dedupe(posts).sortedWith(newestFirst)

    /**
     * Whether a feed taking [replies] and [reposts] takes [post]. A repost
     * follows the repost setting whatever it shares, as Bluesky's own
     * `posts_no_replies` keeps reposts of replies; every other reply counts.
     */
    fun takes(post: BlueskyPost, replies: Boolean, reposts: Boolean): Boolean =
        if (post.isRepost) reposts else replies || !post.isReply

    /**
     * Authors of [posts] and of the posts they quote whom the reader does not
     * follow yet ([alreadyFollows] takes a lower-case handle), once each:
     * those a followed account reposted first, then the rest, at most [cap].
     */
    fun peopleToFollow(posts: List<BlueskyPost>, alreadyFollows: (String) -> Boolean, cap: Int = 12): List<BlueskyFeedPerson> {
        val met = posts.flatMap { post -> listOfNotNull(person(post, post.isRepost), post.quotedPost?.let { person(it, false) }) }
        val fresh = met.filter { it.handle.isNotEmpty() && !alreadyFollows(it.handle) }.distinctBy { it.handle }
        val (reposted, others) = fresh.partition { it.fromRepost }
        return (reposted + others).take(cap)
    }

    private fun person(post: BlueskyPost, fromRepost: Boolean): BlueskyFeedPerson {
        val handle = post.handle.trim().lowercase()
        return BlueskyFeedPerson(handle, post.authorName.takeIf { it.isNotBlank() } ?: handle, post.avatarUrl, fromRepost)
    }
}

/** A profile's tabs, each read with the `getAuthorFeed` filter bsky.app's tab uses. */
enum class BlueskyProfileTab(val filter: String) {
    POSTS(Bluesky.AUTHOR_FEED_POSTS),
    REPLIES(Bluesky.AUTHOR_FEED_REPLIES),
    MEDIA(Bluesky.AUTHOR_FEED_MEDIA),
    ;

    /** Whether the tab shows [post]: the Replies filter also lets the author's own posts through. */
    fun shows(post: BlueskyPost): Boolean = when (this) {
        POSTS -> true
        REPLIES -> post.isReply
        MEDIA -> post.hasMedia
    }

    /** [posts] as the tab lists them: Posts starts with the [pinned] post, and has it there only. */
    fun withPin(posts: List<BlueskyPost>, pinned: BlueskyPost?): List<BlueskyPost> =
        if (this != POSTS || pinned == null) posts else listOf(pinned) + posts.filter { it.uri != pinned.uri }
}
