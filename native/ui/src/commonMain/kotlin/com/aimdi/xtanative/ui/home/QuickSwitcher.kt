package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.search.LibrarySearch

/** What a place of the quick switcher is, named beside it. */
enum class QuickKind { SCREEN, TIMELINE, GROUP, ANTENNA, FOLDER, SETTINGS }

/** Where a place of the quick switcher leads. */
sealed interface QuickGo {
    /** A screen of its own, opened over the one shown. */
    data class Route(val route: String) : QuickGo

    /** A page of Home's bar, by its id: Home, turned to it. */
    data class HomePage(val id: String) : QuickGo

    /** A timeline of Home's strip, by its key ([HomeTab.key]): Home's Feed page, on it. */
    data class Timeline(val key: String) : QuickGo

    /** A tab of Saved (a folder, by its id): Saved, turned to it, as a page of Home's bar where it is one ([onBar]). */
    data class SavedTab(val tab: String, val onBar: Boolean) : QuickGo
}

/**
 * One place the quick switcher goes to: what it is called and of what
 * [kind], named as the row shows it ([label], so "groups" finds them all),
 * and where it leads.
 */
data class QuickTarget(val id: String, val title: String, val kind: QuickKind, val go: QuickGo, val label: String = "") {
    /** What the switcher ranks by, folded once rather than at every key ([LibrarySearch.fold]). */
    val folded: String = LibrarySearch.fold(title)

    /** What it finds by: the title and the label. */
    internal val haystack: String = if (label.isEmpty()) folded else folded + " " + LibrarySearch.fold(label)
}

/**
 * The desktop's quick switcher (Ctrl+P, Ctrl+K): a field over the reader's
 * own places (screens, Home's timelines, groups, antennas, Saved's folders,
 * Settings' pages), filtered as it is typed into; nothing it does reaches a
 * network.
 */
object QuickSwitch {
    /** How many places it lists at most. */
    const val SHOWN = 50

    /**
     * The places holding every word of [query] in their title or label (case and
     * accents count for nothing, as in the library search): those whose title
     * starts with it first, then those with a word that starts with its first
     * word, then the rest, each in the order given; all of them, in order, for
     * a blank query. No more than [limit].
     */
    fun matching(targets: List<QuickTarget>, query: String, limit: Int = SHOWN): List<QuickTarget> {
        val words = LibrarySearch.words(query)
        if (words.isEmpty()) return targets.take(limit)
        val whole = words.joinToString(" ")
        return targets
            .filter { target -> words.all(target.haystack::contains) }
            .sortedBy { rank(it.folded, whole, words.first()) }
            .take(limit)
    }

    private fun rank(title: String, whole: String, first: String): Int = when {
        title.startsWith(whole) -> 0
        title.split(' ').any { it.startsWith(first) } -> 1
        else -> 2
    }

    /** The row chosen after a move [by] rows from [index] among [count]: the arrows wrap round, as a menu's do; none while there are none. */
    fun moved(index: Int, count: Int, by: Int): Int = if (count <= 0) 0 else Math.floorMod(index + by, count)

    /** The row chosen after a jump of a page [by] rows (Page Up and Down), stopping at either end. */
    fun paged(index: Int, count: Int, by: Int): Int = if (count <= 0) 0 else (index + by).coerceIn(0, count - 1)

    /**
     * Where the list scrolls so the [chosen] row shows, its rows [first] to
     * [last] being in view: to it from above when it went up past the first,
     * so it ends the view when it went down past the last ([shown] rows
     * fit); null when it shows already.
     */
    fun scrolledTo(chosen: Int, first: Int, last: Int): Int? = when {
        chosen < first -> chosen
        chosen > last -> first + (chosen - last)
        else -> null
    }

    /** The same places once, the first of each id kept: a group both on the bar and among the groups. */
    fun distinct(targets: List<QuickTarget>): List<QuickTarget> = targets.distinctBy { it.go }
}
