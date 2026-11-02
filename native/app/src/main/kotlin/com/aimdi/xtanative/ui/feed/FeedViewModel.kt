package com.aimdi.xtanative.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.FollowingFeed
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.feed.feedReads
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.FeedCache
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

/** Where to put the reader, once: back at their place, or at the top after a pull. [token] tells two jumps apart. */
data class Jump(val threadIndex: Int, val offset: Int, val token: Int)

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
    /** Posts a refresh the reader did not ask for put above them, not yet scrolled up to. */
    val newAbove: Int = 0,
    val jump: Jump? = null,
)

/** The Following feed for a null [groupId], else that group's feed. */
class FeedViewModel(private val graph: AppGraph, private val groupId: String?) : ViewModel() {
    private val cache = graph.feedCache(groupId)
    private val state = MutableStateFlow(FeedUi())
    val ui: StateFlow<FeedUi> = state.asStateFlow()

    private var feed: FollowingFeed? = null

    /** The feed's reading matches what is shown: newer posts can go above it, and more below. */
    private var continuous = false
    private var refreshJob: Job? = null
    private var resets = 0
    private var jumps = 0

    private data class Shape(val members: List<FeedMember>, val replies: Boolean, val reposts: Boolean)

    init {
        viewModelScope.launch { retryWhenBackOnline() }
        viewModelScope.launch {
            var saved = restoreFromDisk()
            graph.sessions.state.filterNotNull()
                .map { Shape(it.membersOf(groupId), it.includeReplies, it.includeReposts) }
                .distinctUntilChanged()
                .collect { shape ->
                    val next = FollowingFeed(shape.members, graph.client.feedReads(), shape.replies, shape.reposts)
                    // The saved reading is good for the follows and switches it was saved with only.
                    val savedState = saved?.state
                    continuous = savedState != null && next.restore(savedState, state.value.threads.map { it.id })
                    saved = null
                    feed = next
                    state.update { it.copy(followed = shape.members.size, hasMore = continuous && savedState?.hasMore == true) }
                    refresh(byReader = false)
                }
        }
    }

    /**
     * Reads again when the connection comes back and the feed said there was
     * none, once the refresh under way has ended: the phone may have come
     * online while it was failing.
     */
    private suspend fun retryWhenBackOnline() {
        graph.online.collect { online ->
            if (!online) return@collect
            refreshJob?.join()
            val now = state.value
            if (now.problem == Problem.Offline || now.partial == Problem.Offline) refresh(byReader = false)
        }
    }

    /** The posts and place of the last run, shown at once. */
    private suspend fun restoreFromDisk(): SavedFeed? {
        val saved = cache.read()?.takeIf { it.threads.isNotEmpty() } ?: return null
        val place = cache.readPlace()
        graph.posts.remember(saved.threads)
        val index = place?.let { p -> saved.threads.indexOfFirst { it.id == p.postId } } ?: -1
        state.update {
            if (it.threads.isNotEmpty()) {
                it
            } else {
                it.copy(threads = saved.threads, fromCache = true, jump = if (index >= 0 && place != null) Jump(index, place.offset, ++jumps) else null)
            }
        }
        return saved
    }

    /**
     * New posts. [byReader] is a pull: the newest are shown at once. Otherwise
     * (on launch, after a settings change) they go above the reader, who keeps
     * their place and is told how many there are.
     */
    fun refresh(byReader: Boolean = true) {
        val feed = feed ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            state.update { it.copy(refreshing = true, problem = null, moreProblem = null) }
            val top = state.value.threads.firstOrNull()?.id?.toLongOrNull()
            if (continuous && top != null && putNewerAbove(feed, top, byReader)) return@launch
            startOver(feed)
        }
    }

    /** True when the newer posts met the ones shown; false when a fresh start is needed. */
    private suspend fun putNewerAbove(feed: FollowingFeed, top: Long, byReader: Boolean): Boolean {
        val newer = try {
            feed.newer(than = top)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.update { it.copy(refreshing = false, partial = Problem.of(error)) }
            return true
        }
        if (!newer.bridged) return false
        graph.posts.remember(newer.threads)
        state.update {
            val fresh = newer.threads.filter { thread -> it.threads.none { shown -> shown.id == thread.id } }
            it.copy(
                threads = fresh + it.threads,
                fromCache = false,
                refreshing = false,
                answered = true,
                partial = Problem.of(newer.failures),
                newAbove = if (byReader) 0 else it.newAbove + fresh.size,
                jump = if (byReader && fresh.isNotEmpty()) Jump(0, 0, ++jumps) else it.jump,
            )
        }
        persist()
        return true
    }

    /** The newest posts from the top, in place of what was shown. */
    private suspend fun startOver(feed: FollowingFeed) {
        resets++
        val page = try {
            feed.first()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.update { it.copy(refreshing = false, problem = Problem.of(error).takeIf { _ -> it.threads.isEmpty() }, partial = Problem.of(error)) }
            return
        }
        graph.posts.remember(page.threads)
        val failed = Problem.of(page.failures)
        if (page.threads.isEmpty() && failed != null) {
            // An empty answer from a failing X is not an empty feed: what was shown stays.
            state.update { it.copy(refreshing = false, problem = failed.takeIf { _ -> it.threads.isEmpty() }, partial = failed) }
            return
        }
        continuous = true
        state.update {
            it.copy(
                threads = page.threads,
                fromCache = false,
                refreshing = false,
                hasMore = page.hasMore,
                problem = null,
                partial = failed,
                answered = true,
                newAbove = 0,
                jump = if (it.threads.isNotEmpty()) Jump(0, 0, ++jumps) else it.jump,
            )
        }
        persist()
    }

    fun loadMore() {
        val feed = feed ?: return
        val now = state.value
        if (!continuous || now.loadingMore || !now.hasMore) return
        val run = resets
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
            if (run != resets) {
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
            persist()
        }
    }

    /** The posts shown and where the feed's reading stands, for the next launch. */
    private fun persist() {
        val feed = feed ?: return
        viewModelScope.launch {
            val reading = feed.state()
            val threads = state.value.threads
            val whole = threads.size <= FeedCache.KEPT && continuous
            cache.write(SavedFeed(threads.take(FeedCache.KEPT), if (whole) reading else null))
        }
    }

    /** The post at the top of the screen, kept as the app goes to the background. */
    fun keepPlace(threadIndex: Int, offset: Int) {
        val thread = state.value.threads.getOrNull(threadIndex) ?: return
        viewModelScope.launch { cache.writePlace(FeedPlace(thread.id, offset)) }
    }

    fun jumped() = state.update { it.copy(jump = null) }

    fun seenNew() = state.update { if (it.newAbove == 0) it else it.copy(newAbove = 0) }

    fun dismissPartial() = state.update { it.copy(partial = null) }
}
