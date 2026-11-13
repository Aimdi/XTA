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
    /** Each reply's `reply_level` by id, where Meta said it: what nests a reply under the one it answers. */
    val levels: Map<String, Int> = emptyMap(),
    /** The reply threads, by their first post's id, Meta holds more of than the page carried. */
    val unfinished: Set<String> = emptySet(),
    /** Meta holds more replies than the page carried. */
    val moreReplies: Boolean = false,
    /** Where the replies after these start, when the page named a way to follow them; else they are read on threads.com. */
    val repliesNext: String? = null,
    /** Posts Threads suggests beside this one, never part of it. */
    val related: List<ThreadsPost> = emptyList(),
) {
    val replyCount: Int get() = replies.sumOf { it.size }

    /** A link stub carries no words or pictures: there is nothing to draw yet. */
    val focusIsStub: Boolean get() = focus.text.isEmpty() && !focus.hasMedia && focus.linkCard == null && focus.quoted == null

    /** The reply threads the post's author took part in. */
    val authorReplies: List<List<ThreadsPost>> get() = replies.filter { chain -> chain.any(::byAuthor) }

    /** Whether [post] is the opened post's author's, which a reply marks "Author". */
    fun byAuthor(post: ThreadsPost): Boolean = focus.handle.isNotEmpty() && post.handle == focus.handle

    /** The reply threads in [order], the author's alone with [authorOnly]. */
    fun orderedReplies(order: ThreadsReplyOrder, authorOnly: Boolean = false): List<List<ThreadsPost>> {
        val chains = if (authorOnly) authorReplies else replies
        return when (order) {
            ThreadsReplyOrder.TOP -> chains
            ThreadsReplyOrder.NEWEST -> chains.sortedWith { a, b -> compareTimes(a.firstOrNull()?.publishedAt, b.firstOrNull()?.publishedAt, newestFirst = true) }
            ThreadsReplyOrder.OLDEST -> chains.sortedWith { a, b -> compareTimes(a.firstOrNull()?.publishedAt, b.firstOrNull()?.publishedAt, newestFirst = false) }
        }
    }

    /**
     * The reply list as rows, each long reply thread cut to its first few posts
     * until it is opened, and an opened one ending in the row that cuts it again.
     */
    fun replyRows(view: ThreadsThreadView = ThreadsThreadView()): List<ThreadsReplyRow> =
        orderedReplies(view.order, view.authorOnly).flatMap { chain -> chainRows(chain, view.isExpanded(chain)) }

    /** The conversation with [more] reply threads after its own, as later pages brought them. */
    fun withMore(more: List<ThreadsReplyThread>): ThreadsConversation {
        if (more.isEmpty()) return this
        val shown = replies.flatten().mapTo(HashSet()) { it.id }.apply { add(focus.id) }
        val fresh = more.map { thread -> thread.copy(posts = thread.posts.filter { shown.add(it.id) }) }.filter { it.posts.isNotEmpty() }
        return copy(
            replies = replies + fresh.map { it.posts },
            levels = fresh.fold(levels) { all, thread -> all + thread.levels },
            unfinished = unfinished + fresh.filter { it.hasMore }.map { it.posts.first().id },
        )
    }

    /** What reading the conversation aloud says: each post's author and words, the post first, then every reply. */
    fun spokenText(): String = (ancestors + focus + continuation + replies.flatten())
        .filter { it.text.isNotBlank() }
        .joinToString("\n\n") { "${it.authorName}. ${it.text}" }

    private fun chainRows(chain: List<ThreadsPost>, expanded: Boolean): List<ThreadsReplyRow> {
        val shown = if (expanded) chain else chain.take(REPLY_CHAIN_PREVIEW)
        val hidden = chain.size - shown.size
        val shapes = ThreadsReplyShape.of(chain.map { levels[it.id] })
        val posts = shown.mapIndexed { index, post ->
            val shape = shapes[index]
            ThreadsReplyRow.Post(post, connectTop = shape.railAbove, connectBottom = shapes.getOrNull(index + 1)?.railAbove ?: false, depth = shape.depth)
        }
        val unfinishedRow = chain.firstOrNull()?.takeIf { it.id in unfinished && hidden == 0 }?.let { ThreadsReplyRow.OnWeb(chain) }
        return when {
            hidden > 0 -> posts + ThreadsReplyRow.More(chain, hidden)
            expanded && chain.size > REPLY_CHAIN_PREVIEW -> posts + listOfNotNull(unfinishedRow) + ThreadsReplyRow.Less(chain)
            else -> posts + listOfNotNull(unfinishedRow)
        }
    }

    companion object {
        /** How many posts of a reply thread show before "continue thread". */
        const val REPLY_CHAIN_PREVIEW = 3

        /** What a screen shows before Meta answers: the tapped card alone. */
        fun seed(post: ThreadsPost): ThreadsConversation = ThreadsConversation(focus = post)

        /**
         * A read post page around [seed]. A page in the older shape is arranged
         * from its chains; the newer one names its post, so a short link that
         * Meta answered under another id still lands on the right one.
         */
        fun from(page: ThreadsPostPage, seed: ThreadsPost): ThreadsConversation {
            val target = page.target ?: return from(page.chains, seed).copy(related = page.related)
            return ThreadsConversation(
                focus = keepRepostContext(target, seed),
                ancestors = page.above,
                continuation = page.continuation,
                replies = page.replies.map { it.posts },
                loaded = true,
                levels = page.replies.fold(emptyMap()) { all, thread -> all + thread.levels },
                unfinished = page.replies.filter { it.hasMore }.mapNotNull { it.posts.firstOrNull()?.id }.toSet(),
                moreReplies = page.moreReplies,
                repliesNext = page.repliesNext,
                related = page.related,
            )
        }

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
    /**
     * A post; rails join a reply to the one it answers when that is the
     * only answer, as the Threads app draws a chain; a reply beside others
     * is set in by [depth] instead.
     */
    data class Post(val post: ThreadsPost, val connectTop: Boolean = false, val connectBottom: Boolean = false, val depth: Int = 0) : ThreadsReplyRow

    /** The marker that opens the [hidden] rest of a long reply thread. */
    data class More(val chain: List<ThreadsPost>, val hidden: Int) : ThreadsReplyRow

    /** The marker that cuts an opened reply thread back to its first few posts. */
    data class Less(val chain: List<ThreadsPost>) : ThreadsReplyRow

    /**
     * Meta holds more of this reply thread than the page carried: it goes on
     * at the page of its last post shown ([continuesAt]), whose own
     * conversation carries the replies under it.
     */
    data class OnWeb(val chain: List<ThreadsPost>) : ThreadsReplyRow {
        /** The post whose conversation shows the rest: the last of the chain with a link of its own. */
        val continuesAt: ThreadsPost? get() = chain.lastOrNull { !it.url.isNullOrBlank() }
    }
}

/**
 * Where one reply of a thread sits: how far it is set in, and whether a rail
 * joins it to the post above. From Meta's `reply_level`s: a reply that is
 * the only answer to the post above it continues that post's chain (a rail,
 * no step in); one of several answers steps in under the post it answers.
 * Without every level, the thread is one chain, as the older pages gave it.
 */
data class ThreadsReplyShape(val depth: Int, val railAbove: Boolean) {
    companion object {
        /** How far a reply is set in at most, so a deep thread keeps room for its words. */
        const val MAX_DEPTH = 3

        fun of(levels: List<Int?>): List<ThreadsReplyShape> {
            if (levels.any { it == null }) return levels.indices.map { ThreadsReplyShape(0, railAbove = it > 0) }
            val known = levels.map { it!! }
            return known.indices.fold(emptyList()) { shapes, index -> shapes + shape(known, index, shapes) }
        }

        private fun shape(levels: List<Int>, index: Int, before: List<ThreadsReplyShape>): ThreadsReplyShape {
            val parent = (index - 1 downTo 0).firstOrNull { levels[it] < levels[index] } ?: return ThreadsReplyShape(0, railAbove = false)
            val answers = (parent + 1 until levels.size).takeWhile { levels[it] > levels[parent] }.count { levels[it] == levels[index] }
            return if (answers == 1 && parent == index - 1) {
                ThreadsReplyShape(before[parent].depth, railAbove = true)
            } else {
                ThreadsReplyShape(minOf(before[parent].depth + 1, MAX_DEPTH), railAbove = false)
            }
        }
    }
}
