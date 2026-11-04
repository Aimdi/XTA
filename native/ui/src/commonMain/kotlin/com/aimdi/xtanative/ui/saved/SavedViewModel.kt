package com.aimdi.xtanative.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.saved.LikedPost
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.SavedEmptyKind
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedSource
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.saved.SavedView
import com.aimdi.xtanative.store.settings.SavedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Saved shows: the open tab and its choices, its chips, and what the tab holds. */
data class SavedUi(
    val loaded: Boolean = false,
    val view: SavedView = SavedView(),
    val chips: List<SavedChip> = emptyList(),
    val library: SavedLibrary = SavedLibrary(),
    val notes: List<LocalPost> = emptyList(),
    val body: SavedBody = SavedBody.Empty(SavedEmptyKind.SAVED),
    /** What Select all takes. */
    val visible: List<String> = emptyList(),
) {
    val folders: List<SavedFolder> get() = SavedLibrary.ordered(library.folders)
    val filterMenu: SavedFilterMenu get() = SavedFilterMenu.of(view)
}

private data class Kept(val library: SavedLibrary, val likes: List<LikedPost>, val notes: List<LocalPost>)

/**
 * Saved (saved_screen.dart): saved posts by folder, likes (by group if the
 * reader asks), and notes, searched, filtered by network, turned around,
 * media only, and selected to move or remove. Everything here is the
 * device's: nothing is read from or sent to any network.
 */
class SavedViewModel(private val graph: AppGraph) : ViewModel() {
    private val view = MutableStateFlow(SavedView())
    private var typing: Job? = null

    private val kept = combine(graph.saved.state.filterNotNull(), graph.likes.state.filterNotNull(), graph.localPosts.state.filterNotNull()) { library, likes, notes ->
        Kept(library, likes, notes)
    }

    val ui: StateFlow<SavedUi> = combine(kept, graph.settings.settings, graph.sessions.state, view) { kept, settings, session, view ->
        val showUnfiled = settings[SavedPrefs.showUnfiledTab]
        val showFavorites = settings[SavedPrefs.showFavoritesTab]
        val shown = view.reconciled(kept.library.folders, showUnfiled, showFavorites)
        if (shown != view) this.view.compareAndSet(view, shown)
        val groups = FollowGroup.ordered(session?.groups.orEmpty())
        val body = savedBody(shown, kept.library.posts, kept.likes, kept.notes, groups)
        SavedUi(
            loaded = true,
            view = shown,
            chips = savedChips(kept.library.folders, settings[SavedPrefs.tabOrder], settings[SavedPrefs.showAllTab], showUnfiled, showFavorites),
            library = kept.library,
            notes = kept.notes,
            body = body,
            visible = visibleIds(body),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUi())

    init {
        viewModelScope.launch {
            graph.saved.library()
            graph.likes.likes()
            graph.localPosts.notes()
        }
    }

    /** A chip tapped: its tab opens; Likes tapped again breaks the likes down by group, or back. */
    fun select(tab: String) = view.update { it.select(tab) }

    fun pickSource(source: SavedSource) = view.update { it.copy(source = source) }

    /** What is typed in the search, matched once the typing pauses: a fast typist would filter everything per key. */
    fun search(text: String) {
        typing?.cancel()
        if (text.isEmpty()) {
            view.update { it.copy(query = "") }
            return
        }
        typing = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            view.update { it.copy(query = text) }
        }
    }

    fun filter(action: SavedFilterAction) = view.update { it.after(action) }

    fun beginSelection(id: String) = view.update { it.beginSelection(id) }

    fun toggle(id: String) = view.update { it.toggle(id) }

    fun selectAll() = view.update { it.selectAll(ui.value.visible) }

    fun finishSelection() = view.update { it.finishSelection() }

    /** The selected posts into [folderId], or out of every folder with null. */
    fun moveSelected(folderId: String?) {
        val ids = view.value.selected
        graph.keep { graph.saved.setFolder(ids, folderId) }
        finishSelection()
    }

    fun removeSelected() {
        val ids = view.value.selected
        graph.keep { graph.saved.remove(ids) }
        finishSelection()
    }

    /** A post long-pressed in the media grid, removed from the tab it is in. */
    fun removeFromGrid(id: String) {
        val likes = view.value.tab == SavedTabs.FAVORITES
        graph.keep { if (likes) graph.likes.unlike(id) else graph.saved.remove(id) }
    }

    fun createFolder(name: String, autoDownload: Boolean) = graph.keep { graph.saved.createFolder(name, autoDownload) }

    fun updateFolder(id: String, name: String, autoDownload: Boolean) = graph.keep { graph.saved.updateFolder(id, name, autoDownload) }

    /** Deletes the folder (its posts stay saved, unfiled); a tab on it goes back to All. */
    fun deleteFolder(id: String) {
        graph.keep { graph.saved.deleteFolder(id) }
        view.update { if (it.tab == id) it.select(SavedTabs.ALL) else it }
    }

    suspend fun setNote(id: String, note: String?) = graph.saved.setNote(id, note)

    fun deleteNote(id: String) = graph.keep { graph.localPosts.delete(id) }

    /** After a note is written: Notes opens, so the reader sees it. */
    fun showNotes() = view.update { if (it.tab == SavedTabs.NOTES) it else it.select(SavedTabs.NOTES) }

    private companion object {
        const val SEARCH_PAUSE_MS = 200L
    }
}
