package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.StoreDir
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Saved posts from every network and their folders, newest first, kept by
 * [KeptPosts] under `saved/library`: saving, filing or noting a post writes
 * that one post, not the library. Posts and folders change in one write, so
 * moving a folder's posts out as it is deleted can never half happen. Saving
 * is local: nothing is bookmarked on any network.
 */
class SavedStore(
    dir: StoreDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val kept = KeptPosts(dir, "library", SavedShape)

    /** The library with what was kept of each post; null before [library] first reads it. */
    val state: StateFlow<SavedLibrary?> = kept.whole

    /**
     * The library without the posts' content: enough to say what is saved,
     * where, by whom, when and under which note, and to list the folders, at
     * a fraction of the memory. Null before the first call of any kind.
     */
    val index: StateFlow<SavedLibrary?> = kept.bare

    /** The library with every post's content, which reads each post kept the first time. */
    suspend fun library(): SavedLibrary = kept.whole()

    /** The library as [index] holds it. */
    suspend fun index(): SavedLibrary = kept.bare()

    /** What was kept of post [id] alone; null when it is not saved or nothing was kept. */
    suspend fun contentOf(id: String): JsonElement? = kept.content(id)

    /**
     * Files the post in [folderId]; a post not yet saved is saved there,
     * newest first, with [content] as it is now. A post already saved keeps
     * its place and what was kept of it, and only changes folder.
     */
    suspend fun file(id: String, authorId: String?, content: JsonElement?, folderId: String? = null): SavedPost {
        var known = false
        val filed = kept.edit { library ->
            val before = library.post(id)
            known = before != null
            when {
                before == null -> SavedPost(id, authorId, content, folderId, savedAt = clock()).let { Edit(library.copy(posts = listOf(it) + library.posts), it, fresh = id) }
                before.folderId == folderId -> Edit(library, before)
                else -> before.copy(folderId = folderId).let { moved -> Edit(library.withPosts { if (it.id == id) moved else it }, moved) }
            }
        }
        return if (known) filed.copy(content = kept.content(id)) else filed
    }

    /** Moves [ids] into [folderId], or out of every folder with null. */
    suspend fun setFolder(ids: Collection<String>, folderId: String?) {
        val selected = ids.toSet()
        kept.edit { library ->
            Edit(
                if (library.posts.none { it.id in selected && it.folderId != folderId }) library
                else library.withPosts { if (it.id in selected && it.folderId != folderId) it.copy(folderId = folderId) else it },
                Unit,
            )
        }
    }

    /** The reader's note on [id], trimmed; a blank one removes it. */
    suspend fun setNote(id: String, note: String?) {
        val noted = note?.trim()?.takeIf { it.isNotEmpty() }
        kept.edit { library ->
            Edit(
                if (library.post(id)?.let { it.note != noted } != true) library
                else library.withPosts { if (it.id == id) it.copy(note = noted) else it },
                Unit,
            )
        }
    }

    /** Takes [ids] out of Saved, and deletes what was kept of each post. */
    suspend fun remove(ids: Collection<String>) {
        val removed = ids.toSet()
        kept.edit { library ->
            Edit(if (library.posts.none { it.id in removed }) library else library.copy(posts = library.posts.filterNot { it.id in removed }), Unit)
        }
    }

    suspend fun remove(id: String) = remove(listOf(id))

    /** [remove], answering what it took, content and all, for [putBack]; null when none of [ids] was saved. */
    suspend fun removeTaking(ids: Collection<String>): TakenSaved? {
        val removed = ids.toSet()
        var taken: TakenSaved? = null
        kept.rewrite { library ->
            PutBack.taken(library.posts, { it.id in removed }, { it.id })?.let { took ->
                taken = took
                library.copy(posts = library.posts.filterNot { it.id in removed })
            }
        }
        return taken
    }

    /** Undoes [removeTaking]: the posts back where they were; false when one is saved again since, and nothing changes. */
    suspend fun putBack(taken: TakenSaved): Boolean {
        var done = false
        kept.rewrite { library -> PutBack.saved(library, taken)?.also { done = true } }
        return done
    }

    /** A new folder named [name] (trimmed) after the others; null when the name is blank. */
    suspend fun createFolder(name: String, autoDownload: Boolean = false, autoUpload: Boolean = false): SavedFolder? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return kept.edit { library ->
            val folder = SavedFolder(newId(), trimmed, library.folders.size, clock(), autoDownload, autoUpload)
            Edit(library.copy(folders = library.folders + folder), folder)
        }
    }

    /** Renames [id] and, when given, changes what saving into it does. A blank name changes nothing. */
    suspend fun updateFolder(id: String, name: String, autoDownload: Boolean? = null, autoUpload: Boolean? = null) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        kept.edit { library ->
            Edit(
                if (library.folder(id) == null) library
                else library.copy(folders = library.folders.map { folder ->
                    if (folder.id != id) folder
                    else folder.copy(name = trimmed, autoDownload = autoDownload ?: folder.autoDownload, autoUpload = autoUpload ?: folder.autoUpload)
                }),
                Unit,
            )
        }
    }

    /** Deletes the folder; its posts stay saved, unfiled. */
    suspend fun deleteFolder(id: String) {
        kept.edit { library ->
            Edit(
                if (library.folder(id) == null && library.posts.none { it.folderId == id }) library
                else SavedLibrary(
                    posts = library.posts.map { if (it.folderId == id) it.copy(folderId = null) else it },
                    folders = library.folders.filterNot { it.id == id },
                ),
                Unit,
            )
        }
    }

    /** [deleteFolder], answering the folder and the posts it held, for [putBack]; null when there was no such folder. */
    suspend fun deleteFolderTaking(id: String): TakenFolder? {
        var taken: TakenFolder? = null
        kept.edit { library ->
            val folder = library.folder(id) ?: return@edit Edit(library, Unit)
            taken = TakenFolder(folder, library.posts.filter { it.folderId == id }.mapTo(HashSet()) { it.id })
            Edit(
                SavedLibrary(
                    posts = library.posts.map { if (it.folderId == id) it.copy(folderId = null) else it },
                    folders = library.folders.filterNot { it.id == id },
                ),
                Unit,
            )
        }
        return taken
    }

    /** Undoes [deleteFolderTaking] ([PutBack.folder]); false when a folder of its id is there again. */
    suspend fun putBack(taken: TakenFolder): Boolean {
        var done = false
        kept.edit { library -> Edit(PutBack.folder(library, taken)?.also { done = true } ?: library, Unit) }
        return done
    }

    /** Everything kept, for the backup. */
    suspend fun content(): SavedLibrary = library()

    /** Replaces everything kept with [library], as a restored backup does; folders are put in XTA's order. */
    suspend fun replace(library: SavedLibrary) {
        kept.rewrite { SavedLibrary(library.posts.distinctBy { it.id }, SavedLibrary.ordered(library.folders.distinctBy { it.id })) }
    }

    /**
     * A restore: [merge] gets what is kept now and gives what to keep (null
     * for no change), under the same lock as every save, so a post saved
     * while a long restore runs is not written over.
     */
    suspend fun restore(merge: (SavedLibrary) -> SavedLibrary?) {
        kept.rewrite { now ->
            merge(now)?.let { SavedLibrary(it.posts.distinctBy { post -> post.id }, SavedLibrary.ordered(it.folders.distinctBy { folder -> folder.id })) }
        }
    }

    private fun SavedLibrary.withPosts(change: (SavedPost) -> SavedPost) = copy(posts = posts.map(change))
}
