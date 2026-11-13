package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
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
