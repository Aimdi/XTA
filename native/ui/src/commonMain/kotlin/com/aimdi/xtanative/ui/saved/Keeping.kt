package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.saved.SavedFolder
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Saving and liking posts on this device (tweet_footer.dart,
 * folder_picker.dart). Nothing reaches the network a post came from: a save
 * is a row of Saved, a like a heart this device remembers. Each change is
 * written to its end even when the screen that asked for it goes away.
 */
object Keeping {
    /** The bookmark tapped: an unsaved post is saved where a plain tap files, a saved one is removed. */
    fun toggleSave(keeper: Keeper, item: Keepable) = keeper.keep {
        if (keeper.saved.index().isSaved(item.id)) {
            keeper.saved.remove(item.id)
        } else {
            val filing = SavedFiling(keeper.saved, keeper.settings)
            file(keeper, filing, item, filing.rememberedFolder())
            if (filing.firstSave()) KeepNotices.show(KeepNotice.FolderHint)
        }
    }

    /** Files [item] in [folderId] from the save sheet, which remembers the pick for plain taps. */
    fun fileFromSheet(keeper: Keeper, item: Keepable, folderId: String?) = keeper.keep {
        val filing = SavedFiling(keeper.saved, keeper.settings)
        file(keeper, filing, item, folderId)
        filing.remember(folderId)
    }

    /** The heart tapped: liked on this device, or no longer; the first like says that likes stay here. */
    fun toggleLike(keeper: Keeper, item: Keepable) = keeper.keep { like(keeper, item, !keeper.likes.isLiked(item.id)) }

    /** [item] liked on this device, or no longer, as a network's own like mirrored into the likes is. */
    fun setLiked(keeper: Keeper, item: Keepable, liked: Boolean) = keeper.keep { like(keeper, item, liked) }

    /** [item] liked on this device unless it is already, as a double-tap on a post likes it (tweet_footer.dart's withDoubleTapToLike). */
    fun likeHere(keeper: Keeper, item: Keepable) = keeper.keep { if (!keeper.likes.isLiked(item.id)) like(keeper, item, true) }

    /** A like a network keeps in its own likes (XTA's Bluesky, Threads and Substack hearts): the first like says that likes stay here, as one of these does. */
    fun likedElsewhere(keeper: Keeper) = keeper.keep { sayLikesStayHere(keeper) }

    /** The note on [item] in Saved, as its editor saves it: a post not saved yet is saved first, where a plain tap files. */
    suspend fun writeNote(keeper: Keeper, item: Keepable, note: String?) {
        if (!keeper.saved.index().isSaved(item.id)) {
            val filing = SavedFiling(keeper.saved, keeper.settings)
            file(keeper, filing, item, filing.rememberedFolder())
        }
        keeper.saved.setNote(item.id, note)
    }

    private suspend fun like(keeper: Keeper, item: Keepable, liked: Boolean) {
        if (!liked) return keeper.likes.unlike(item.id)
        val first = !keeper.likes.isLiked(item.id)
        keeper.likes.like(item.id, item.authorId, item.content)
        if (first) sayLikesStayHere(keeper)
    }

    /** Once, on the reader's first like: that likes stay on this device. */
    private suspend fun sayLikesStayHere(keeper: Keeper) {
        if (SavedFiling(keeper.saved, keeper.settings).firstLike()) KeepNotices.show(KeepNotice.LikesStayHere)
    }

    private suspend fun file(keeper: Keeper, filing: SavedFiling, item: Keepable, folderId: String?) {
        when (val auto = filing.file(item.id, item.authorId, item.content, folderId)) {
            AutoDownload.None -> Unit
            AutoDownload.NeedsFolder -> KeepNotices.show(KeepNotice.NeedsDownloadFolder)
            is AutoDownload.Start -> keeper.download(auto.requests)
        }
        folderId?.let { keeper.saved.index().folder(it) }?.let { folder -> FolderFilings.filed(keeper, folder, item) }
    }
}

/**
 * Who hears of a post filed in one of a keeper's folders besides Saved, as
 * XTA's filing hands a post on once its folder's downloads are under way:
 * the app's plugins, whose runner listens for its keeper.
 */
internal object FolderFilings {
    private val listeners = WeakHashMap<Keeper, (SavedFolder, Keepable) -> Unit>()

    @Synchronized
    fun listen(keeper: Keeper, listener: (SavedFolder, Keepable) -> Unit) {
        listeners[keeper] = listener
    }

    fun filed(keeper: Keeper, folder: SavedFolder, item: Keepable) {
        val listener = synchronized(this) { listeners[keeper] }
        listener?.invoke(folder, item)
    }
}

/** The ids liked here, as they change, read once for every heart on screen instead of once per heart, and without what was kept of each post. */
internal class LikedIds private constructor(keeper: Keeper) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val ids: StateFlow<Set<String>> =
        keeper.likes.index.map { likes -> likes?.mapTo(HashSet()) { it.id }.orEmpty() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        scope.launch { runCatching { keeper.likes.index() } }
    }

    companion object {
        private val all = WeakHashMap<Keeper, LikedIds>()

        @Synchronized
        fun of(keeper: Keeper): LikedIds = all.getOrPut(keeper) { LikedIds(keeper) }
    }
}
