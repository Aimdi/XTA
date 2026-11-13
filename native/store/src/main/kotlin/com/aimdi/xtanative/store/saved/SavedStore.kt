package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.StoreDir
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Saved posts from every network and their folders, newest first, in
 * `saved/library.json`. Both live in the one file, so moving a folder's posts
 * out as it is deleted can never half happen. Saving is local: nothing is
 * bookmarked on any network.
 */
class SavedStore(
    dir: StoreDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val stored = StoredJson(dir, "saved/library.json", SavedLibrary(), SavedCodec::library, SavedCodec::library)

    /** The library once read; null before the first call reads it. */
    val state: StateFlow<SavedLibrary?> = stored.state

    suspend fun library(): SavedLibrary = stored.get()

    /**
     * Files the post in [folderId]; a post not yet saved is saved there,
     * newest first, with [content] as it is now. A post already saved keeps
     * its place and what was kept of it, and only changes folder.
     */
    suspend fun file(id: String, authorId: String?, content: JsonElement?, folderId: String? = null): SavedPost =
        stored.updateWith { library ->
            val known = library.post(id)
            when {
                known == null -> SavedPost(id, authorId, content, folderId, savedAt = clock()).let { library.copy(posts = listOf(it) + library.posts) to it }
                known.folderId == folderId -> library to known
                else -> known.copy(folderId = folderId).let { moved -> library.withPosts { if (it.id == id) moved else it } to moved }
            }
        }

    /** Moves [ids] into [folderId], or out of every folder with null. */
    suspend fun setFolder(ids: Collection<String>, folderId: String?) {
        val selected = ids.toSet()
        stored.update { library ->
            if (library.posts.none { it.id in selected && it.folderId != folderId }) library
            else library.withPosts { if (it.id in selected) it.copy(folderId = folderId) else it }
        }
    }

    /** The reader's note on [id], trimmed; a blank one removes it. */
    suspend fun setNote(id: String, note: String?) {
        val kept = note?.trim()?.takeIf { it.isNotEmpty() }
        stored.update { library ->
            if (library.post(id)?.let { it.note != kept } != true) library
            else library.withPosts { if (it.id == id) it.copy(note = kept) else it }
        }
    }

    suspend fun remove(ids: Collection<String>) {
        val removed = ids.toSet()
        stored.update { library ->
            if (library.posts.none { it.id in removed }) library else library.copy(posts = library.posts.filterNot { it.id in removed })
        }
    }

    suspend fun remove(id: String) = remove(listOf(id))

    /** A new folder named [name] (trimmed) after the others; null when the name is blank. */
    suspend fun createFolder(name: String, autoDownload: Boolean = false, autoUpload: Boolean = false): SavedFolder? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return stored.updateWith { library ->
            val folder = SavedFolder(newId(), trimmed, library.folders.size, clock(), autoDownload, autoUpload)
            library.copy(folders = library.folders + folder) to folder
        }
    }

    /** Renames [id] and, when given, changes what saving into it does. A blank name changes nothing. */
    suspend fun updateFolder(id: String, name: String, autoDownload: Boolean? = null, autoUpload: Boolean? = null) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        stored.update { library ->
            if (library.folder(id) == null) library
            else library.copy(folders = library.folders.map { folder ->
                if (folder.id != id) folder
                else folder.copy(name = trimmed, autoDownload = autoDownload ?: folder.autoDownload, autoUpload = autoUpload ?: folder.autoUpload)
            })
        }
    }

    /** Deletes the folder; its posts stay saved, unfiled. */
    suspend fun deleteFolder(id: String) {
        stored.update { library ->
            if (library.folder(id) == null && library.posts.none { it.folderId == id }) library
            else SavedLibrary(
                posts = library.posts.map { if (it.folderId == id) it.copy(folderId = null) else it },
                folders = library.folders.filterNot { it.id == id },
            )
        }
    }

    /** Everything kept, for the backup. */
    suspend fun content(): SavedLibrary = library()

    /** Replaces everything kept with [library], as a restored backup does; folders are put in XTA's order. */
    suspend fun replace(library: SavedLibrary) {
        stored.update {
            SavedLibrary(library.posts.distinctBy { it.id }, SavedLibrary.ordered(library.folders.distinctBy { it.id }))
        }
    }

    private fun SavedLibrary.withPosts(change: (SavedPost) -> SavedPost) = copy(posts = posts.map(change))
}
