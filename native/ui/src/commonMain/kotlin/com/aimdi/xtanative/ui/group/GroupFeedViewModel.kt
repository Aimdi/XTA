package com.aimdi.xtanative.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedPage
import com.aimdi.xtanative.core.feed.FeedReads
import com.aimdi.xtanative.core.feed.FeedProgress
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FirstPageAction
import com.aimdi.xtanative.core.feed.GroupFeed
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.feed.HeldRefresh
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.feed.feedReads
import com.aimdi.xtanative.core.session.CombinedGroups
import com.aimdi.xtanative.core.session.ProfileRefresh
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.FeedCache
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import com.aimdi.xtanative.ui.feed.FeedSources
import com.aimdi.xtanative.ui.feed.HiddenPosts
import com.aimdi.xtanative.ui.feed.MixedSources
import com.aimdi.xtanative.ui.feed.StoredSnapshots
import com.aimdi.xtanative.ui.feed.PluginFeeds
import com.aimdi.xtanative.ui.feed.PluginPostsState
import com.aimdi.xtanative.ui.feed.SourceAsk
import com.aimdi.xtanative.ui.feed.feedConcurrency
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.subscriptions.GroupActions
import com.aimdi.xtanative.ui.subscriptions.GroupBoardModel
import com.aimdi.xtanative.ui.subscriptions.SubscriptionList
import com.aimdi.xtanative.ui.subscriptions.SubscriptionRow
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where to put the reader, once: [token] tells two jumps to the same place apart. */
data class FeedJump(val threadIndex: Int, val offset: Int, val token: Int)

/** How a group feed's reading stands. */
data class FeedRun(
    val shown: FeedShown = FeedShown(),
    /** The posts are the ones kept from the last time, until X answers. */
    val fromCache: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** Nothing to show, and why. */
    val problem: Problem? = null,
    /** The first page failed; what is shown is from before. */
    val stale: Problem? = null,
    /** When the posts shown were read (epoch milliseconds): what the stale notice says of them; null when not known. */
    val savedAt: Long? = null,
    val moreProblem: Problem? = null,
    val answered: Boolean = false,
    val tracks: Boolean = false,
    val catchUp: Boolean = false,
    /** The last read post as the feed opened: the divider stays where it was for the whole visit. */
    val lastSeen: ReadPosition? = null,
    val unrelatedDismissed: Boolean = false,
    val jump: FeedJump? = null,
) {
    val divider: Int? get() = GroupFeedPaging.dividerIndex(shown.threads, lastSeen, tracks, catchUp)
}

/** A group feed at one moment: the group, its settings and what its reading has shown. */
data class GroupFeedUi(
    val loaded: Boolean = false,
    val group: FollowGroup? = null,
    /** A group's feed whose group is gone: XTA says it is unable to load the group. */
    val missing: Boolean = false,
    /** Every member the feed reads, nested groups' and those read alongside included. */
    val memberCount: Int = 0,
    val settings: FeedSettings = FeedSettings(),
    val includeReplies: Boolean = true,
    val includeReposts: Boolean = true,
    val prefs: FeedPrefs = FeedPrefs(),
    val inDeck: Boolean = false,
    val alsoRead: Set<String> = emptySet(),
    /** The groups in the board's order, for the switcher. */
    val groups: List<FollowGroup> = emptyList(),
    /** The feed's accounts, for their post caps. */
    val accounts: List<FollowedAccount> = emptyList(),
    val postCaps: Map<String, Int> = emptyMap(),
    /** Everything followed, for the edit sheet's member list. */
    val people: List<SubscriptionRow> = emptyList(),
    val session: Session = Session(),
    val run: FeedRun = FeedRun(),
    val progress: FeedProgress = FeedProgress(),
) {
    val activeFilters: Int get() = GroupFeedRules.activeFilters(settings)

    val contentChip: ContentFilter? get() = GroupFeedRules.contentChip(settings)

    /** The unrelated-posts pill: X mixed in posts from outside the group, and the reader has not turned the warning off. */
    val unrelatedNotice: Boolean get() = run.shown.unrelated && !prefs.hideUnrelatedWarning && !run.unrelatedDismissed
}

