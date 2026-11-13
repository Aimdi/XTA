package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.SavedFiling

/**
 * The note under a network's post in Saved (plugin_post_actions.dart's
 * editPluginPostNote). A post not saved yet is saved first, where a plain tap
 * on the bookmark files it. The note never leaves the device.
 */
class PluginNotes(private val saved: SavedStore, private val settings: SettingsStore) {
    suspend fun noteOf(id: String): String? = saved.library().post(id)?.note

    /** Keeps [note] under [item]; a blank one removes it. */
    suspend fun keep(item: Keepable, note: String?) {
        if (!saved.library().isSaved(item.id)) {
            saved.file(item.id, item.authorId, item.content, SavedFiling(saved, settings).rememberedFolder())
        }
        saved.setNote(item.id, note)
    }
}
