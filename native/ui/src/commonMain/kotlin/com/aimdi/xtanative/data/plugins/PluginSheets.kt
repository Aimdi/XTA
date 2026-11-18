package com.aimdi.xtanative.data.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.saved.Keepable

/** A sheet a plugin opened over the app's screens, one at a time ([com.aimdi.xtanative.ui.plugins.PluginHost]'s archive and group calls), or a cashtag's. */
sealed interface PluginSheet {
    /** The save sheet: [post]'s folders. */
    data class Folders(val post: PluginPostArchive) : PluginSheet

    /** The note editor of [post]'s note in Saved. */
    data class Note(val post: PluginPostArchive) : PluginSheet

    /** The group picker for the member [memberId]; [follow] runs once it is put in a group. */
    class Groups(val memberId: String, val follow: suspend () -> Unit) : PluginSheet

    /** Plugin [pluginId]'s post menu. */
    class Menu(val pluginId: String, val menu: PluginPostMenu) : PluginSheet

    /** The sheet of the cashtag [symbol] (without its `$`) long-pressed in an X post. */
    data class Cashtag(val symbol: String) : PluginSheet
}

/** [this] as Saved and the likes file it. */
fun PluginPostArchive.keepable(): Keepable = Keepable(id, authorId, content)

/** A running plugin's entry in a post sheet, in the reader's language: [run] once the sheet has closed. */
class SheetEntry(val icon: ImageVector, val label: String, val run: () -> Unit)

/**
 * The running plugins' entries for a post in a post sheet, in [language]
 * (the app gives them from its plugins); none where nothing gave them.
 */
val LocalSheetEntries = staticCompositionLocalOf<@Composable (post: PluginSharedPost, language: String) -> List<SheetEntry>> { { _, _ -> emptyList() } }

