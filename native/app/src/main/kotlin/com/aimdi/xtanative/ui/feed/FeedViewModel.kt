package com.aimdi.xtanative.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FollowingFeed
import com.aimdi.xtanative.core.feed.feedReads
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUi(
    val threads: List<XThread> = emptyList(),
    /** Read from disk: what the feed showed last, until X answers. */
    val fromCache: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Nothing to show, and why. */
    val problem: Problem? = null,
    /** Some chunks failed while others answered. */
    val partial: Problem? = null,
    val moreProblem: Problem? = null,
    val followed: Int = 0,
    /** X has answered at least once since launch. */
    val answered: Boolean = false,
)

/** The Following feed for a null [groupId], else that group's feed. */
class FeedViewModel(private val graph: AppGraph, private val groupId: String?) : ViewModel() {
    private val cache = graph.feedCache(groupId)

    private val state = MutableStateFlow(FeedUi())
    val ui: StateFlow<FeedUi> = state.asStateFlow()

    private var feed: FollowingFeed? = null
    private var refreshJob: Job? = null
    private var generation = 0

    private data class Shape(val members: List<FeedMember>, val replies: Boolean, val reposts: Boolean)

    init {
        viewModelScope.launch {
            val cached = cache.read()
            if (cached.isNotEmpty() && state.value.threads.isEmpty()) {
                graph.posts.remember(cached)
                state.update { it.copy(threads = cached, fromCache = true) }
            }
        }
        viewModelScope.launch {
            graph.sessions.state.filterNotNull()
                .map { Shape(it.membersOf(groupId), it.includeReplies, it.includeReposts) }
                .distinctUntilChanged()
                .collect { shape ->
                    feed = FollowingFeed(shape.members, graph.client.feedReads(), shape.replies, shape.reposts)
                    state.update { it.copy(followed = shape.members.size) }
                    refresh()
                }
        }
    }

    fun refresh() {
        val feed = feed ?: return
        refreshJob?.cancel()
        val run = ++generation
        refreshJob = viewModelScope.launch {
            state.update { it.copy(refreshing = true, problem = null, moreProblem = null) }
            val page = try {
                feed.first()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(refreshing = false, problem = Problem.of(error).takeIf { _ -> it.threads.isEmpty() }, partial = Problem.of(error)) }
                return@launch
            }
            if (run != generation) return@launch
            graph.posts.remember(page.threads)
            val failed = Problem.of(page.failures)
            state.update {
                if (page.threads.isEmpty() && failed != null) {
                    // Keep what was shown: an empty answer from a failing X is not an empty feed.
                    it.copy(refreshing = false, problem = failed.takeIf { _ -> it.threads.isEmpty() }, partial = failed, hasMore = false)
                } else {
                    it.copy(
                        threads = page.threads,
                        fromCache = false,
                        refreshing = false,
                        hasMore = page.hasMore,
                        problem = null,
                        partial = failed,
                        answered = true,
                    )
                }
            }
            if (page.threads.isNotEmpty()) cache.write(page.threads)
        }
    }

    fun loadMore() {
        val feed = feed ?: return
        val now = state.value
        if (now.refreshing || now.loadingMore || !now.hasMore || now.fromCache) return
        val run = generation
        viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            val page = try {
                feed.more()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
                return@launch
            }
            if (run != generation) {
                state.update { it.copy(loadingMore = false) }
                return@launch
            }
            graph.posts.remember(page.threads)
            state.update {
                val known = it.threads.mapTo(HashSet()) { thread -> thread.id }
                it.copy(
                    threads = it.threads + page.threads.filter { thread -> thread.id !in known },
                    loadingMore = false,
                    hasMore = page.hasMore,
                    moreProblem = if (page.threads.isEmpty()) Problem.of(page.failures) else null,
                )
            }
        }
    }

    fun dismissPartial() = state.update { it.copy(partial = null) }
}
