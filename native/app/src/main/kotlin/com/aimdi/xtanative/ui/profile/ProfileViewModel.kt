package com.aimdi.xtanative.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.MergedTimeline
import com.aimdi.xtanative.core.feed.TimelineSource
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileTab { POSTS, REPLIES, MEDIA }

data class TabUi(
    val threads: List<XThread> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val problem: Problem? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
    val hasMore: Boolean = false,
)

data class ProfileUi(
    val user: XUser? = null,
    /** Read from X, not only what a post said of its author. */
    val complete: Boolean = false,
    val loading: Boolean = true,
    val problem: Problem? = null,
    val tab: ProfileTab = ProfileTab.POSTS,
    val tabs: Map<ProfileTab, TabUi> = emptyMap(),
    /** The pinned post, above the others in Posts. */
    val pinned: List<XThread> = emptyList(),
) {
    fun of(tab: ProfileTab): TabUi = tabs[tab] ?: TabUi()
}

/**
 * A profile: its name and picture at once from the feed, then the rest from X,
 * and three tabs read as they are opened. Posts holds what it posted and what
 * it reposted, in time order, as XTA shows them; X keeps the two apart.
 */
class ProfileViewModel(private val graph: AppGraph, private val screenName: String) : ViewModel() {
    private val state = MutableStateFlow(ProfileUi(user = graph.posts.user(screenName)))
    val ui: StateFlow<ProfileUi> = state.asStateFlow()

    private var posts: MergedTimeline? = null
    private val cursors = HashMap<ProfileTab, String?>()

    @Volatile
    private var pinned: List<XThread> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val (user, _) = graph.client.profile(screenName)
                graph.posts.remember(user)
                posts = postsOf(user.id)
                state.update { it.copy(user = user, complete = true, loading = false, tabs = emptyMap()) }
                loadTab(state.value.tab)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    fun select(tab: ProfileTab) {
        state.update { it.copy(tab = tab) }
        val now = state.value.of(tab)
        if (!now.loaded && !now.loading) loadTab(tab)
    }

    /** Posts and reposts, merged; the pinned post is kept apart for the top. */
    private fun postsOf(userId: String) = MergedTimeline(
        listOf(
            TimelineSource("posts") { cursor ->
                val page = graph.client.userPosts(userId, cursor)
                if (cursor == null) pinned = page.threads.filter { it.id in page.pinnedIds }
                page.copy(threads = page.threads.filterNot { it.id in page.pinnedIds })
            },
            TimelineSource("reposts") { cursor -> graph.client.userReposts(userId, cursor) },
        ),
    )

    private fun loadTab(tab: ProfileTab) {
        val user = state.value.user?.takeIf { state.value.complete } ?: return
        viewModelScope.launch {
            setTab(tab) { it.copy(loading = true, problem = null) }
            try {
                val (threads, more) = when (tab) {
                    ProfileTab.POSTS -> posts!!.first().let { page ->
                        if (page.threads.isEmpty() && pinned.isEmpty()) Problem.of(page.failures)?.let { throw Failed(it) }
                        page.threads to page.hasMore
                    }
                    ProfileTab.REPLIES -> paged(tab, graph.client.userReplies(user.id))
                    ProfileTab.MEDIA -> paged(tab, graph.client.userMedia(user.id))
                }
                graph.posts.remember(threads + pinned)
                state.update { it.copy(pinned = if (tab == ProfileTab.POSTS) pinned else it.pinned) }
                setTab(tab) { it.copy(threads = threads, hasMore = more, loading = false, loaded = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Failed) {
                setTab(tab) { it.copy(loading = false, problem = error.problem) }
            } catch (error: Throwable) {
                setTab(tab) { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    fun loadMore() {
        val tab = state.value.tab
        val now = state.value.of(tab)
        val user = state.value.user ?: return
        if (!now.loaded || now.loading || now.loadingMore || !now.hasMore) return
        viewModelScope.launch {
            setTab(tab) { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val (threads, more) = when (tab) {
                    ProfileTab.POSTS -> posts!!.more().let { it.threads to it.hasMore }
                    ProfileTab.REPLIES -> paged(tab, graph.client.userReplies(user.id, cursors[tab]))
                    ProfileTab.MEDIA -> paged(tab, graph.client.userMedia(user.id, cursors[tab]))
                }
                graph.posts.remember(threads)
                setTab(tab) {
                    val known = it.threads.mapTo(HashSet()) { thread -> thread.id }
                    val fresh = threads.filter { thread -> thread.id !in known }
                    it.copy(threads = it.threads + fresh, hasMore = more && fresh.isNotEmpty(), loadingMore = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setTab(tab) { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }

    /**
     * A page of Replies or Media and whether there is more. Media comes as one
     * grid of posts per page; each post is its own row here.
     */
    private fun paged(tab: ProfileTab, page: XTimelinePage): Pair<List<XThread>, Boolean> {
        cursors[tab] = page.bottomCursor
        val threads = if (tab == ProfileTab.MEDIA) page.threads.flatMap { it.posts }.map { XThread(it.id, listOf(it)) } else page.threads
        return threads to (page.bottomCursor != null && threads.isNotEmpty())
    }

    private fun setTab(tab: ProfileTab, change: (TabUi) -> TabUi) =
        state.update { it.copy(tabs = it.tabs + (tab to change(it.of(tab)))) }

    private class Failed(val problem: Problem) : Exception()
}