/** Groups read together for as long as the reader looks at them: one combination for whichever feed is open, gone with the app. */
object Combinations {
    val state = MutableStateFlow(CombinedGroups())
}

/**
 * A group's feed (_feed.dart, group_screen.dart), or for a null [groupId]
 * the Following feed, which XTA runs as its group `-1`: the session's plan
 * for it, read with core's [GroupFeed], shown with its progress, the sources
 * that failed to retry, folded posts, the unrelated-posts pill, the read
 * position and catch-up mode. A change of what it reads (members, replies
 * and reposts, order, rules) refreshes it, held while the reader is
 * scrolled down; the other settings apply from the next load. Its members of other networks are read by
 * their plugins ([feeds]) and mixed in by time, with the feed's reply and
 * repost settings; the Following feed mixes in, instead, what is followed in
 * every plugin whose "show in the home timeline" switch is on.
 *
 * As XTA's, a feed opened again in this run of the app shows what it had
 * where the reader left it, without asking X ([sessions]); after a cold start
 * its first page reads what was written since down to the posts it kept, and
 * shows them above those, so the reader is brought back to their last read
 * post. A first load shows the chunks as they answer. A feed out of sight
 * ([visible]) reads nothing. X is read through [reads].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupFeedViewModel(
    private val graph: AppGraph,
    private val groupId: String?,
    private val feeds: PluginFeeds = PluginFeeds.None,
    private val reads: FeedReads = graph.client.feedReads(),
    private val sessions: FeedSessionCache = FeedSessions.feeds,
) : ViewModel() {
    /** The feed's key: its group's id, or the Following feed's. */
    private val key = groupId ?: FeedKeys.FOLLOWING
    private val cache: FeedCache = graph.feedCache(groupId)
    private val marks = ReadMarks(graph.feedCache(ReadMarks.key(key)))
    private val run = MutableStateFlow(FeedRun())
    private val current = MutableStateFlow<GroupFeed?>(null)
    val groupActions = GroupActions(graph, viewModelScope)
    val settingsActions = GroupSettingsActions(graph, key, viewModelScope)

    private var plan: GroupFeedPlan? = null
    private var prefs = FeedPrefs()

    /** What the feed's reading is for ([GroupFeedPlan.readKey]): only a change of it reads the feed again. */
    private var readKey: String? = null

    /** The feed as the last launch left it, read on from by the first page until one is shown. */
    private var stored: SavedFeed? = null

    /** The feed for a changed plan, read from its first page once the refresh it waits for runs. */
    private var nextFeed: GroupFeed? = null
    private var refreshJob: Job? = null
    private var moreJob: Job? = null
    private var pending: Job? = null
    private val held = HeldRefresh()
    private var atTop = true
    private var evaluated = false
    private val place: KeptPlace? = sessions.place(key)
    private var jumps = 0
    private var resets = 0

    /** The reader has scrolled the list: a first load's preview keeps its order from then on. */
    private var scrolled = false

    /** What is shown is the first load's preview, built as the chunks answer. */
    private var progressive = false

    /** Whether the feed can be seen, and what its hiding stopped, to read once it is seen again. */
    private var visible = true
    private var refreshWhenVisible = false
    private var moreWhenVisible = false

    private data class Inputs(val plan: GroupFeedPlan, val prefs: FeedPrefs)

    val ui: StateFlow<GroupFeedUi> = combine(
        graph.sessions.state.filterNotNull(),
        graph.settings.settings,
        Combinations.state,
        run,
        combine(current.flatMapLatest { it?.progress ?: flowOf(FeedProgress()) }, feeds.ids, ::Pair),
    ) { session, settings, combined, run, (progress, plugins) -> build(session, settings, combined, run, progress, plugins) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupFeedUi())

    private val mixed = MixedSources(
        viewModelScope,
        feeds,
        combine(feeds.ids, graph.sessions.state.filterNotNull(), Combinations.state, graph.settings.settings) { ids, session, combined, settings ->
            asksOf(ids, session, alsoReadOf(combined), settings)
        },
        ::problemOf,
        StoredSnapshots(graph.feedSnapshots),
    )

    /** The group's members of other networks' posts, as their plugins read them. */
    val plugin: StateFlow<PluginPostsState> = mixed.posts.state

    init {
        groupId?.let { id -> Combinations.state.update { it.released(id) } }
        viewModelScope.launch {
            run.update { it.copy(lastSeen = marks.position()) }
            combine(
                graph.sessions.state.filterNotNull(),
                graph.settings.settings,
                Combinations.state,
                HiddenPosts.of(graph.profileFilters),
            ) { session, settings, combined, hidden ->
                val prefs = FeedPrefs.of(settings)
                Inputs(GroupFeedRules.plan(session, prefs, groupId, alsoReadOf(combined), hidden), prefs)
            }.distinctUntilChanged().collect(::read)
        }
    }

    /** The groups read alongside this one; none beside the Following feed, which combines with nothing. */
    private fun alsoReadOf(combined: CombinedGroups): Set<String> = groupId?.let { combined.ids - it }.orEmpty()

    /**
     * What the feed asks of the plugins with a feed ([ids]), with its reply
     * and repost settings: a group its members of theirs, the Following feed
     * the accounts followed in each whose home-timeline switch is on.
     */
    private fun asksOf(ids: Set<String>, session: Session, alsoRead: Set<String>, settings: Settings): Map<String, SourceAsk> {
        val plan = session.feedPlan(groupId, alsoRead)
        val kinds = FeedKinds(plan.includeReplies, plan.includeReposts)
        val members = groupId?.let { PluginMembers.of(session, it, alsoRead, ids) }.orEmpty()
        return FeedSources.asks(ids, members, home = groupId == null, inHome = { PluginSettings.inHomeFeed(settings, it) }, kinds = kinds)
    }

    private fun build(session: Session, settings: Settings, combined: CombinedGroups, run: FeedRun, progress: FeedProgress, plugins: Set<String>): GroupFeedUi {
        val feedSettings = session.settingsOf(key)
        val alsoRead = alsoReadOf(combined)
        val memberIds = (if (groupId == null) session.feedMembers else session.membersOf(groupId, alsoRead)).mapTo(HashSet()) { it.id }
        val pluginMembers = groupId?.let { PluginMembers.of(session, it, alsoRead, plugins).values.sumOf { members -> members.size } } ?: 0
        val rows = SubscriptionList.ordered(
            SubscriptionList.rows(session),
            settings[SubscriptionPrefs.order],
            settings[SubscriptionPrefs.ascending],
            settings[SubscriptionPrefs.customOrder],
        )
        return GroupFeedUi(
            loaded = true,
            group = session.groups.firstOrNull { it.id == groupId },
            missing = groupId != null && session.groups.none { it.id == groupId },
            memberCount = memberIds.size + pluginMembers,
            settings = feedSettings,
            includeReplies = feedSettings.includeReplies ?: session.includeReplies,
            includeReposts = feedSettings.includeReposts ?: session.includeReposts,
            prefs = FeedPrefs.of(settings),
            inDeck = groupId != null && groupId in session.deck,
            alsoRead = alsoRead,
            groups = GroupBoardModel.ordered(session.groups, settings[SubscriptionPrefs.groupsOrder], settings[SubscriptionPrefs.groupsAscending]),
            accounts = session.following.filter { it.id in memberIds },
            postCaps = session.postCaps,
            people = rows,
            session = session,
            run = run,
            progress = progress,
        )
    }

    /**
     * A plan read for the first time, or changed. One that reads as before
     * ([GroupFeedPlan.readKey]: the same searches, order and rules, XTA's
     * chunk hashes and rules key) is taken for the next loads without reading
     * anything again; otherwise a new feed, refreshed at once, or once the
     * reader is back at the top.
     */
    private suspend fun read(inputs: Inputs) {
        val first = plan == null
        plan = inputs.plan
        prefs = inputs.prefs
        run.update {
            it.copy(tracks = GroupFeedRules.tracks(inputs.plan, inputs.prefs), catchUp = GroupFeedRules.catchUp(inputs.plan))
        }
        val readsAs = inputs.plan.readKey
        if (!first && readsAs == readKey) {
            current.value?.replan(inputs.plan)
            nextFeed?.replan(inputs.plan)
            return
        }
        readKey = readsAs
        if (first) {
            val feed = GroupFeed(inputs.plan, reads, concurrency())
            current.value = feed
            val kept = sessions.feed(key)?.takeIf { it.readKey == readsAs }
            if (kept != null && feed.restore(kept.state, (kept.shown.threads + kept.shown.stops.held).map { it.id })) return reuse(kept)
            showCached(inputs.plan)
            refresh(byReader = false)
            return
        }
        nextFeed = GroupFeed(inputs.plan, reads, concurrency())
        // A burst of changes (several members added) makes one refresh.
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(CHANGE_DEBOUNCE_MILLIS)
            if (held.request(atTop)) refresh(byReader = false)
        }
    }

    private fun concurrency(): Int = graph.feedConcurrency()

    /** The feed as this run of the app left it: its pages, its reading and its place, with nothing asked of X. */
    private fun reuse(kept: KeptFeed) {
        evaluated = true
        val index = place?.let { p -> kept.shown.threads.indexOfFirst { it.id == p.postId } } ?: -1
        run.update {
            it.copy(
                shown = kept.shown,
                savedAt = kept.savedAt,
                answered = true,
                jump = place?.takeIf { index >= 0 }?.let { p -> FeedJump(index, p.offset, ++jumps) },
            )
        }
    }

    /** What the feed showed the last time, at once, shaped by the rules as they are now; its first page reads on from it. */
    private suspend fun showCached(plan: GroupFeedPlan) {
        val kept = cache.read() ?: return
        stored = kept
        val saved = kept.threads.takeIf { it.isNotEmpty() } ?: return
        graph.posts.remember(saved)
        val preview = GroupFeedPaging.preview(saved, plan.shaping, Instant.now())
        val index = place?.let { p -> preview.threads.indexOfFirst { it.id == p.postId } } ?: -1
        run.update {
            if (it.shown.threads.isNotEmpty()) it else it.copy(
                shown = preview,
                fromCache = true,
                savedAt = kept.savedAt,
                jump = place?.takeIf { index >= 0 }?.let { p -> FeedJump(index, p.offset, ++jumps) },
            )
        }
    }

    private fun isSeen(): ((XThread) -> Boolean)? {
        val now = run.value
        val seen = now.lastSeen ?: return null
        return if (now.catchUp) seen::isSeen else null
    }

    /**
     * The newest posts. [byReader] is a pull or the refresh button: the
     * reader goes to the top, and the feed is read from its top alone, as
     * XTA's pull drops the chunks it kept. Otherwise a feed kept from the last
     * launch reads what was written since down to its kept posts
     * ([GroupFeed.reopen]), and a first load shows the chunks as they answer.
     */
    fun refresh(byReader: Boolean = true) {
        if (byReader) mixed.refresh(force = true)
        if (!visible) {
            refreshWhenVisible = true
            return
        }
        nextFeed?.let {
            current.value = it
            nextFeed = null
        }
        val feed = current.value ?: return
        val saved = stored.takeUnless { byReader }
        val previewing = !run.value.answered && !run.value.catchUp
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            resets++
            run.update { it.copy(refreshing = true, problem = null, moreProblem = null) }
            val page = try {
                saved?.let { feed.reopen(it) } ?: feed.first(if (previewing) ::arrived else null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failed(Problem.of(error))
                return@launch
            }
            val failure = Problem.of(page.failures)
            if (page.threads.isEmpty() && failure != null) {
                // An empty answer from a failing X is not an empty feed: what was shown stays.
                failed(failure)
                return@launch
            }
            shownFirst(page, byReader)
            stored = null
        }
    }

    /** What a first load's chunks brought so far, shown until its first page is: never in catch-up mode, which stops at the last read post. */
    private fun arrived(page: FeedPage) {
        val now = run.value
        if (page.threads.isEmpty() || now.answered || now.catchUp) return
        val previous = if (progressive) now.shown.threads else emptyList()
        progressive = true
        run.update { it.copy(shown = GroupFeedPaging.arrived(previous, page, scrolled), fromCache = false) }
    }

    private fun failed(problem: Problem) = run.update {
        it.copy(refreshing = false, problem = problem.takeIf { _ -> it.shown.threads.isEmpty() }, stale = problem)
    }

    private suspend fun shownFirst(arrived: FeedPage, byReader: Boolean) {
        val now = run.value
        val page = if (progressive && scrolled && !now.catchUp) arrived.copy(threads = GroupFeedPaging.inPreviewOrder(now.shown.threads, arrived.threads)) else arrived
        progressive = false
        val shown = GroupFeedPaging.first(page, prefs.pageCap, isSeen())
        graph.posts.remember(page.threads)
        refreshProfiles(page.threads)
        val action = firstPageAction(shown.threads, page)
        run.update {
            it.copy(
                shown = shown,
                fromCache = false,
                savedAt = System.currentTimeMillis(),
                refreshing = false,
                problem = null,
                // Sources that failed while others answered are the retry pill's, not a stale feed.
                stale = null,
                answered = true,
                unrelatedDismissed = false,
                jump = when {
                    action is FirstPageAction.RestoreToBoundary -> FeedJump(action.index, 0, ++jumps)
                    byReader -> FeedJump(0, 0, ++jumps)
                    else -> it.jump
                },
            )
        }
        if (action is FirstPageAction.RecordPosition) marks.record(action.thread, System.currentTimeMillis())
        page.threads.firstOrNull()?.let { marks.noteNewest(it) }
        persist()
    }

    /**
     * What a first page does with the read position, decided once per visit:
     * back to the divider, or a record of where the reader is; never a record
     * past posts that were not loaded.
     */
    private fun firstPageAction(threads: List<XThread>, page: FeedPage): FirstPageAction? {
        val now = run.value
        if (!now.tracks || now.catchUp) return null
        val offset = place?.let { if (it.atTop) 0.0 else 1.0 }
        val reaches = FirstPageAction.reaches(threads, now.lastSeen, page.hasMore, page.gap)
        val action = FirstPageAction.of(threads, now.lastSeen, evaluated, offset, atTop, reaches)
        evaluated = true
        return action
    }

    /** Followed accounts as the posts X just sent show them: a new handle, name or picture. */
    private suspend fun refreshProfiles(threads: List<XThread>) {
        val users = ProfileRefresh.postAuthors(threads)
        val session = graph.sessions.state.value ?: return
        if (ProfileRefresh.refreshed(session.following, users).isNotEmpty()) graph.sessions.update { it.withProfilesFrom(users) }
    }

    fun loadMore() {
        val feed = current.value ?: return
        val now = run.value
        if (!now.shown.canLoadMore || now.loadingMore || now.refreshing || now.fromCache) return
        if (!visible) {
            moreWhenVisible = true
            return
        }
        val reset = resets
        moreJob = viewModelScope.launch {
            run.update { it.copy(loadingMore = true, moreProblem = null) }
            val page = try {
                feed.more()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                run.update { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
                return@launch
            }
            if (reset != resets) {
                run.update { it.copy(loadingMore = false) }
                return@launch
            }
            graph.posts.remember(page.threads)
            refreshProfiles(page.threads)
            run.update {
                it.copy(
                    shown = GroupFeedPaging.more(it.shown, page, isSeen()),
                    loadingMore = false,
                    moreProblem = if (page.threads.isEmpty()) Problem.of(page.failures) else null,
                )
            }
            persist()
        }
    }

    /** Reads again only the sources that failed. */
    fun retryFailed() {
        val feed = current.value ?: return
        val popular = plan?.popular == true
        viewModelScope.launch {
            val page = try {
                feed.retryFailed()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                run.update { it.copy(stale = Problem.of(error)) }
                return@launch
            }
            graph.posts.remember(page.threads)
            val isSeen = isSeen()
            run.update { it.copy(shown = GroupFeedPaging.retried(it.shown, page, popular, isSeen), stale = null) }
            persist()
        }
    }

    /** Reads on past catch-up's end or zen mode's last page. */
    fun continuePastPause() = run.update { it.copy(shown = GroupFeedPaging.resumed(it.shown)) }

    /** The reader reached the end of what was new: the only moment catch-up mode moves the read position. */
    fun caughtUp() {
        val newest = ReadPosition.newestRecordable(run.value.shown.threads) ?: return
        viewModelScope.launch { marks.record(newest, System.currentTimeMillis()) }
    }

    /**
     * Whether the reader is at the top: a held refresh runs then, and outside
     * catch-up mode everything loaded counts as read.
     */
    fun atTop(top: Boolean) {
        mixed.posts.readingAway(!top)
        if (!top) scrolled = true
        if (top == atTop) return
        atTop = top
        if (!top) return
        if (held.returnedToTop()) refresh(byReader = false)
        val now = run.value
        if (now.tracks && !now.catchUp && !now.fromCache) {
            ReadPosition.newestRecordable(now.shown.threads)?.let { viewModelScope.launch { marks.record(it, System.currentTimeMillis()) } }
        }
    }

    /**
     * The feed (what was held back under a stop included) and where each of
     * its chunks' reading stands, for the next launch to read on from: the
     * reading only while every post shown is kept, so no hole opens below them.
     */
    private fun persist() {
        val feed = current.value ?: return
        val shown = run.value.shown
        val threads = shown.threads + shown.stops.held
        val readKey = feed.plan.readKey
        viewModelScope.launch {
            val state = if (threads.size <= FeedCache.KEPT) feed.state() else null
            cache.write(SavedFeed(threads.take(FeedCache.KEPT), state, savedAt = System.currentTimeMillis(), key = readKey))
        }
    }

    /**
     * The post at the top of the screen, kept as the feed closes, with its
     * pages and reading, for the next visit in this run of the app; a feed
     * closed halfway through a load keeps only its place.
     */
    fun keepPlace(threadIndex: Int, offset: Int) {
        val now = run.value
        now.shown.threads.getOrNull(threadIndex)?.let { thread ->
            sessions.keepPlace(key, KeptPlace(thread.id, offset, atTop = threadIndex == 0 && offset == 0))
        }
        val feed = current.value
        val readKey = readKey
        if (feed != null && readKey != null && nextFeed == null && now.answered && !now.refreshing && !now.loadingMore && feed.plan.readKey == readKey) {
            // Kept to its end even as the screen goes: the place is kept as it closes.
            graph.keep { sessions.keepFeed(key, KeptFeed(readKey, feed.state(), now.shown, now.savedAt)) }
        } else {
            sessions.dropFeed(key)
        }
    }

    /**
     * Whether the feed can be seen (XTA's ReadVisibility): a deck column or a
     * page of the bar out of sight, or an app in the background, reads
     * nothing. What its hiding stopped, a first page or the next one, is read
     * once it is seen again; a refresh asked for meanwhile waits for then.
     */
    fun visible(seen: Boolean) {
        if (seen == visible) return
        visible = seen
        if (!seen) {
            val now = run.value
            if (now.refreshing) refreshWhenVisible = true
            if (now.loadingMore) moreWhenVisible = true
            refreshJob?.cancel()
            moreJob?.cancel()
            run.update { it.copy(refreshing = false, loadingMore = false) }
            return
        }
        when {
            refreshWhenVisible -> {
                refreshWhenVisible = false
                moreWhenVisible = false
                refresh(byReader = false)
            }
            moreWhenVisible -> {
                moreWhenVisible = false
                loadMore()
            }
        }
    }

    fun jumped() = run.update { it.copy(jump = null) }

    fun dismissStale() = run.update { it.copy(stale = null) }

    fun dismissUnrelated() = run.update { it.copy(unrelatedDismissed = true) }

    /** Shows the members' posts of other networks that waited while the reader was down the list. */
    fun revealPlugins() = mixed.posts.reveal()

    /** Reads plugin [source] again, as its failure's retry does. */
    fun retryPlugin(source: String) = mixed.posts.retry(source)


    // ---- groups read alongside ----

    fun toggleAlongside(otherId: String) {
        if (groupId != null && otherId != groupId) Combinations.state.update { it.toggled(otherId) }
    }

    fun clearAlongside() = Combinations.state.update { it.cleared() }

    private companion object {
        /** A burst of member or filter changes makes one refresh. */
        const val CHANGE_DEBOUNCE_MILLIS = 600L
    }
}
