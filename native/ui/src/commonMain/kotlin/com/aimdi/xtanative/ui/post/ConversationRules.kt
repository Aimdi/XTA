package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.XQuoteOrder
import com.aimdi.xtanative.core.x.XReplyRanking
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.offline.CachedTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How the replies under an opened post are ordered (XTA's conversation_sort.dart). */
enum class ReplySort { RELEVANT, RECENT, OLDEST, MOST_LIKED }

/** How the quotes of a post are ordered. */
enum class QuoteSort { RECENT, TOP, OLDEST, MOST_LIKED }

/** How the people who reposted a post are ordered. */
enum class ReposterSort { RECENT, MOST_FOLLOWERS }

/** The sort choices a reader made, kept for the rest of the run and shared by every conversation, as XTA's store. */
data class ConversationSorts(
    val replies: ReplySort = ReplySort.RELEVANT,
    val quotes: QuoteSort = QuoteSort.RECENT,
    val reposters: ReposterSort = ReposterSort.RECENT,
)

/** The orders X offers and what each asks it for. */
object Sorts {
    /** X ranks replies itself, through TweetDetail's `rankingMode`. */
    val X_REPLIES = listOf(ReplySort.RELEVANT, ReplySort.RECENT, ReplySort.MOST_LIKED)

    /** X ranks quotes itself, through SearchTimeline's `product`. */
    val X_QUOTES = listOf(QuoteSort.RECENT, QuoteSort.TOP)

    private val chosen = MutableStateFlow(ConversationSorts())

    /** This run's choices. */
    val state: StateFlow<ConversationSorts> = chosen.asStateFlow()

    fun selectReplies(sort: ReplySort) = chosen.update { it.copy(replies = sort) }

    fun selectQuotes(sort: QuoteSort) = chosen.update { it.copy(quotes = sort) }

    fun selectReposters(sort: ReposterSort) = chosen.update { it.copy(reposters = sort) }

    /** [chosen] when the network offers it, else the network's own default, the first of [options]. */
    fun <T> effective(chosen: T, options: List<T>): T = if (chosen in options) chosen else options.first()

    fun ranking(sort: ReplySort): XReplyRanking = when (effective(sort, X_REPLIES)) {
        ReplySort.RECENT -> XReplyRanking.RECENCY
        ReplySort.MOST_LIKED -> XReplyRanking.LIKES
        ReplySort.RELEVANT, ReplySort.OLDEST -> XReplyRanking.RELEVANCE
    }

    fun quoteOrder(sort: QuoteSort): XQuoteOrder = if (effective(sort, X_QUOTES) == QuoteSort.TOP) XQuoteOrder.TOP else XQuoteOrder.LATEST

    /** The reposter orders the people loaded can back: by followers only once X said how many each has. */
    fun reposterSorts(people: List<XUser>): List<ReposterSort> =
        listOf(ReposterSort.RECENT) + listOfNotNull(ReposterSort.MOST_FOLLOWERS.takeIf { people.any { it.followers != null } })

    /** X's own order (newest first), or most followed first, those without a count last; ties keep their order. */
    fun sortReposters(people: List<XUser>, sort: ReposterSort): List<XUser> =
        if (sort == ReposterSort.RECENT) people else people.sortedByDescending { it.followers ?: -1 }
}

/** What one page of a conversation adds under the opened post, and where reading goes next. */
data class ConversationStep(
    val replies: List<XThread>,
    /** The next page; null when the conversation is read to its end. */
    val next: String?,
    /** X's "Show additional replies": offered where the thread ends, followed only at the reader's tap. */
    val showMore: String?,
    /** X counted replies but sent none and no way to them: a retry is offered rather than an end. */
    val repliesMissing: Boolean = false,
)

/**
 * The pages of one opened post's conversation, as XTA's status.dart pages
 * them: X sends earlier replies again on later pages, so a post already shown
 * is dropped, and a thread X adds to under an id already used gets one of its
 * own; a page that brings nothing new under the same cursor ends the reading.
 */
class ConversationPaging(private val focalId: String) {
    private val seenPosts = HashSet<String>()
    private val seenThreads = HashSet<String>()

    /**
     * The first page. [expectedReplies] is how many replies X counted on the
     * post. When X held every reply behind "Show additional replies", that
     * cursor is followed as the next page: the reader would otherwise be left
     * with a count and nothing under it.
     */
    fun first(conversation: XConversation, expectedReplies: Long): ConversationStep {
        seenPosts.clear()
        seenThreads.clear()
        (conversation.before + listOfNotNull(conversation.focal)).forEach { seenPosts += it.id }
        val replies = fresh(conversation.replies)
        val visible = replies.any { it.posts.isNotEmpty() }
        val next = conversation.repliesCursor
        val showMore = conversation.showMoreCursor
        if (!visible && expectedReplies > 0 && showMore != null) return ConversationStep(replies, next = showMore, showMore = null)
        return ConversationStep(
            replies = replies,
            next = next,
            showMore = showMore.takeIf { next == null },
            repliesMissing = expectedReplies > 0 && !visible && showMore == null && next == null,
        )
    }

    /** A later page, read with [cursor]. */
    fun next(conversation: XConversation, cursor: String): ConversationStep {
        val replies = fresh(conversation.replies)
        val next = conversation.repliesCursor
        val ends = replies.none { it.posts.isNotEmpty() } || next == null || next == cursor
        val showMore = conversation.showMoreCursor?.takeIf { ends && it != cursor }
        return ConversationStep(replies, next = next.takeIf { !ends }, showMore = showMore)
    }

    private fun fresh(threads: List<XThread>): List<XThread> = threads.mapNotNull { thread ->
        val posts = thread.posts.filter { it.id.isNotEmpty() && it.id != focalId && it.id !in seenPosts }
        val gaps = thread.withheld.filter { it.id !in seenPosts }
        if (posts.isEmpty() && gaps.isEmpty()) return@mapNotNull null
        val id = if (thread.id in seenThreads) thread.id + "-" + (posts.firstOrNull()?.id ?: gaps.first().id) else thread.id
        seenThreads += thread.id
        seenThreads += id
        posts.forEach { seenPosts += it.id }
        gaps.forEach { seenPosts += it.id }
        XThread(id, posts, gaps)
    }
}

/** A reply thread at its depth in the reply tree: 1 answers the opened post, 2 answers one of those. */
data class ThreadNode(val thread: XThread, val depth: Int)

/** One row of the threaded replies. */
sealed interface ThreadRow {
    data class Reply(val node: ThreadNode, val visualDepth: Int, val connectTop: Boolean, val connectBottom: Boolean) : ThreadRow

    /** "Continue thread": the first reply the depth cap leaves out, opened on its own screen. */
    data class Continue(val target: ThreadNode, val indentDepth: Int) : ThreadRow
}

/**
 * Replies as a Reddit-style tree, as XTA's threaded_conversation.dart lays
 * them out under the opened post when "threaded replies" is on: a reply
 * thread nests under the thread holding the post its first post answers;
 * replies to the opened post, or to a post not loaded, are roots. Pre-order,
 * siblings oldest first, each thread exactly once, with guards against
 * cycles; beyond [MAX_DEPTH] a branch is left to "Continue thread".
 */
object ThreadTree {
    const val MAX_DEPTH = 2

    fun nodes(replies: List<XThread>): List<ThreadNode> {
        if (replies.isEmpty()) return emptyList()
        val holder = HashMap<String, XThread>()
        replies.forEach { thread -> thread.posts.forEach { holder[it.id] = thread } }
        fun parentOf(thread: XThread): XThread? = thread.posts.firstOrNull()?.replyToPostId?.let { holder[it] }?.takeIf { it !== thread }
        val children = HashMap<String, MutableList<XThread>>()
        val roots = ArrayList<XThread>()
        replies.forEach { thread ->
            val parent = parentOf(thread)
            if (parent == null) roots += thread else children.getOrPut(parent.id) { ArrayList() } += thread
        }
        val out = ArrayList<ThreadNode>()
        val visited = HashSet<String>()
        fun visit(thread: XThread, depth: Int) {
            if (!visited.add(thread.id)) return
            out += ThreadNode(thread, depth)
            children[thread.id].orEmpty().sortedWith(oldestFirst).forEach { visit(it, depth + 1) }
        }
        roots.sortedWith(oldestFirst).forEach { visit(it, 1) }
        replies.filter { it.id !in visited }.forEach { out += ThreadNode(it, 1) }
        return out
    }

    /** [nodes] as rows: capped at [maxDepth], a "Continue thread" row in place of each deeper branch, the rails joined. */
    fun rows(nodes: List<ThreadNode>, maxDepth: Int = MAX_DEPTH): List<ThreadRow> {
        val raw = ArrayList<ThreadRow>()
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index]
            if (node.depth > maxDepth) {
                index++
                continue
            }
            raw += ThreadRow.Reply(node, node.depth, connectTop = false, connectBottom = false)
            if (index + 1 < nodes.size && nodes[index + 1].depth > maxDepth) {
                raw += ThreadRow.Continue(nodes[index + 1], maxDepth)
                index = skipSubtree(nodes, index + 1)
                continue
            }
            index++
        }
        return raw.mapIndexed { at, row ->
            if (row !is ThreadRow.Reply) return@mapIndexed row
            row.copy(connectTop = row.visualDepth > 0 && at > 0 && raw[at - 1] is ThreadRow.Reply, connectBottom = childBelow(row, at, raw))
        }
    }

    /** Extra start inset of a reply at [depth]: answers to the opened post sit flush with it, deeper ones step in. */
    fun indentLevels(depth: Int): Int = (depth - 1).coerceAtLeast(0)

    private fun skipSubtree(nodes: List<ThreadNode>, start: Int): Int {
        val depth = nodes[start].depth
        var at = start + 1
        while (at < nodes.size && nodes[at].depth > depth) at++
        return at
    }

    private fun childBelow(row: ThreadRow.Reply, at: Int, rows: List<ThreadRow>): Boolean = when (val next = rows.getOrNull(at + 1)) {
        is ThreadRow.Continue -> row.node.depth == next.indentDepth
        is ThreadRow.Reply -> next.node.depth > row.node.depth
        null -> false
    }

    /** Oldest first by the first post's id, which X gives in time order: a shorter id is older. */
    private val oldestFirst = compareBy<XThread>({ (it.posts.firstOrNull()?.id ?: it.id).length }, { it.posts.firstOrNull()?.id ?: it.id })
}

/**
 * When an opened post's first page is kept on the device, as XTA's
 * status.dart keeps it: X's own order only, for ten minutes, and read at any
 * age when X gives nothing. A page with a reply count and nothing under it
 * is never kept: opening the post again would show no replies for as long.
 */
object ThreadCache {
    const val MAX_AGE_MS = 10 * 60 * 1000L

    fun keeps(sort: ReplySort): Boolean = Sorts.effective(sort, Sorts.X_REPLIES) == ReplySort.RELEVANT

    /** Worth keeping: replies to show, or X's withheld ones still to ask for; or a post that has none. */
    fun worthKeeping(conversation: XConversation, expectedReplies: Long): Boolean =
        conversation.replies.any { it.posts.isNotEmpty() } || conversation.showMoreCursor != null || expectedReplies <= 0
}

/** This page as the timeline cache keeps it, with its cursors. */
fun XTimelinePage.cached(): CachedTimeline = CachedTimeline(threads, bottomCursor, topCursor, showMoreCursor)

/** A kept page read back as X's; which posts were pinned is not kept, the profile says it again. */
fun CachedTimeline.page(): XTimelinePage = XTimelinePage(threads, bottomCursor, topCursor, showMoreCursor = showMoreCursor)
