package com.aimdi.xtanative.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedStops
import com.aimdi.xtanative.core.feed.FirstPageAction
import com.aimdi.xtanative.core.feed.ForYou
import com.aimdi.xtanative.core.feed.ForYouPage
import com.aimdi.xtanative.core.feed.HomeReads
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.group.GroupFeedPaging
import com.aimdi.xtanative.ui.group.ReadMarks
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
    /** Settings › Posts › Remember reading position: the timeline keeps where the reader left off. */
    val tracks: Boolean = false,
    /** The last read post as the timeline opened: the divider stays where it was for the whole visit. */
    val lastSeen: ReadPosition? = null,
    /** Back to the last read post, once, when the first page has posts above it: the divider's row. */
    val jump: Int? = null,
) {
    /** Where "You're caught up" goes: above the first read post, when something new sits above it. */
    val divider: Int? get() = GroupFeedPaging.dividerIndex(threads, lastSeen, tracks, catchUp = false)
}

/**
 * For you (XTA's `x` source, its X timeline): the home timelines of the X
 * accounts left on in Home feed accounts, merged newest first, without
 * replies and without the posts of the groups kept off the home feeds, and in
 * zen mode paused after its pages. It mixes in no other network: the plugins'
 * home-timeline posts join Following alone. With Settings › Posts › Remember
 * reading position it keeps where the reader left off (_for_you.dart): the
 * divider over the posts read before, the timeline opened at it, and the
 * newest post read from the top recorded, whose mark the unread dot compares.
 */
class ForYouViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow(ForYouUi())
    val ui: StateFlow<ForYouUi> = state.asStateFlow()

    private data class Shape(val accounts: List<XAccount>, val off: Set<String>, val excluded: Set<String>, val pageCap: Int?, val tracks: Boolean)

    private val reads = HomeReads { account, cursor, count -> graph.client.homeTimeline(account, cursor, count) }
    private val marks = ReadMarks(graph.feedCache(ReadMarks.key(FeedKeys.FOR_YOU)))
    private var shape: Shape? = null
    private var cursor: String? = null
    private var stops = FeedStops()
    private var refreshJob: Job? = null
    private var resets = 0

    /** The divider is placed once per visit, on the first page that has posts above the last read one. */
    private var placed = false
    private var atTop = true

    init {
        viewModelScope.launch { retryWhenBackOnline() }
        viewModelScope.launch {
            state.update { it.copy(lastSeen = marks.position()) }
            combine(graph.sessions.state.filterNotNull(), graph.settings.settings) { session, settings ->
                Shape(
                    accounts = session.accounts,
                    off = settings[AccountPrefs.disabledAccountIds].toSet(),
                    excluded = session.excludedFromFollowing,
                    pageCap = ForYouRules.pageCap(settings[PostsPrefs.zenMode], settings[PostsPrefs.zenPageCap]),
                    tracks = settings[PostsPrefs.rememberReadingPosition],
                )
            }.distinctUntilChanged().collect { next ->
                shape = next
                state.update { it.copy(tracks = next.tracks) }
                refresh(byReader = false)
            }
        }
    }

    /** Reads again once the connection is back, when For you said there was none. */
    private suspend fun retryWhenBackOnline() {
        graph.online.collect { online ->
            if (!online) return@collect
            refreshJob?.join()
            val now = state.value
            if (now.problem == Problem.Offline || now.notice == Problem.Offline) refresh(byReader = false)
        }
    }

    /** The newest page, in place of what is shown; [byReader] is a pull, which also goes back to the top. */
    fun refresh(byReader: Boolean = true) {
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
            val action = firstPageAction(step.shown, shape.tracks)
            state.update {
                it.copy(
                    threads = step.shown,
                    refreshing = false,
                    answered = true,
                    hasMore = page.next != null && stops.pause == null,
                    paused = stops.pause != null,
                    toTop = if (byReader) it.toTop + 1 else it.toTop,
                    jump = (action as? FirstPageAction.RestoreToBoundary)?.index ?: it.jump,
                )
            }
            if (shape.tracks) step.shown.firstOrNull()?.let { marks.noteNewest(it) }
            if (action is FirstPageAction.RecordPosition) marks.record(action.thread, System.currentTimeMillis())
        }
    }

    /** What a first page does with the read position: back to the divider once per visit, else a record while the reader is at the top. */
    private fun firstPageAction(threads: List<XThread>, tracks: Boolean): FirstPageAction? {
        if (!tracks) return null
        val action = FirstPageAction.of(threads, state.value.lastSeen, placed, sessionOffset = null, atTop = atTop)
        placed = true
        return action
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

    /** Whether the reader is at the top: back there, everything loaded counts as read. */
    fun atTop(top: Boolean) {
        if (top == atTop) return
        atTop = top
        val now = state.value
        if (!top || !now.tracks) return
        ReadPosition.newestRecordable(now.threads)?.let { viewModelScope.launch { marks.record(it, System.currentTimeMillis()) } }
    }

    fun jumped() = state.update { it.copy(jump = null) }

    fun dismissNotice() = state.update { it.copy(notice = null) }

    private suspend fun read(shape: Shape, cursor: String?): ForYouPage {
        val page = ForYou.page(shape.accounts, shape.off, cursor, reads, ForYouRules.PAGE_SIZE, shape.excluded)
        return page.copy(threads = ForYouRules.withoutReplies(page.threads))
    }
}
