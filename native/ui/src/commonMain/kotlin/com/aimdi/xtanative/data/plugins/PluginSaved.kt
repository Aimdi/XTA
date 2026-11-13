package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginSavedPost

/** A plugin's own posts among those in Saved, as its saved list reads them ([com.aimdi.xtanative.ui.plugins.PluginHost.savedPosts]). */
object PluginSaved {
    /**
     * The posts of [library] whose record names plugin [id] (its `xtaPlugin`,
     * or a link record's source, as Saved sorts them by network), newest
     * first, each with the reader's note; one too large to have been kept is
     * left out.
     */
    fun posts(library: SavedLibrary, id: String): List<PluginSavedPost> = library.posts.mapNotNull { saved ->
        val content = saved.content?.takeIf { saved.about.network == id } ?: return@mapNotNull null
        PluginSavedPost(PluginPostArchive(saved.id, saved.authorId, content), saved.note)
    }
}
