package com.aimdi.xtanative.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XCommunity
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.feed.HiddenPosts
import com.aimdi.xtanative.ui.feed.MutedEverywhere
import com.aimdi.xtanative.ui.search.Narrowed
import com.aimdi.xtanative.ui.search.Page
import com.aimdi.xtanative.ui.search.PageEnd
import com.aimdi.xtanative.ui.search.PagedRead
import com.aimdi.xtanative.ui.search.PastEmptied
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityUi(
    val community: XCommunity? = null,
    val loading: Boolean = true,
    val problem: Problem? = null,
    val tab: CommunityTab = CommunityTab.TOP,
    /** The pinned posts among the Top posts. */
    val pinnedIds: Set<String> = emptySet(),
)

/** An X community, read only: its header, then its Top posts once the header is in. A guest may read one. */
class CommunityViewModel(private val graph: AppGraph, private val id: String, about: Boolean) : ViewModel() {
    private val state = MutableStateFlow(CommunityUi(tab = CommunityPage.openingTab(about)))
    val ui: StateFlow<CommunityUi> = state.asStateFlow()

    val posts = PagedRead<XThread, String>(PageEnd.POSTS) { it.id }

    init {
        load()
        viewModelScope.launch { HiddenPosts.of(graph.profileFilters).collect { hidden -> posts.narrow { HiddenPosts.withoutAuthors(it, hidden) } } }
        // The words muted in every feed: a community folds nothing, so a word set to fold hides here.
        viewModelScope.launch { MutedEverywhere.of(graph.settings).collect { muted -> posts.narrow { MutedEverywhere.shown(it, muted) } } }
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val community = graph.client.community(id)
                state.update { it.copy(community = community, loading = false) }
                readPosts()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    private fun readPosts() {
        posts.start(viewModelScope) { from ->
            PastEmptied.read(from) { cursor ->
                val page = graph.client.communityPosts(id, cursor)
                graph.posts.remember(page.threads)
                state.update { it.copy(pinnedIds = it.pinnedIds + page.pinnedIds) }
                val kept = HiddenPosts.withoutAuthors(page.threads, graph.profileFilters.filters())
                Narrowed(MutedEverywhere.shown(kept, MutedEverywhere.of(graph.settings.load())), page.threads.size, page.bottomCursor)
            }
        }
    }

    fun select(tab: CommunityTab) = state.update { it.copy(tab = tab) }

    fun retryPosts() = posts.retry(viewModelScope)

    fun morePosts() = posts.more(viewModelScope)
}

/** A community's members, paged as X sends them. */
class CommunityMembersViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    val members = PagedRead<XUser, String>(PageEnd.FRESH) { it.id }

    init {
        members.start(viewModelScope) { cursor ->
            val page = graph.client.communityMembers(id, cursor)
            page.users.forEach { graph.posts.remember(it) }
            Page(page.users, page.bottomCursor)
        }
    }

    fun retry() = members.retry(viewModelScope)

    fun more() = members.more(viewModelScope)
}
