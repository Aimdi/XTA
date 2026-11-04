package com.aimdi.xtanative.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.ForYou
import com.aimdi.xtanative.core.feed.ForYouPage
import com.aimdi.xtanative.core.feed.FeedStops
import com.aimdi.xtanative.core.feed.HomeReads
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.feed.FeedSources
import com.aimdi.xtanative.ui.feed.MixedSources
import com.aimdi.xtanative.ui.feed.PluginFeeds
import com.aimdi.xtanative.ui.feed.PluginPostsState
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.problemOf
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

data class ForYouUi(
    val threads: List<XThread> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Nothing to show, and why. */
    val problem: Problem? = null,
    /** A refresh failed over what is shown. */
    val notice: Problem? = null,
    val moreProblem: Problem? = null,
    /** There is no X account to read For you with: XTA's missing-account state. */
    val noAccount: Boolean = false,
    /** X has answered since the tab opened. */
    val answered: Boolean = false,
    /** Zen mode stopped the feed after its pages: "That's everything for now". */
    val paused: Boolean = false,
    /** Grows when a refresh the reader asked for put new posts on top: back to the top then. */
    val toTop: Int = 0,
)

/**
 * For you (XTA's `x` source on the strip): the home timelines of the X
 * accounts left on in Home feed accounts, merged newest first, without
 * replies, without the posts of the groups kept off the home feeds, and in
 * zen mode paused after its pages; with the posts of what is followed in each
 * plugin whose "show in the home timeline" switch is on mixed in by time
 * ([feeds]), all of them, as XTA's For you takes them.
 */
class ForYouViewModel(private val graph: AppGraph, feeds: PluginFeeds = PluginFeeds.None) : ViewModel() {
    private val state = MutableStateFlow(ForYouUi())
    val ui: StateFlow<ForYouUi> = state.asStateFlow()

    private val mixed = MixedSources(
        viewModelScope,
        feeds,
        combine(feeds.ids, graph.settings.settings) { ids, settings ->
            FeedSources.asks(ids, emptyMap(), home = true, inHome = { PluginSettings.inHomeFeed(settings, it) }, kinds = FeedKinds.ALL)
        },
        ::problemOf,
    )

    /** The plugins' posts For you mixes in. */
    val plugin: StateFlow<PluginPostsState> = mixed.posts.state

    private data class Shape(val accounts: List<XAccount>, val off: Set<String>, val excluded: Set<String>, val pageCap: Int?)

    private val reads = HomeReads { account, cursor, count -> graph.client.homeTimeline(account, cursor, count) }
    private var shape: Shape? = null
    private var cursor: String? = null
    private var stops = FeedStops()
    private var refreshJob: Job? = null
    private var resets = 0

    init {
        viewModelScope.launch { retryWhenBackOnline() }
        viewModelScope.launch {
            combine(graph.sessions.state.filterNotNull(), graph.settings.settings) { session, settings ->
                Shape(
                    accounts = session.accounts,
                    off = settings[AccountPrefs.forYouHiddenAccountIds].toSet(),
                    excluded = session.excludedFromFollowing,
                    pageCap = ForYouRules.pageCap(settings[PostsPrefs.zenMode], settings[PostsPrefs.zenPageCap]),
                )
            }.distinctUntilChanged().collect { next ->
                shape = next
                refresh(byReader = false)
            }
        }
    }

    /** Reads again once the connection is back, when For you said there was none. */
    private suspend fun retryWhenBackOnline() {
        graph.online.collect { online ->
            if (!online) return@collect
            mixed.retryFailed()
            refreshJob?.join()
            val now = state.value
            if (now.problem == Problem.Offline || now.notice == Problem.Offline) refresh(byReader = false)
        }
    }

    /** The newest page, in place of what is shown; [byReader] is a pull, which also goes back to the top. */
    fun refresh(byReader: Boolean = true) {
        if (byReader) mixed.refresh(force = true)
        val shape = shape ?: return
        refreshJob?.cancel()
        resets++
        if (shape.accounts.isEmpty()) {
            state.value = ForYouUi(noAccount = true, answered = true)
            return
        }
        refreshJob = viewModelScope.launch {
            state.update { it.copy(refreshing = true, problem = null, notice = null, moreProblem = null, noAccount = false) }
            val page = try {
                read(shape, null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val problem = Problem.of(error)
                state.update { it.copy(refreshing = false, problem = problem.takeIf { _ -> it.threads.isEmpty() }, notice = problem.takeIf { _ -> it.threads.isNotEmpty() }) }
                return@launch
            }
            val step = FeedStops(pageCap = shape.pageCap).page(page.threads, first = true, isSeen = null, hasMore = page.next != null)
            stops = step.stops
            cursor = page.next
            graph.posts.remember(step.shown)
            state.update {
                it.copy(
                    threads = step.shown,
                    refreshing = false,
                    answered = true,
                    hasMore = page.next != null && stops.pause == null,
                    paused = stops.pause != null,
                    toTop = if (byReader) it.toTop + 1 else it.toTop,
                )
            }
        }
    }

    /** The next page under what is shown; a page that brings nothing new ends the feed. */
    fun loadMore() {
        val shape = shape ?: return
        val from = cursor
        val now = state.value
        if (from == null || now.loadingMore || !now.hasMore || now.refreshing) return
        val run = resets
        viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            val page = try {
                read(shape, from)
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
            val step = stops.page(page.threads, first = false, isSeen = null, hasMore = page.next != null)
            stops = step.stops
            cursor = page.next
            graph.posts.remember(step.shown)
            state.update {
                val threads = ForYouRules.appended(it.threads, step.shown)
                val grew = threads.size > it.threads.size
                it.copy(threads = threads, loadingMore = false, hasMore = grew && page.next != null && stops.pause == null, paused = stops.pause != null)
            }
        }
    }

    /** Zen mode's "Load more anyway": past the pause, then on reading. */
    fun readOn() {
        if (!stops.canContinue) return
        val step = stops.resume()
        stops = step.stops
        state.update { it.copy(threads = ForYouRules.appended(it.threads, step.shown), paused = false, hasMore = cursor != null) }
        loadMore()
    }

    fun dismissNotice() = state.update { it.copy(notice = null) }

    /** Whether the reader is down the list, where the plugins' new posts wait until they come back up or ask for them. */
    fun readingAway(away: Boolean) = mixed.posts.readingAway(away)

    /** Shows the plugins' posts that waited, as the new-posts pill does. */
    fun revealPlugins() = mixed.posts.reveal()

    /** Reads plugin [source] again, as its failure's retry does. */
    fun retryPlugin(source: String) = mixed.posts.retry(source)

    private suspend fun read(shape: Shape, cursor: String?): ForYouPage {
        val page = ForYou.page(shape.accounts, shape.off, cursor, reads, ForYouRules.PAGE_SIZE, shape.excluded)
        return page.copy(threads = ForYouRules.withoutReplies(page.threads))
    }
}
