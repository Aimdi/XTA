package com.aimdi.xtanative.networks.threads

import java.time.Instant

/**
 * The orders Threads' reply list offers. [TOP] is Meta's own ranking, the
 * order its page sends; the others are sorted here, each reply thread moving
 * whole by its first reply.
 */
enum class ThreadsReplyOrder { TOP, NEWEST, OLDEST }

/**
 * A post page arranged the way the Threads app reads it: what the post
 * answers, the post, the rest of its author's thread, then each reply thread.
 */
data class ThreadsConversation(
    val focus: ThreadsPost,
    /** The posts above [focus], oldest first. */
    val ancestors: List<ThreadsPost> = emptyList(),
    /** The author's own posts chained under [focus]. */
    val continuation: List<ThreadsPost> = emptyList(),
    /** Each reply thread: a reply, then the replies Meta chained under it. */
    val replies: List<List<ThreadsPost>> = emptyList(),
    /** False until a page was read: [focus] is then only the card that was tapped, or a link's stub. */
    val loaded: Boolean = false,
) {
    val replyCount: Int get() = replies.sumOf { it.size }

    /** A link stub carries no words or pictures: there is nothing to draw yet. */
    val focusIsStub: Boolean get() = focus.text.isEmpty() && !focus.hasMedia && focus.linkCard == null && focus.quoted == null

    /** The reply threads the post's author took part in. */
    val authorReplies: List<List<ThreadsPost>> get() = replies.filter { chain -> chain.any { it.handle == focus.handle } }

    /** The reply threads in [order], the author's alone with [authorOnly]. */
    fun orderedReplies(order: ThreadsReplyOrder, authorOnly: Boolean = false): List<List<ThreadsPost>> {
        val chains = if (authorOnly) authorReplies else replies
        return when (order) {
            ThreadsReplyOrder.TOP -> chains
            ThreadsReplyOrder.NEWEST -> chains.sortedWith { a, b -> compareTimes(a.firstOrNull()?.publishedAt, b.firstOrNull()?.publishedAt, newestFirst = true) }
            ThreadsReplyOrder.OLDEST -> chains.sortedWith { a, b -> compareTimes(a.firstOrNull()?.publishedAt, b.firstOrNull()?.publishedAt, newestFirst = false) }
        }
    }

    /** The reply list as rows, each long reply thread cut to its first few posts until it is opened. */
    fun replyRows(view: ThreadsThreadView = ThreadsThreadView()): List<ThreadsReplyRow> =
        orderedReplies(view.order, view.authorOnly).flatMap { chain -> chainRows(chain, view.isExpanded(chain)) }

    companion object {
        /** How many posts of a reply thread show before "continue thread". */
        const val REPLY_CHAIN_PREVIEW = 3

        /** What a screen shows before Meta answers: the tapped card alone. */
        fun seed(post: ThreadsPost): ThreadsConversation = ThreadsConversation(focus = post)

        /**
         * [chains] arranged around [seed]. The chain holding the post gives what
         * came before it and its author's continuation; any other post in that
         * chain, and every other chain, is a reply thread. When no chain holds
         * it (Meta answered a short link with another id), the first chain's root
         * is the post the page is about.
         */
        fun from(chains: List<List<ThreadsPost>>, seed: ThreadsPost): ThreadsConversation {
            val usable = chains.filter { it.isNotEmpty() }
            if (usable.isEmpty()) return ThreadsConversation(focus = seed, loaded = true)
            val found = usable.indexOfFirst { chain -> chain.any { samePost(it, seed) } }
            val chainIndex = maxOf(found, 0)
            val chain = usable[chainIndex]
            val focusIndex = if (found < 0) 0 else chain.indexOfFirst { samePost(it, seed) }
            val focus = keepRepostContext(chain[focusIndex], seed)
            val after = chain.drop(focusIndex + 1)
            val continuation = after.takeWhile { it.handle == focus.handle }
            val rest = after.drop(continuation.size)
            return ThreadsConversation(
                focus = focus,
                ancestors = chain.take(focusIndex),
                continuation = continuation,
                replies = listOfNotNull(rest.ifEmpty { null }) + usable.filterIndexed { index, _ -> index != chainIndex },
                loaded = true,
            )
        }

        /**
         * Whether [post] is the one [seed] stands for: by id, else by short code,
         * the part a feed card and a shared link (other host, other query) agree on.
         */
        fun samePost(post: ThreadsPost, seed: ThreadsPost): Boolean {
            if (post.id == seed.id) return true
            val code = seed.shortcode ?: ThreadsLinks.shortcode(seed.id)
            return code != null && post.shortcode == code
        }

        /** A repost opened from the feed is read back as the original: it keeps saying who reposted it. */
        private fun keepRepostContext(fresh: ThreadsPost, seed: ThreadsPost): ThreadsPost {
            val reposter = seed.repostedByHandle
            if (!seed.isRepost || fresh.isRepost || reposter == null) return fresh
            return fresh.repostedBy(id = fresh.id, handle = reposter, name = seed.reposterDisplayName, at = fresh.publishedAt)
        }

        /** Undated posts sort last in either direction. */
        private fun compareTimes(a: Instant?, b: Instant?, newestFirst: Boolean): Int = when {
            a == null || b == null -> (if (a == null) 1 else 0) - (if (b == null) 1 else 0)
            newestFirst -> b.compareTo(a)
            else -> a.compareTo(b)
        }

        private fun chainRows(chain: List<ThreadsPost>, expanded: Boolean): List<ThreadsReplyRow> {
            val shown = if (expanded) chain else chain.take(REPLY_CHAIN_PREVIEW)
            val hidden = chain.size - shown.size
            val posts = shown.mapIndexed { index, post ->
                ThreadsReplyRow.Post(post, connectTop = index > 0, connectBottom = index < shown.size - 1 || hidden > 0)
            }
            return if (hidden > 0) posts + ThreadsReplyRow.More(chain, hidden) else posts
        }
    }
}

/** How the reader chose to look at a conversation's replies. */
data class ThreadsThreadView(
    /** Only the reply threads the post's author took part in. */
    val authorOnly: Boolean = false,
    /** Reply threads opened past their first few posts, by their first post's id. */
    val expanded: Set<String> = emptySet(),
    val order: ThreadsReplyOrder = ThreadsReplyOrder.TOP,
) {
    fun isExpanded(chain: List<ThreadsPost>): Boolean = chain.isNotEmpty() && chain.first().id in expanded
}

/** One row of a reply list. */
sealed interface ThreadsReplyRow {
    /** A post; rails join the posts of one reply thread, as the Threads app draws them. */
    data class Post(val post: ThreadsPost, val connectTop: Boolean = false, val connectBottom: Boolean = false) : ThreadsReplyRow

    /** The marker that opens the [hidden] rest of a long reply thread. */
    data class More(val chain: List<ThreadsPost>, val hidden: Int) : ThreadsReplyRow
}
