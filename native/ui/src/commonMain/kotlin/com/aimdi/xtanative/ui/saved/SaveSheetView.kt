package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.saved.FolderPicker
import com.aimdi.xtanative.store.saved.QuickPick
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary

/**
 * Where the save sheet stands (save_sheet.dart): what is searched, whether
 * All folders is open, and the folders made in it, newest first.
 */
data class SaveSheetView(val query: String = "", val showAll: Boolean = false, val createdIds: List<String> = emptyList()) {
    fun search(text: String) = copy(query = text)

    fun showingAll() = copy(showAll = true)

    /** [id] was made from the search, which it empties; it leads the chips from now on. */
    fun created(id: String) = copy(query = "", createdIds = listOf(id) + createdIds)

    /** The chips, from the folders in the order the sheet opened with. */
    fun chips(library: SavedLibrary, openOrder: List<String>): QuickPick.Chips<SavedFolder> {
        val ordered = QuickPick.ordered(SavedLibrary.ordered(library.folders), openOrder, createdIds) { it.id }
        return QuickPick.chips(ordered, query, showAll, { it.name })
    }

    companion object {
        /** The order the chips open in, fixed once so a chip never moves under a finger: the latest saves' folders first. */
        fun openOrder(library: SavedLibrary): List<String> =
            FolderPicker.openOrder(library.posts, SavedLibrary.ordered(library.folders)).map { it.id }

        /** The label over the chips goes when there are none, except to say that nothing matched. */
        fun labelShown(chips: QuickPick.Chips<*>): Boolean = chips.items.isNotEmpty() || chips.section == QuickPick.Section.NO_MATCH
    }
}
