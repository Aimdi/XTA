package com.aimdi.xtanative.networks.bluesky

import java.time.Instant

/**
 * What a Bluesky timeline shows of the posts already read, as XTA's reader
 * filters them on the device: it never asks the network again, and never
 * changes the feed's own order or cursor.
 */
data class BlueskyReaderFilter(
    /** Every word must appear in the text, the author's name or handle, or the link card's title. */
    val query: String = "",
    val content: Content = Content.ALL,
    val order: Order = Order.FEED,
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
) {
    enum class Content { ALL, IMAGES, VIDEOS, LINKS }

    /** [FEED] keeps the feed's order; the others sort by [BlueskyPost.timelineDate], undated last. */
    enum class Order { FEED, NEWEST, OLDEST }

    /** Whether anything is left out: a screen shows its filter as set. */
    val filtered: Boolean get() = query.isNotBlank() || content != Content.ALL || hideReplies || hideReposts

    /** The posts this filter shows, each URI once, in its [order]. */
    fun apply(posts: List<BlueskyPost>): List<BlueskyPost> {
        val words = query.trim().lowercase().split(spaces).filter { it.isNotEmpty() }
        val shown = posts.filter { shows(it) && matches(it, words) }.distinctBy { it.uri }
        return when (order) {
            Order.FEED -> shown
            Order.NEWEST -> shown.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(reverseOrder())) { it.timelineDate })
            Order.OLDEST -> shown.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(naturalOrder())) { it.timelineDate })
        }
    }

    private fun shows(post: BlueskyPost): Boolean = !(hideReposts && post.isRepost) && !(hideReplies && post.isReply) && when (content) {
        Content.ALL -> true
        Content.IMAGES -> post.media.any { !it.isVideo }
        Content.VIDEOS -> post.media.any { it.isVideo }
        Content.LINKS -> post.hasLinkCard || post.facets.any { it.kind == BlueskyFacet.Kind.LINK }
    }

    private fun matches(post: BlueskyPost, words: List<String>): Boolean {
        val text = "${post.text} ${post.authorName} ${post.handle} ${post.linkCard?.title.orEmpty()}".lowercase()
        return words.all { it in text }
    }

    private companion object {
        val spaces = Regex("[$JS_SPACE]+")
    }
}
