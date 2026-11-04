package com.aimdi.xtanative.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.GroupFeed
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.feed.feedReads
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.FeedCache
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.group.FeedPrefs
import com.aimdi.xtanative.ui.group.GroupFeedRules
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.problemOf
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where to put the reader, once: back at their place, or at the top after a pull. [token] tells two jumps apart. */
data class Jump(val threadIndex: Int, val offset: Int, val token: Int)

data class FeedUi(
    val threads: List<XThread> = emptyList(),
    /** Why the folded threads are folded, by thread id: the feed's rules fold rather than hide. */
    val folds: Map<String, String> = emptyMap(),
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

/**
 * The Following feed for a null [groupId], else that group's feed, read as
 * its plan says (XTA's Following is its group `-1`): its order and custom
 * rules, its own reply and repost choices, the language filter and zen mode,
 * without the reposts and replies the reader hid on profiles ([HiddenPosts]);
 * with the plugins' posts mixed in by time ([feeds]): on the Following feed
 * those of every plugin whose "show in the home timeline" switch is on, on a
 * group's its members of other networks.
 */
class FeedViewModel(private val graph: AppGraph, private val groupId: String?, feeds: PluginFeeds = PluginFeeds.None) : ViewModel() {
    private val cache = graph.feedCache(groupId)
    private val state = MutableStateFlow(FeedUi())
    val ui: StateFlow<FeedUi> = state.asStateFlow()

    private val mixed = MixedSources(
        viewModelScope,
        feeds,
        combine(feeds.ids, graph.settings.settings, graph.sessions.state.filterNotNull()) { ids, settings, session -> asksOf(ids, settings, session) },
        ::problemOf,
    )

    /** The plugins' posts this feed mixes in. */
    val plugin: StateFlow<PluginPostsState> = mixed.posts.state

    private var feed: GroupFeed? = null

    /** The feed's reading matches what is shown: newer posts can go above it, and more below. */
    private var continuous = false

    /** A popular feed is ordered by likes: newer posts do not go above it, it is read from the top again. */
    private var popular = false
    private var refreshJob: Job? = null
    private var resets = 0
    private var jumps = 0

    init {
        viewModelScope.launch { retryWhenBackOnline() }
        viewModelScope.launch {
            var saved: SavedFeed? = null
            var first = true
            combine(graph.sessions.state.filterNotNull(), graph.settings.settings, HiddenPosts.of(graph.profileFilters)) { session, settings, hidden ->
                GroupFeedRules.plan(session, FeedPrefs.of(settings), groupId, emptySet(), hidden)
            }.distinctUntilChanged().collect { plan ->
                if (first) saved = restoreFromDisk(plan).also { first = false }
                val next = GroupFeed(plan, graph.client.feedReads(), graph.feedConcurrency())
                // The saved reading is good for the follows and switches it was saved with only.
                val savedState = saved?.state
                continuous = savedState != null && next.restore(savedState, state.value.threads.map { it.id })
                saved = null
                feed = next
                popular = plan.popular
                state.update { it.copy(followed = plan.members.size, hasMore = continuous && savedState?.hasMore == true) }
                refresh(byReader = false)
            }
        }
    }

    /**
     * Reads again when the connection comes back and the feed said there was
     * none, once the refresh under way has ended: the phone may have come
     * online while it was failing. The plugins that failed are read again too.
     */
    private suspend fun retryWhenBackOnline() {
        graph.online.collect { online ->
            if (!online) return@collect
            mixed.retryFailed()
            refreshJob?.join()
            val now = state.value
            if (now.problem == Problem.Offline || now.partial == Problem.Offline) refresh(byReader = false)
        }
    }

    /** What the feed asks of the plugins with a feed ([ids]): on the Following feed their followed accounts while their switch is on, on a group's its members of theirs. */
    private fun asksOf(ids: Set<String>, settings: Settings, session: Session): Map<String, SourceAsk> {
        val plan = session.feedPlan(groupId)
        val members = groupId?.let { PluginMembers.of(session, it, emptySet(), ids) }.orEmpty()
        val kinds = FeedKinds(plan.includeReplies, plan.includeReposts)
        return FeedSources.asks(ids, members, home = groupId == null, inHome = { PluginSettings.inHomeFeed(settings, it) }, kinds = kinds)
    }

    /** The posts and place of the last run, shown at once, as the feed's rules see them now ([plan]). */
    private suspend fun restoreFromDisk(plan: GroupFeedPlan): SavedFeed? {
        val saved = cache.read()?.takeIf { it.threads.isNotEmpty() } ?: return null
        val shown = FeedPreview.of(saved.threads, plan.shaping, Instant.now())
        val place = cache.readPlace()
        graph.posts.remember(shown.threads)
        val index = place?.let { p -> shown.threads.indexOfFirst { it.id == p.postId } } ?: -1
        state.update {
            if (it.threads.isNotEmpty()) {
                it
            } else {
                it.copy(
                    threads = shown.threads,
                    folds = shown.folds,
                    fromCache = true,
                    jump = if (index >= 0 && place != null) Jump(index, place.offset, ++jumps) else null,
                )
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
        if (byReader) mixed.refresh(force = true)
        val feed = feed ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            state.update { it.copy(refreshing = true, problem = null, moreProblem = null) }
            val top = state.value.threads.firstOrNull()?.id?.toLongOrNull()
            if (continuous && !popular && top != null && putNewerAbove(feed, top, byReader)) return@launch
            startOver(feed)
        }
    }

    /** True when the newer posts met the ones shown; false when a fresh start is needed. */
    private suspend fun putNewerAbove(feed: GroupFeed, top: Long, byReader: Boolean): Boolean {
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
                folds = it.folds + newer.foldReasons,
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
    private suspend fun startOver(feed: GroupFeed) {
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
                folds = page.foldReasons,
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
                    folds = it.folds + page.foldReasons,
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

    /** The post at the top of the screen, kept as the app goes to the background or its window closes. */
    fun keepPlace(threadIndex: Int, offset: Int) {
        val thread = state.value.threads.getOrNull(threadIndex) ?: return
        graph.keep { cache.writePlace(FeedPlace(thread.id, offset)) }
    }

    fun jumped() = state.update { it.copy(jump = null) }

    fun seenNew() {
        state.update { if (it.newAbove == 0) it else it.copy(newAbove = 0) }
        mixed.posts.reveal()
    }

    /** Whether the reader is down the list, where the plugins' new posts wait until they come back up or ask for them. */
    fun readingAway(away: Boolean) = mixed.posts.readingAway(away)

    /** Reads plugin [source] again, as its failure's retry does. */
    fun retryPlugin(source: String) = mixed.posts.retry(source)

    fun dismissPartial() = state.update { it.copy(partial = null) }
}
