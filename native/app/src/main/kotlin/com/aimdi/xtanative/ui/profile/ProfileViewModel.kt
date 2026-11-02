package com.aimdi.xtanative.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUi(
    val user: XUser? = null,
    /** Read from X, not only what a post said of its author. */
    val complete: Boolean = false,
    val posts: List<XThread> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
)

/** A profile: its name and picture at once from the feed, then the rest and its posts from X. */
class ProfileViewModel(private val graph: AppGraph, private val screenName: String) : ViewModel() {
    private val state = MutableStateFlow(ProfileUi(user = graph.posts.user(screenName)))
    val ui: StateFlow<ProfileUi> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val (user, _) = graph.client.profile(screenName)
                graph.posts.remember(user)
                state.update { it.copy(user = user, complete = true) }
                val page = graph.client.userPosts(user.id)
                graph.posts.remember(page.threads)
                state.update {
                    it.copy(
                        posts = page.threads,
                        pinnedIds = page.pinnedIds,
                        cursor = page.bottomCursor.takeIf { _ -> page.threads.isNotEmpty() },
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    fun loadMore() {
        val now = state.value
        val user = now.user ?: return
        val cursor = now.cursor ?: return
        if (now.loading || now.loadingMore || !now.complete) return
        viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = graph.client.userPosts(user.id, cursor)
                graph.posts.remember(page.threads)
                state.update {
                    val known = it.posts.mapTo(HashSet()) { thread -> thread.id }
                    val fresh = page.threads.filter { thread -> thread.id !in known }
                    it.copy(posts = it.posts + fresh, cursor = page.bottomCursor.takeIf { _ -> fresh.isNotEmpty() }, loadingMore = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }
}
