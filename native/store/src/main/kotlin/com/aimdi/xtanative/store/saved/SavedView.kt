package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tabs of Saved: four built in, then one per folder, whose token is its
 * id. Their order and which built-in ones show are settings; these are the
 * rules that turn those settings into the tabs on screen.
 */
object SavedTabs {
    const val ALL = "all"
    const val UNFILED = "unfiled"
    const val FAVORITES = "favorites"
    const val NOTES = "notes"

    val builtIn: Set<String> = setOf(ALL, UNFILED, FAVORITES, NOTES)

    fun isBuiltIn(token: String): Boolean = token in builtIn

    /**
     * Every tab, in [stored] order where the reader set one: tokens of folders
     * since deleted are dropped, and a tab the stored order lacks (a new
     * folder, or a built-in one) follows the rest in its default place
     * relative to them: All, the folders, Unfiled, Likes, Notes.
     */
    fun ordered(folders: List<SavedFolder>, stored: List<String>?): List<String> {
        val defaults = listOf(ALL) + folders.map { it.id } + listOf(UNFILED, FAVORITES, NOTES)
        val kept = stored.orEmpty().filter { it in defaults }.distinct()
        return kept + defaults.filterNot { it in kept }
    }

    /** The order as XTA's `saved.tab_order` setting holds it, a JSON list; anything unreadable is no order. */
    fun decodeOrder(text: String?): List<String> = text?.let { strings(J.parse(it)) }.orEmpty()

    fun encodeOrder(tokens: List<String>): String = JsonArray(tokens.map(::JsonPrimitive)).toString()

    /** [tokens] with the tab at [from] dragged to [to]. */
    fun moved(tokens: List<String>, from: Int, to: Int): List<String> {
        if (from !in tokens.indices) return tokens
        val rest = tokens.toMutableList().apply { removeAt(from) }
        return rest.apply { add(to.coerceIn(0, rest.size), tokens[from]) }
    }

    /** The tabs the strip shows. Unfiled shows only beside folders: without any it is All again. Notes always shows. */
    fun shown(order: List<String>, folders: List<SavedFolder>, showAll: Boolean, showUnfiled: Boolean, showFavorites: Boolean): List<String> =
        order.filter { token ->
            when (token) {
                ALL -> showAll
                UNFILED -> showUnfiled && folders.isNotEmpty()
                FAVORITES -> showFavorites
                NOTES -> true
                else -> folders.any { it.id == token }
            }
        }

    /** Whether [tab] can still be open: a deleted folder or a hidden tab falls back to All. */
    fun reachable(tab: String, folders: List<SavedFolder>, showUnfiled: Boolean, showFavorites: Boolean): Boolean = when (tab) {
        ALL, NOTES -> true
        UNFILED -> showUnfiled && folders.isNotEmpty()
        FAVORITES -> showFavorites
        else -> folders.any { it.id == tab }
    }
}

enum class SavedSort { NEWEST, OLDEST }

/** Which empty message an empty Saved shows. */
enum class SavedEmptyKind { SEARCH, LIKES, SAVED, FOLDER, NOTES }

/**
 * What Saved shows: the open tab, the network picked, the search, media only,
 * the order, likes by group, and the posts selected. Each change returns a
 * new view; the lists come from [posts], [likes] and [notes].
 */
