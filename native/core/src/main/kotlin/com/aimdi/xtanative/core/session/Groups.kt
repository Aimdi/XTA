package com.aimdi.xtanative.core.session

/**
 * Groups nested in groups, as XTA nests them: a parent shows its children
 * under it, and its feed reads its own members and everything nested in it.
 * Rules over a child-to-parent map, so a loop in the data never hangs them.
 */
object GroupTree {
    /** [id] and everything nested in it, however deep. */
    fun withDescendants(id: String, parentOf: Map<String, String?>): Set<String> {
        val found = linkedSetOf(id)
        val pending = ArrayDeque(listOf(id))
        while (pending.isNotEmpty()) {
            childrenOf(pending.removeLast(), parentOf).filter(found::add).forEach(pending::addLast)
        }
        return found
    }

    /** The groups directly inside [id]. */
    fun childrenOf(id: String, parentOf: Map<String, String?>): List<String> = parentOf.filterValues { it == id }.keys.toList()

    /**
     * The groups that stand on their own, which is what a board shows. One
     * whose parent is gone, or that is its own parent, counts as top level
     * rather than becoming unreachable.
     */
    fun topLevel(ids: List<String>, parentOf: Map<String, String?>): List<String> {
        val present = ids.toSet()
        return ids.filter { id -> parentOf[id].let { it == null || it == id || it !in present } }
    }

    /**
     * [ids] with every group directly under its parent, parents first, each
     * level in the order given. A group in a loop belongs to no top level and
     * is placed after the rest rather than lost.
     */
    fun treeOrder(ids: List<String>, parentOf: Map<String, String?>): List<String> {
        val placed = LinkedHashSet<String>()
        fun place(id: String) {
            if (!placed.add(id)) return
            ids.filter { parentOf[it] == id && it != id }.forEach(::place)
        }
        topLevel(ids, parentOf).forEach(::place)
        ids.forEach(::place)
        return placed.toList()
    }

    /** Whether putting [child] inside [parent] would make a group contain itself. */
    fun wouldLoop(child: String, parent: String, parentOf: Map<String, String?>): Boolean =
        generateSequence(parent) { parentOf[it] }.distinctUntilLoop().any { it == child }

    /** How deep [id] sits, from 0 at the top. */
    fun depthOf(id: String, parentOf: Map<String, String?>): Int =
        generateSequence(parentOf[id]) { parentOf[it] }.distinctUntilLoop(setOf(id)).count()

    /** Stops a parent chain at the first group already passed. */
    private fun Sequence<String>.distinctUntilLoop(seen: Set<String> = emptySet()): Sequence<String> {
        val passed = seen.toMutableSet()
        return takeWhile(passed::add)
    }
}

/**
 * Groups read together for as long as the reader looks at them, alongside the
 * feed that is open: picked on a whim and dropped, never stored. Each brings
 * everything nested in it.
 */
data class CombinedGroups(val ids: Set<String> = emptySet()) {
    fun toggled(id: String): CombinedGroups = CombinedGroups(if (id in ids) ids - id else ids + id)

    fun cleared(): CombinedGroups = CombinedGroups()

    /** Without [id], for when it becomes the feed shown: a group is not read alongside itself. */
    fun released(id: String): CombinedGroups = if (id in ids) CombinedGroups(ids - id) else this

    /** Every group whose members [open]'s feed reads with these alongside. */
    fun covers(open: String, parentOf: Map<String, String?>): Set<String> =
        (setOf(open) + ids).flatMapTo(LinkedHashSet()) { GroupTree.withDescendants(it, parentOf) }
}

/** The order the "add to groups" picker opens in. */
object GroupPicks {
    /** How many groups "recently used" remembers. */
    const val RECENT_LIMIT = 12

    /**
     * The groups the account is in first (confirming one is the commonest
     * reason to open it), then the ones picked last, then the rest, each once
     * and only while it exists.
     */
    fun order(groupIds: List<String>, selected: Collection<String>, recent: List<String>): List<String> {
        val known = groupIds.toSet()
        return (groupIds.filter { it in selected } + recent.filter { it in known } + groupIds).distinct()
    }

    /** [added] at the front of "recently used". */
    fun remembered(recent: List<String>, added: Collection<String>): List<String> =
        if (added.isEmpty()) recent else (added + recent).distinct().take(RECENT_LIMIT)
}

/** Groups pinned as the deck's columns on a wide screen. */
object DeckGroups {
    /** XTA keeps the ids comma-separated. */
    fun parse(raw: String?): List<String> = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun join(ids: List<String>): String = ids.joinToString(",")

    /** [ids] with [id] pinned at the end, or taken out when it was pinned. */
    fun toggled(ids: List<String>, id: String): List<String> = if (id in ids) ids - id else ids + id

    /** A deck needs a tablet's shortest side or a wide window (in dp). */
    fun fits(widthDp: Float, shortestSideDp: Float): Boolean = shortestSideDp >= 600 || widthDp >= 900
}

/**
 * One column of the deck, as [Session.deck] keeps it: a group by its id, as
 * XTA kept the deck, or a timeline of Home's by a name no group id takes
 * (they start with `@`): Following, X's For you, a network's timeline.
 */
sealed interface DeckColumn {
    /** How [Session.deck] holds it. */
    val id: String

    data class Group(val groupId: String) : DeckColumn {
        override val id: String get() = groupId
    }

    data object Following : DeckColumn {
        override val id: String = "@following"
    }

    data object ForYou : DeckColumn {
        override val id: String = "@foryou"
    }

    /** A network's timeline, by its plugin's id. */
    data class Network(val networkId: String) : DeckColumn {
        override val id: String get() = NETWORK + networkId
    }

    companion object {
        private const val NETWORK = "@network:"

        /** The column [id] names; a group's id is any that is not one of Home's timelines. */
        fun of(id: String): DeckColumn? = when {
            id == Following.id -> Following
            id == ForYou.id -> ForYou
            id.startsWith(NETWORK) -> id.removePrefix(NETWORK).takeIf { it.isNotBlank() }?.let(::Network)
            id.startsWith("@") -> null
            id.isBlank() -> null
            else -> Group(id)
        }

        /** Whether [id] names one of Home's timelines rather than a group: a restore keeps it, as it keeps a group still there. */
        fun isTimeline(id: String): Boolean = of(id).let { it != null && it !is Group }
    }
}

/** The deck's columns as the reader arranges them. */
object DeckColumns {
    /** [ids] with the column at [from] moved by [by] places (-1 left, +1 right), as far as the ends allow. */
    fun moved(ids: List<String>, from: Int, by: Int): List<String> {
        if (from !in ids.indices) return ids
        val to = (from + by).coerceIn(0, ids.lastIndex)
        if (to == from) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** [ids] with [column] last, unless it is a column already. */
    fun added(ids: List<String>, column: DeckColumn): List<String> = if (column.id in ids) ids else ids + column.id

    /**
     * Each column's width in [available] dp for [count] columns of
     * [preferred] dp: wider to fill the window while they fit, else
     * [preferred], the deck scrolling sideways.
     */
    fun width(available: Float, count: Int, preferred: Float): Float =
        if (count <= 0) preferred else maxOf(preferred, available / count)
}
