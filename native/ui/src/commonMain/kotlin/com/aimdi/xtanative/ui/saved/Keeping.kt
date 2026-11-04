package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.downloads.Downloading
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
    fun toggleSave(graph: AppGraph, item: Keepable) = graph.keep {
        if (graph.saved.library().isSaved(item.id)) {
            graph.saved.remove(item.id)
        } else {
            val filing = SavedFiling(graph.saved, graph.settings)
            file(graph, filing, item, filing.rememberedFolder())
            if (filing.firstSave()) KeepNotices.show(KeepNotice.FolderHint)
        }
    }

    /** Files [item] in [folderId] from the save sheet, which remembers the pick for plain taps. */
    fun fileFromSheet(graph: AppGraph, item: Keepable, folderId: String?) = graph.keep {
        val filing = SavedFiling(graph.saved, graph.settings)
        file(graph, filing, item, folderId)
        filing.remember(folderId)
    }

    /** The heart tapped: liked on this device, or no longer; the first like says that likes stay here. */
    fun toggleLike(graph: AppGraph, item: Keepable) = graph.keep { like(graph, item, !graph.likes.isLiked(item.id)) }

    /** [item] liked on this device, or no longer, as a network's own like mirrored into the likes is. */
    fun setLiked(graph: AppGraph, item: Keepable, liked: Boolean) = graph.keep { like(graph, item, liked) }

    /** The note on [item] in Saved, as its editor saves it: a post not saved yet is saved first, where a plain tap files. */
    suspend fun writeNote(graph: AppGraph, item: Keepable, note: String?) {
        if (!graph.saved.library().isSaved(item.id)) {
            val filing = SavedFiling(graph.saved, graph.settings)
            file(graph, filing, item, filing.rememberedFolder())
        }
        graph.saved.setNote(item.id, note)
    }

    private suspend fun like(graph: AppGraph, item: Keepable, liked: Boolean) {
        if (!liked) return graph.likes.unlike(item.id)
        val first = !graph.likes.isLiked(item.id)
        graph.likes.like(item.id, item.authorId, item.content)
        if (first && SavedFiling(graph.saved, graph.settings).firstLike()) KeepNotices.show(KeepNotice.LikesStayHere)
    }

    private suspend fun file(graph: AppGraph, filing: SavedFiling, item: Keepable, folderId: String?) {
        when (val auto = filing.file(item.id, item.authorId, item.content, folderId)) {
            AutoDownload.None -> Unit
            AutoDownload.NeedsFolder -> KeepNotices.show(KeepNotice.NeedsDownloadFolder)
            is AutoDownload.Start -> Downloading.batch(graph, auto.requests)
        }
    }
}

/** The ids liked here, as they change, read once for every heart on screen instead of once per heart. */
internal class LikedIds private constructor(graph: AppGraph) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val ids: StateFlow<Set<String>> =
        graph.likes.state.map { likes -> likes?.mapTo(HashSet()) { it.id }.orEmpty() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        scope.launch { runCatching { graph.likes.likes() } }
    }

    companion object {
        private val all = WeakHashMap<AppGraph, LikedIds>()

        @Synchronized
        fun of(graph: AppGraph): LikedIds = all.getOrPut(graph) { LikedIds(graph) }
    }
}