data class SavedView(
    val tab: String = SavedTabs.ALL,
    val source: SavedSource = SavedSource.ALL,
    val query: String = "",
    val mediaOnly: Boolean = false,
    val sort: SavedSort = SavedSort.NEWEST,
    val likesByGroup: Boolean = false,
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
) {
    /** The search as matched: trimmed and lowercased once. */
    val needle: String get() = query.trim().lowercase()

    /** Likes and notes keep their own order; only saved posts can be turned around. */
    val sortable: Boolean get() = tab != SavedTabs.FAVORITES && tab != SavedTabs.NOTES

    val emptyKind: SavedEmptyKind get() = when {
        needle.isNotEmpty() -> SavedEmptyKind.SEARCH
        tab == SavedTabs.FAVORITES -> SavedEmptyKind.LIKES
        tab == SavedTabs.NOTES -> SavedEmptyKind.NOTES
        tab == SavedTabs.ALL -> SavedEmptyKind.SAVED
        else -> SavedEmptyKind.FOLDER
    }

    /** Opens [tab], ending a selection; Likes tapped while open breaks the likes down by group, or back. */
    fun select(tab: String): SavedView =
        if (tab == SavedTabs.FAVORITES && this.tab == SavedTabs.FAVORITES) copy(likesByGroup = !likesByGroup)
        else copy(tab = tab, likesByGroup = false, selecting = false, selected = emptySet())

    /** Starts selecting, with [id] selected when given; the media grid gives way to the list it selects in. */
    fun beginSelection(id: String? = null): SavedView = copy(selecting = true, mediaOnly = false, selected = selected + listOfNotNull(id))

    fun toggle(id: String): SavedView = copy(selecting = true, selected = if (id in selected) selected - id else selected + id)

    /** Selects every post of [visible], or none when all of them already are. */
    fun selectAll(visible: List<String>): SavedView = copy(selecting = true, selected = if (allSelected(visible)) emptySet() else visible.toSet())

    fun allSelected(visible: List<String>): Boolean = visible.isNotEmpty() && selected.size == visible.size && selected.containsAll(visible)

    fun finishSelection(): SavedView = copy(selecting = false, selected = emptySet())

    /** This view, or All when its tab is no longer [SavedTabs.reachable]. */
    fun reconciled(folders: List<SavedFolder>, showUnfiled: Boolean, showFavorites: Boolean): SavedView =
        if (SavedTabs.reachable(tab, folders, showUnfiled, showFavorites)) this else select(SavedTabs.ALL)

    /** The saved posts the open tab shows: its folder, the search (post or note), the network, the order, media only. */
    fun posts(saved: List<SavedPost>): List<SavedPost> {
        val inTab = when (tab) {
            SavedTabs.ALL -> saved
            SavedTabs.UNFILED -> saved.filter { it.folderId == null }
            else -> saved.filter { it.folderId == tab }
        }
        val needle = needle
        val found = if (needle.isEmpty()) inTab else inTab.filter { it.note.orEmpty().lowercase().contains(needle) || it.about.matches(needle) }
        val sorted = found.filter { it.about.from(source) }.let { if (sort == SavedSort.OLDEST) it.reversed() else it }
        return if (mediaOnly) sorted.filter { it.about.hasMedia } else sorted
    }

    /** The likes shown: the search, the network, media only, newest first. */
    fun likes(likes: List<LikedPost>): List<LikedPost> {
        val needle = needle
        val found = if (needle.isEmpty()) likes else likes.filter { it.about.matches(needle) }
        val fromSource = found.filter { it.about.from(source) }
        return if (mediaOnly) fromSource.filter { it.about.hasMedia } else fromSource
    }

    /** The notes listed: those the search finds, each thread once by its first note still kept. */
    fun notes(notes: List<LocalPost>): List<LocalPost> {
        val needle = needle
        return LocalPosts.roots(if (needle.isEmpty()) notes else notes.filter { LocalPosts.recordMatches(it, needle) })
    }
}

/** Which kept posts a profile's Saved tab shows. */
enum class ArchiveFilter { ALL, LIKES, BOOKMARKS }

/** A kept post as a profile's Saved tab lists it. */
data class ArchiveItem(val id: String, val content: JsonElement?)

/**
 * The saved and liked posts of [authorId], as a profile's Saved tab lists
 * them: saves in their order, then likes not already saved.
 */
fun profileArchive(saved: List<SavedPost>, liked: List<LikedPost>, authorId: String, filter: ArchiveFilter): List<ArchiveItem> {
    val saves = saved.filter { it.authorId == authorId }.map { ArchiveItem(it.id, it.content) }
    val hearts = liked.filter { it.authorId == authorId }.map { ArchiveItem(it.id, it.content) }
    return when (filter) {
        ArchiveFilter.BOOKMARKS -> saves
        ArchiveFilter.LIKES -> hearts
        ArchiveFilter.ALL -> (saves + hearts).distinctBy { it.id }
    }
}
