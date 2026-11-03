package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant

/** How a feed orders, and tells apart, what it read. */
object FeedOrder {
    /** A thread's place in time: its id, which X gives in time order, else its first post's. */
    fun timeOf(thread: XThread): Long = thread.id.toLongOrNull() ?: thread.posts.firstOrNull()?.id?.toLongOrNull() ?: 0

    fun newestFirst(threads: List<XThread>): List<XThread> = threads.sortedByDescending(::timeOf)

    /** A popular feed orders the same window by its first posts' likes; equal ones keep their order. */
    fun byLikes(threads: List<XThread>): List<XThread> = threads.sortedByDescending { it.posts.firstOrNull()?.likeCount ?: 0 }

    /**
     * The first of each thread, and of each leading post: successive search
     * windows and the chunks of one feed overlap, so the same post routinely
     * comes back, sometimes under another thread's id.
     */
    fun dedupe(threads: List<XThread>): List<XThread> {
        val threadIds = HashSet<String>()
        val postIds = HashSet<String>()
        return threads.filter { thread ->
            val lead = thread.posts.firstOrNull()?.id
            val fresh = thread.id !in threadIds && (lead == null || lead !in postIds)
            if (fresh) {
                threadIds += thread.id
                lead?.let(postIds::add)
            }
            fresh
        }
    }
}

/** Limits on how much of one account a load shows. */
object PostCaps {
    /** Zen mode keeps this many posts of an author per load. */
    const val ZEN_PER_AUTHOR = 4

    /** The page caps zen mode offers, in pages per feed session. */
    val ZEN_PAGE_CAPS = listOf(3, 5, 10, 20)

    /**
     * At most [capByAuthor] threads per author, walking [threads] in their
     * order, so a popular feed keeps its ranking and a recent one its newest.
     * An author missing from the map, or capped at 0, is not capped.
     */
    fun perAuthor(threads: List<XThread>, capByAuthor: Map<String, Int>): List<XThread> {
        if (capByAuthor.isEmpty()) return threads
        val counts = HashMap<String, Int>()
        return threads.filter { thread ->
            val author = thread.posts.firstOrNull()?.author?.id ?: return@filter true
            val cap = capByAuthor[author]?.takeIf { it > 0 } ?: return@filter true
            val count = counts.getOrDefault(author, 0)
            (count < cap).also { kept -> if (kept) counts[author] = count + 1 }
        }
    }

    /** Zen mode: newest first and no ranking, with only each author's few latest posts, so no one floods the page. */
    fun zen(threads: List<XThread>): List<XThread> {
        val counts = HashMap<String, Int>()
        return FeedOrder.newestFirst(threads).filter { thread ->
            val author = thread.posts.firstOrNull()?.author?.id ?: return@filter true
            val count = counts.getOrDefault(author, 0)
            (count < ZEN_PER_AUTHOR).also { kept -> if (kept) counts[author] = count + 1 }
        }
    }
}

/**
 * What a reader can hide of one profile wherever it shows up (XTA's profile
 * feed settings): its reposts, or its replies to others. Both go by the
 * lower-case handle.
 */
object ProfileFilters {
    /**
     * Whether [post] answers someone else. Answering yourself is a thread, not
     * a reply: hiding replies must not cut someone's own threads.
     */
    fun repliesToSomeoneElse(post: XPost?): Boolean {
        val target = post?.replyToScreenName ?: return false
        return target.lowercase() != post.author.screenName.lowercase()
    }

    fun withoutReposts(threads: List<XThread>, hidden: Set<String>): List<XThread> =
        if (hidden.isEmpty()) threads else threads.filterNot { hidesRepost(it, hidden) }

    fun withoutReplies(threads: List<XThread>, hidden: Set<String>): List<XThread> =
        if (hidden.isEmpty()) threads else threads.filterNot { hidesReply(it, hidden) }

    internal fun hidesRepost(thread: XThread, hidden: Set<String>): Boolean {
        val post = thread.posts.firstOrNull() ?: return false
        return post.isRepost && post.author.screenName.lowercase() in hidden
    }

    internal fun hidesReply(thread: XThread, hidden: Set<String>): Boolean {
        val post = thread.posts.firstOrNull() ?: return false
        return repliesToSomeoneElse(post) && post.author.screenName.lowercase() in hidden
    }
}

/**
 * Everything a feed does to the posts it read before showing them, as XTA's
 * group feed prepares a page: the profiles' own filters, the feed's rules and
 * the language filter hide; the sort orders; folded posts say why; author caps
 * and zen mode limit each account.
 */
data class FeedShaping(
    val sort: FeedSort = FeedSort.RECENT,
    val rules: FeedRules = FeedRules(),
    /** The most posts one load keeps of an account, by its id. */
    val postCaps: Map<String, Int> = emptyMap(),
    val language: LanguageFilter = LanguageFilter(),
    val zen: Boolean = false,
    /** Lower-case handles whose reposts the reader hid. */
    val hiddenReposts: Set<String> = emptySet(),
    /** Lower-case handles whose replies to others the reader hid. */
    val hiddenReplies: Set<String> = emptySet(),
    val facts: PostFacts = PostFacts.UNKNOWN,
) {
    /**
     * Whether [thread] may be shown at all. A feed asks this as it reads, so
     * what is hidden never takes a place on a page.
     */
    fun keeps(thread: XThread, now: Instant): Boolean =
        !ProfileFilters.hidesRepost(thread, hiddenReposts) &&
            !ProfileFilters.hidesReply(thread, hiddenReplies) &&
            rules.verdict(thread, MutedKeywords.active(rules.mutedKeywords, now), facts) != Verdict.Hide &&
            language.verdict(thread, facts) != Verdict.Hide

    /** One load's threads, already kept, as the feed shows them: ordered, folded where asked, capped. */
    fun page(threads: List<XThread>, now: Instant): Shaped {
        val active = MutedKeywords.active(rules.mutedKeywords, now)
        val ordered = if (sort == FeedSort.POPULAR) FeedOrder.byLikes(FeedOrder.newestFirst(threads)) else FeedOrder.newestFirst(threads)
        val capped = PostCaps.perAuthor(ordered, postCaps).let { if (zen) PostCaps.zen(it) else it }
        val folds = capped.mapNotNull { thread ->
            // A language's reason takes the place of a keyword's, as in XTA.
            val reason = (language.verdict(thread, facts) as? Verdict.Fold ?: rules.verdict(thread, active, facts) as? Verdict.Fold)?.reason
            reason?.let { thread.id to it }
        }.toMap()
        return Shaped(capped, folds)
    }

    /** XTA's whole pass over one load: deduplicated, hidden, then [page]. */
    fun shape(threads: List<XThread>, now: Instant = Instant.now()): Shaped = page(FeedOrder.dedupe(threads).filter { keeps(it, now) }, now)
}
