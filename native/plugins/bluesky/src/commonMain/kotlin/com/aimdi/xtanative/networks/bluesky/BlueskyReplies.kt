package com.aimdi.xtanative.networks.bluesky

import java.time.Instant

/** How the replies under an opened post are ordered: [RELEVANT] keeps the AppView's ranking, the rest sort on the device. */
enum class BlueskyReplySort { RELEVANT, RECENT, OLDEST, MOST_LIKED }

/**
 * A reply and the replies under it. [contextOnly] marks a reply by someone
 * else, kept only to connect the opened post's author's own replies when
 * those alone are shown. Not a data class: a branch can be thousands deep.
 */
class BlueskyReplyBranch(val post: BlueskyPost, val children: List<BlueskyReplyBranch>, val contextOnly: Boolean = false) {
    /** How many replies sit under this one, at any depth. */
    val descendants: Int = children.sumOf { 1 + it.descendants }
}

/** A reply as a list draws it: how deep it sits, and whether its branch is [collapsed]. */
data class BlueskyReplyRow(val branch: BlueskyReplyBranch, val depth: Int, val collapsed: Boolean)

/**
 * The replies of a [BlueskyThread] as a tree, as XTA's conversation view
 * builds it: by each reply's [BlueskyPost.replyToUri], every post once, the
 * opened post and the posts above it never again, cycles broken. Built and
 * walked without recursion, so no thread is too deep.
 */
object BlueskyReplies {
    private class Entry(val uri: String, val parent: String?)

    /**
     * The branches under the opened post. Siblings are ordered by [order]
     * without leaving their parents; a reply whose parent is not among the
     * replies starts a branch of its own. [authorOnly] keeps the opened
     * post's author's replies and the replies leading to them.
     */
    fun branches(
        thread: BlueskyThread,
        authorOnly: Boolean = false,
        order: BlueskyReplySort = BlueskyReplySort.RELEVANT,
    ): List<BlueskyReplyBranch> {
        val excluded = thread.ancestors.map { it.uri }.toSet() + thread.post.uri
        val posts = LinkedHashMap<String, BlueskyPost>()
        thread.replies.filter { it.uri.isNotEmpty() && it.uri !in excluded }.forEach { posts.putIfAbsent(it.uri, it) }
        val (nested, roots) = sorted(posts.values.toList(), order).partition { post ->
            post.replyToUri.let { it != null && it != post.uri && it in posts }
        }
        val children = nested.groupBy({ it.replyToUri!! }, { it.uri })
        val entries = preorder(children, roots.map { it.uri } + posts.keys)
        return build(entries, posts, if (authorOnly) thread.post else null)
    }

    /** The rows a list draws: every branch depth first, without what sits under a [collapsed] one. */
    fun visible(branches: List<BlueskyReplyBranch>, collapsed: Set<String>): List<BlueskyReplyRow> {
        val pending = ArrayDeque(branches.asReversed().map { it to 0 })
        val rows = ArrayList<BlueskyReplyRow>()
        while (pending.isNotEmpty()) {
            val (branch, depth) = pending.removeLast()
            val hidden = branch.descendants > 0 && branch.post.uri in collapsed
            rows += BlueskyReplyRow(branch, depth, hidden)
            if (!hidden) branch.children.asReversed().forEach { pending.addLast(it to depth + 1) }
        }
        return rows
    }

    /** The URIs of every reply with replies under it: what "collapse all" folds. */
    fun foldable(branches: List<BlueskyReplyBranch>): Set<String> =
        visible(branches, emptySet()).filter { it.branch.descendants > 0 }.map { it.branch.post.uri }.toSet()

    private fun sorted(posts: List<BlueskyPost>, order: BlueskyReplySort): List<BlueskyPost> = when (order) {
        BlueskyReplySort.RELEVANT -> posts
        BlueskyReplySort.RECENT -> posts.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(reverseOrder())) { it.publishedAt })
        BlueskyReplySort.OLDEST -> posts.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(naturalOrder())) { it.publishedAt })
        BlueskyReplySort.MOST_LIKED -> posts.sortedByDescending { it.likeCount }
    }

    /** Each post once, parents before their children, starting from each candidate not yet reached. */
    private fun preorder(children: Map<String, List<String>>, candidates: List<String>): List<Entry> {
        val seen = HashSet<String>()
        val ordered = ArrayList<Entry>()
        val pending = ArrayDeque<Entry>()
        candidates.forEach { candidate ->
            pending.addLast(Entry(candidate, null))
            while (pending.isNotEmpty()) {
                val entry = pending.removeLast()
                if (!seen.add(entry.uri)) continue
                ordered += entry
                children[entry.uri].orEmpty().asReversed().forEach { pending.addLast(Entry(it, entry.uri)) }
            }
        }
        return ordered
    }

    /** The branches of [entries], children first; a reply not by [author] is dropped unless it leads to one that is. */
    private fun build(entries: List<Entry>, posts: Map<String, BlueskyPost>, author: BlueskyPost?): List<BlueskyReplyBranch> {
        val under = HashMap<String?, MutableList<BlueskyReplyBranch>>()
        entries.asReversed().forEach { entry ->
            val post = posts.getValue(entry.uri)
            val children = under[entry.uri].orEmpty()
            val contextOnly = author != null && !sameAuthor(post, author)
            if (!contextOnly || children.isNotEmpty()) {
                under.getOrPut(entry.parent) { ArrayList() } += BlueskyReplyBranch(post, children.asReversed().toList(), contextOnly)
            }
        }
        return under[null].orEmpty().asReversed().toList()
    }

    private fun sameAuthor(post: BlueskyPost, author: BlueskyPost): Boolean =
        if (post.did.isNotEmpty() && author.did.isNotEmpty()) post.did == author.did
        else post.handle.isNotEmpty() && post.handle.equals(author.handle, ignoreCase = true)
}
