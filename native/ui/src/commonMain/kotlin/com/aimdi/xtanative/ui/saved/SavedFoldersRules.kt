package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.ArchiveFilter
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.saved.XtaTweetJson
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.SavedPrefs
import kotlinx.serialization.json.JsonElement

/** A row of Manage folders (saved_folders_screen.dart): a built-in tab, shown or hidden, or a folder with its count. */
sealed interface FolderRow {
    val token: String

    /** [shown] is the setting that shows it on the strip; null for Notes, which always shows. */
    data class BuiltIn(override val token: String, val shown: Pref<Boolean>?) : FolderRow

    data class Folder(val folder: SavedFolder, val count: Int) : FolderRow {
        override val token: String get() = folder.id
    }

    companion object {
        /** Every tab in the strip's order, the reader's where they set one. */
        fun of(library: SavedLibrary, storedOrder: List<String>): List<FolderRow> {
            val folders = SavedLibrary.ordered(library.folders)
            return SavedTabs.ordered(folders, storedOrder).mapNotNull { token ->
                when (token) {
                    SavedTabs.ALL -> BuiltIn(token, SavedPrefs.showAllTab)
                    SavedTabs.UNFILED -> BuiltIn(token, SavedPrefs.showUnfiledTab)
                    SavedTabs.FAVORITES -> BuiltIn(token, SavedPrefs.showFavoritesTab)
                    SavedTabs.NOTES -> BuiltIn(token, null)
                    else -> folders.firstOrNull { it.id == token }?.let { Folder(it, library.countIn(it.id)) }
                }
            }
        }
    }
}

/**
 * Where a row dragged from [from] by [offset] pixels lands among rows of
 * [heights]: past each neighbour once more than half of it is crossed.
 */
fun dropIndex(from: Int, offset: Float, heights: List<Int>): Int {
    if (from !in heights.indices) return from
    var target = from
    var left = kotlin.math.abs(offset)
    val step = if (offset > 0) 1 else -1
    var next = from + step
    while (offset != 0f && next in heights.indices && left > heights[next] / 2f) {
        left -= heights[next]
        target = next
        next += step
    }
    return target
}

/** What the device keeps of an X post: the post shown (the post reposted, for a repost), as XTA's tweet JSON. */
data class Keepable(val id: String, val authorId: String?, val content: JsonElement) {
    companion object {
        fun of(post: XPost): Keepable = post.shown.let { Keepable(it.id, it.author.id.ifEmpty { null }, XtaTweetJson.encode(it)) }
    }
}

/** What a profile's Saved tab says when it has nothing: no likes yet, or no saves yet. */
fun profileSavedEmptyIsLikes(filter: ArchiveFilter): Boolean = filter == ArchiveFilter.LIKES
