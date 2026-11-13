package com.aimdi.xtanative.networks.mastodon

/** One reply of an opened conversation, as a screen lays it out. */
data class MastodonReplyRow(
    val post: MastodonPost,
    /** 0 for a reply to the opened post, one more per level below it. */
    val depth: Int,
    /** How many replies hang below it, at any depth. */
    val descendants: Int,
    /** Folded: its [descendants] are left out of the rows. */
    val collapsed: Boolean,
    /** In author focus, another account's reply kept only to connect the author's own. */
    val contextOnly: Boolean = false,
)

/** How replies to the same post are ordered. Mastodon ranks nothing: `/context` is the conversation as it happened. */
enum class MastodonReplyOrder { OLDEST, RECENT, MOST_LIKED }

/**
 * A conversation laid out from `/context`, as XTA's mastodon_thread_outline.dart
 * does: replies as a tree that is walked without recursion, cycles broken,
 * replies whose parent is missing kept at the top, and the ancestors in the
 * order their reply links give.
 */
object MastodonThreads {
    private data class Branch(val post: MastodonPost, val parent: String?, val depth: Int)

    /**
     * The rows of [thread]'s replies, depth first. Replies under a post in
     * [collapsed] are left out. With [authorOnly], only the opened post's
     * author's replies stay, with the replies leading to them as context.
     */
    fun replies(
        thread: MastodonThread,
        collapsed: Set<String> = emptySet(),
        authorOnly: Boolean = false,
        order: MastodonReplyOrder = MastodonReplyOrder.OLDEST,
    ): List<MastodonReplyRow> {
        val forest = forest(thread, order)
        val author = thread.status.acct.lowercase()
        val kept = if (authorOnly) authorContext(forest, author) else null
        val branches = if (kept == null) forest else forest.filter { it.post.id in kept }
        return visible(branches, collapsed, if (authorOnly) author else null)
    }

    /** The posts above [thread]'s status, oldest first: its reply chain last, any others the server sent before it. */
    fun ancestors(thread: MastodonThread): List<MastodonPost> {
        val posts = LinkedHashMap<String, MastodonPost>()
        thread.ancestors.filter { it.id != thread.status.id }.forEach { posts[it.id] = it }
        val chain = ArrayList<MastodonPost>()
        val seen = HashSet<String>()
        var parent = thread.status.replyToId
        while (parent != null && parent in posts && seen.add(parent)) {
            val post = posts.getValue(parent)
            chain += post
            parent = post.replyToId
        }
        return posts.values.filter { it.id !in seen } + chain.asReversed()
    }

    /** Each descendant once, under its parent when the parent is among them, siblings ordered as asked. */
    private fun forest(thread: MastodonThread, order: MastodonReplyOrder): List<Branch> {
        val excluded = thread.ancestors.map { it.id }.toSet() + thread.status.id
        val posts = LinkedHashMap<String, MastodonPost>()
        thread.descendants.filter { it.id !in excluded }.forEach { posts[it.id] = it }
        val children = posts.values
            .groupBy { post -> post.replyToId?.takeIf { it != post.id && it in posts } }
            .mapValues { (_, siblings) -> ordered(siblings, order).map { it.id } }
        val visited = HashSet<String>()
        return (children[null].orEmpty() + posts.keys).flatMap { root -> if (root in visited) emptyList() else walk(root, posts, children, visited) }
    }

    private fun walk(root: String, posts: Map<String, MastodonPost>, children: Map<String?, List<String>>, visited: MutableSet<String>): List<Branch> {
        val branches = ArrayList<Branch>()
        val pending = ArrayDeque(listOf(Triple(root, null as String?, 0)))
        while (pending.isNotEmpty()) {
            val (id, parent, depth) = pending.removeLast()
            if (!visited.add(id)) continue
            branches += Branch(posts.getValue(id), parent, depth)
            children[id].orEmpty().asReversed().forEach { pending.addLast(Triple(it, id, depth + 1)) }
        }
        return branches
    }

    private fun ordered(posts: List<MastodonPost>, order: MastodonReplyOrder): List<MastodonPost> = when (order) {
        MastodonReplyOrder.OLDEST -> posts
        MastodonReplyOrder.RECENT -> posts.sortedWith(compareBy<MastodonPost> { it.publishedAt == null }.thenByDescending { it.publishedAt })
        MastodonReplyOrder.MOST_LIKED -> posts.sortedByDescending { it.favouritesCount }
    }

    /** The author's replies and every reply on the way up to them. */
    private fun authorContext(branches: List<Branch>, author: String): Set<String> {
        val parents = branches.associate { it.post.id to it.parent }
        val kept = HashSet<String>()
        branches.filter { it.post.acct.lowercase() == author }.forEach { branch ->
            var current: String? = branch.post.id
            while (current != null && kept.add(current)) current = parents[current]
        }
        return kept
    }

    private fun visible(branches: List<Branch>, collapsed: Set<String>, contextAuthor: String?): List<MastodonReplyRow> {
        val counts = descendantCounts(branches)
        val hidden = HashSet<String>()
        val rows = ArrayList<MastodonReplyRow>()
        for (branch in branches) {
            val id = branch.post.id
            if (branch.parent != null && branch.parent in hidden) {
                hidden += id
                continue
            }
            val count = counts[id] ?: 0
            val folded = count > 0 && id in collapsed
            rows += MastodonReplyRow(branch.post, branch.depth, count, folded, contextAuthor != null && branch.post.acct.lowercase() != contextAuthor)
            if (folded) hidden += id
        }
        return rows
    }

    private fun descendantCounts(branches: List<Branch>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        branches.asReversed().forEach { branch ->
            branch.parent?.let { counts[it] = (counts[it] ?: 0) + (counts[branch.post.id] ?: 0) + 1 }
        }
        return counts
    }
}
