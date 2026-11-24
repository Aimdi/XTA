package com.aimdi.xtanative.store.saved

/**
 * What a removal took, as an undo puts it back: the [items] themselves, as
 * they were (what was kept of them, when, where), and the [order] of every
 * id before, so each goes back between the same neighbours.
 */
class Taken<P> internal constructor(val items: List<P>, internal val order: List<String>)

/** Saved posts taken out ([SavedStore.removeTaking]). */
typealias TakenSaved = Taken<SavedPost>

/** A like taken back ([LikesStore.unlikeTaking]). */
typealias TakenLike = Taken<LikedPost>

/** A note deleted, with its files' bytes ([LocalPostsStore.deleteTaking]). */
typealias TakenNote = Taken<LocalPost>

/** A folder deleted ([SavedStore.deleteFolderTaking]): the folder as it was and the posts that were filed in it. */
class TakenFolder internal constructor(val folder: SavedFolder, internal val members: Set<String>)

/** Putting back what a removal took: pure, so that an undo can be tested apart from the disk. */
internal object PutBack {
    /** [taken] when it took anything; null otherwise. */
    fun <P> taken(before: List<P>, removed: (P) -> Boolean, id: (P) -> String): Taken<P>? =
        before.filter(removed).takeIf { it.isNotEmpty() }?.let { Taken(it, before.map(id)) }

    /**
     * [now] with [taken]'s items back, each before the item that followed it
     * then and is still there (or after the last), so items added since keep
     * their places. Null when any of them is there again (saved, liked or
     * written anew since): putting the old one back would undo that too.
     */
    fun <P> into(now: List<P>, taken: Taken<P>, id: (P) -> String): List<P>? {
        val present = now.mapTo(HashSet(), id)
        if (taken.items.any { id(it) in present }) return null
        val back = taken.items.associateBy(id)
        val result = now.toMutableList()
        var next: String? = null
        taken.order.asReversed().forEach { key ->
            val item = back[key]
            when {
                item != null -> {
                    val at = next?.let { anchor -> result.indexOfFirst { id(it) == anchor } }?.takeIf { it >= 0 } ?: result.size
                    result.add(at, item)
                    next = key
                }
                key in present -> next = key
            }
        }
        return result
    }

    /** [library] with [taken]'s posts back as they were ([into]); a post whose folder was deleted since goes back unfiled. */
    fun saved(library: SavedLibrary, taken: TakenSaved): SavedLibrary? {
        val folders = library.folders.mapTo(HashSet()) { it.id }
        // A post's folder deleted since: the post goes back unfiled, as the folder's delete left the others.
        val items = taken.items.map { post -> if (post.folderId == null || post.folderId in folders) post else post.copy(folderId = null) }
        return into(library.posts, Taken(items, taken.order)) { it.id }?.let { library.copy(posts = it) }
    }

    /**
     * [library] with the folder back where it was, and its posts filed in it
     * again: those still saved and still unfiled (one filed elsewhere since
     * stays where the reader put it). Null when a folder of its id is there.
     */
    fun folder(library: SavedLibrary, taken: TakenFolder): SavedLibrary? {
        if (library.folder(taken.folder.id) != null) return null
        return SavedLibrary(
            posts = library.posts.map { if (it.id in taken.members && it.folderId == null) it.copy(folderId = taken.folder.id) else it },
            folders = library.folders + taken.folder,
        )
    }
}
