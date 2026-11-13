package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.saved.FolderPicker
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.SavedPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.downloads.downloadDestination
import kotlinx.serialization.json.JsonElement

/** What a folder asks for once a post is filed in it. */
sealed interface AutoDownload {
    data object None : AutoDownload

    /** Downloads ask where to save each time, which a filing cannot answer. */
    data object NeedsFolder : AutoDownload

    data class Start(val requests: List<DownloadRequest>) : AutoDownload
}

/**
 * Filing posts in Saved (folder_picker.dart): where a plain tap on the
 * bookmark files one, what the folder picker remembers, and the pictures a
 * folder set to auto-download fetches. Saving is local: nothing is
 * bookmarked on any network.
 */
class SavedFiling(private val saved: SavedStore, private val settings: SettingsStore) {
    /** Where a plain tap files: the folder last picked while Remember this folder is on, if it still exists; else unfiled. */
    suspend fun rememberedFolder(): String? {
        val prefs = settings.load()
        val id = FolderPicker.rememberedFolder(prefs[SavedPrefs.stickyFolderEnabled], prefs[SavedPrefs.stickyFolderId]) ?: return null
        return id.takeIf { saved.library().folder(it) != null }
    }

    /** Files the post in [folderId] (null for unfiled), saving it first, then says what that folder asks to download. */
    suspend fun file(id: String, authorId: String?, content: JsonElement?, folderId: String?): AutoDownload {
        saved.file(id, authorId, content, folderId)
        val folder = folderId?.let { saved.library().folder(it) }
        return autoDownload(folder, SavedContent.of(content), downloadDestination(settings.load()))
    }

    /** After a pick in the sheet: the folder is where plain taps go next, while remembering is on. */
    suspend fun remember(picked: String?) {
        val prefs = settings.load()
        FolderPicker.toRemember(prefs[SavedPrefs.stickyFolderEnabled], picked)?.let { settings.set(SavedPrefs.stickyFolderId, it) }
    }

    /** Remember this folder switched; switched on, it adopts the folder the post is in now. */
    suspend fun setRemember(on: Boolean, currentFolder: String?) {
        settings.update { prefs ->
            val switched = prefs.with(SavedPrefs.stickyFolderEnabled, on)
            if (on) switched.with(SavedPrefs.stickyFolderId, currentFolder.orEmpty()) else switched
        }
    }

    /** True the first time only: the hint that a long press files into a folder. */
    suspend fun firstSave(): Boolean = once(SavedPrefs.folderHintShown)

    /** True the first time only: the notice that likes stay on this device. */
    suspend fun firstLike(): Boolean = once(SavedPrefs.likedFirstToastShown)

    private suspend fun once(shown: Pref<Boolean>): Boolean {
        val before = settings.load()[shown]
        if (!before) settings.set(shown, true)
        return !before
    }

    companion object {
        /** What filing [content] in [folder] downloads: its pictures when the folder auto-downloads, where the setting says. */
        fun autoDownload(folder: SavedFolder?, content: SavedContent, destination: DownloadDestination): AutoDownload {
            if (folder?.autoDownload != true) return AutoDownload.None
            val requests = FolderPicker.autoDownloads(content, destination) ?: return AutoDownload.NeedsFolder
            return if (requests.isEmpty()) AutoDownload.None else AutoDownload.Start(requests)
        }
    }
}

/** Where the save sheet says a post is. */
sealed interface SaveStatus {
    data object NotSaved : SaveStatus

    data object Unfiled : SaveStatus

    data class InFolder(val name: String) : SaveStatus

    companion object {
        fun of(library: SavedLibrary, id: String): SaveStatus {
            if (!library.isSaved(id)) return NotSaved
            return library.folderOf(id)?.let(library::folder)?.let { InFolder(it.name) } ?: Unfiled
        }
    }
}
