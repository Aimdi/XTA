package com.aimdi.xtanative.networks.substack

/** The order replies are shown in among their siblings: as the API sent them (best first), or by date. */
enum class SubstackCommentOrder { ORIGINAL, NEWEST, OLDEST }

/**
 * A comment as the discussion shows it: [depth] levels in, with [descendants]
 * replies under it. A [collapsed] one hides them; a [contextOnly] one is shown
 * only because a search matched a reply under it.
 */
data class SubstackCommentRow(
    val comment: SubstackComment,
    val depth: Int,
    val descendants: Int,
    val collapsed: Boolean,
    val contextOnly: Boolean,
)

/**
 * A post's discussion as rows, as XTA's comments store lays it out. Parent ids
 * decide nesting where there are any, else the flattened depth does. A
 * duplicate, an orphan or a cycle is shown once, and walks never recurse, so a
 * thread ten thousand replies deep is laid out as easily as a short one.
 */
object SubstackComments {
    private class Entry(val comment: SubstackComment, val parent: String?, val depth: Int)

    /**
     * The rows to show. A search keeps comments whose author and text hold
     * every word, with the comments above them as context; a [collapsed]
     * comment's replies are left out.
     */
    fun rows(
        comments: List<SubstackComment>,
        collapsed: Set<String> = emptySet(),
        order: SubstackCommentOrder = SubstackCommentOrder.ORIGINAL,
        query: String = "",
    ): List<SubstackCommentRow> {
        val outline = outline(comments, order)
        val terms = SubstackText.terms(query)
        val matched = outline.map { it.comment }.filter { comment ->
            "${comment.author.orEmpty()}\n${comment.body}".lowercase().let { text -> terms.all { it in text } }
        }.mapTo(HashSet()) { it.id }
        val included = HashSet(matched)
        outline.asReversed().forEach { if (it.comment.id in included && it.parent != null) included += it.parent }
        return visible(outline.filter { it.comment.id in included }, collapsed, matched)
    }

    private fun parents(comments: List<SubstackComment>): Map<String, String?> {
        val ids = comments.mapTo(HashSet()) { it.id }
        val parents = HashMap<String, String?>()
        val ancestry = ArrayList<SubstackComment>()
        for (comment in comments) {
            while (ancestry.isNotEmpty() && ancestry.last().depth >= comment.depth) ancestry.removeAt(ancestry.lastIndex)
            val parent = comment.parentId
            parents[comment.id] = if (parent != null) parent.takeIf { it != comment.id && it in ids } else ancestry.lastOrNull()?.id
            ancestry += comment
        }
        return parents
    }

    private fun outline(comments: List<SubstackComment>, order: SubstackCommentOrder): List<Entry> {
        val unique = LinkedHashMap<String, SubstackComment>()
        comments.filter { it.id.isNotEmpty() }.forEach { unique.putIfAbsent(it.id, it) }
        val parents = parents(unique.values.toList())
        val children = LinkedHashMap<String?, MutableList<String>>()
        unique.keys.forEach { children.getOrPut(parents[it]) { ArrayList() } += it }
        if (order != SubstackCommentOrder.ORIGINAL) children.values.forEach { it.sortWith(byDate(unique, order == SubstackCommentOrder.NEWEST)) }
        return walk(unique, children)
    }

    /** By date, undated last; the sort is stable, so equals keep the API's order. */
    private fun byDate(comments: Map<String, SubstackComment>, newest: Boolean) = Comparator<String> { a, b ->
        val left = comments.getValue(a).at
        val right = comments.getValue(b).at
        when {
            left == null || right == null -> if (left == right) 0 else if (left == null) 1 else -1
            newest -> right.compareTo(left)
            else -> left.compareTo(right)
        }
    }

    private fun walk(comments: Map<String, SubstackComment>, children: Map<String?, List<String>>): List<Entry> {
        val visited = HashSet<String>()
        val result = ArrayList<Entry>()
        for (id in children[null].orEmpty() + comments.keys) {
            if (id in visited) continue
            val pending = ArrayDeque(listOf(Entry(comments.getValue(id), null, 0)))
            while (pending.isNotEmpty()) {
                val next = pending.removeLast()
                if (!visited.add(next.comment.id)) continue
                result += next
                children[next.comment.id].orEmpty().asReversed().forEach { pending.addLast(Entry(comments.getValue(it), next.comment.id, next.depth + 1)) }
            }
        }
        return result
    }

    private fun visible(entries: List<Entry>, collapsed: Set<String>, matched: Set<String>): List<SubstackCommentRow> {
        val counts = HashMap<String, Int>()
        entries.asReversed().forEach { entry ->
            entry.parent?.let { counts[it] = (counts[it] ?: 0) + 1 + (counts[entry.comment.id] ?: 0) }
        }
        val hidden = HashSet<String>()
        val rows = ArrayList<SubstackCommentRow>()
        for (entry in entries) {
            val id = entry.comment.id
            if (entry.parent != null && entry.parent in hidden) {
                hidden += id
                continue
            }
            val count = counts[id] ?: 0
            val isCollapsed = count > 0 && id in collapsed
            rows += SubstackCommentRow(entry.comment, entry.depth, count, isCollapsed, id !in matched)
            if (isCollapsed) hidden += id
        }
        return rows
    }
}
