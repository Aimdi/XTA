package com.aimdi.xtanative.networks.threads

import java.time.Instant

/**
 * Home's followed accounts mixed with what reaches it besides their own
 * threads: their reposts and the posts of the topics and communities
 * followed here. One post shows once: an original and a repost of it, or a
 * post both an account and a topic brought, are one card, the newest of
 * them (a repost is dated when it was reposted), so a post shared again
 * rises with the line naming who shared it.
 */
object ThreadsFeedMix {
    /** [lists] as one feed, newest first, each post once by its link (its id when it has none). */
    fun merge(vararg lists: List<ThreadsPost>): List<ThreadsPost> = lists.toList().flatten()
        .sortedWith(compareByDescending<ThreadsPost> { it.publishedAt ?: Instant.MIN }.thenBy { if (it.isRepost) 0 else 1 })
        .distinctBy(::identity)

    /** What makes two cards one post: the short code of the original's link, else its id. */
    fun identity(post: ThreadsPost): String = post.shortcode ?: post.url?.trim()?.ifEmpty { null } ?: post.id
}
