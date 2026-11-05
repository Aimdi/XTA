package com.aimdi.xtanative.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.MergedTimeline
import com.aimdi.xtanative.core.feed.TimelineSource
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.ProfileRefresh
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.follows.RememberedFollows
import com.aimdi.xtanative.store.offline.KeptProfile
import com.aimdi.xtanative.store.saved.ArchiveFilter
import com.aimdi.xtanative.store.saved.ProfileFilters
import com.aimdi.xtanative.store.saved.XtaTweetJson
import com.aimdi.xtanative.store.saved.profileArchive
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.ProfileTab as StoredTab
import com.aimdi.xtanative.ui.media.MediaGridItem
import com.aimdi.xtanative.ui.media.MediaGridItems
import com.aimdi.xtanative.ui.post.cached
import com.aimdi.xtanative.ui.post.page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TabUi(
    val threads: List<XThread> = emptyList(),
    /** The media grid's tiles, for the Media tab. */
    val tiles: List<MediaGridItem> = emptyList(),
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
    /** What X said of a profile it will not show: the page, as a retry cannot change it. */
    val failure: ProfileFailure? = null,
    val tab: ProfileTab = ProfileTab.POSTS,
    val tabs: Map<ProfileTab, TabUi> = emptyMap(),
    /** The pinned post, above the others in Posts. */
    val pinned: List<XThread> = emptyList(),
    val postsFilter: PostsFilter = PostsFilter.ALL,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val archiveFilter: ArchiveFilter = ArchiveFilter.ALL,
    /** The reader's private note on the account. */
    val note: String = "",
    val filters: ProfileFilters = ProfileFilters(),
    /** A pull is reading the profile again from X. */
    val refreshing: Boolean = false,
) {
    fun of(tab: ProfileTab): TabUi = tabs[tab] ?: TabUi()

    val repostsHidden: Boolean get() = user?.let { filters.repostsHidden(it.id) } == true
    val repliesHidden: Boolean get() = user?.let { filters.repliesHidden(it.id) } == true
}

/** How the account is followed here: in XTA Native alone, never on X; its feed cap; who of the follows follows it. */
data class FollowUi(
    val followed: Boolean = false,
    val inFeed: Boolean = true,
    /** XTA's "Max posts per feed load" for it, when followed. */
    val maxPosts: Int? = null,
    val followedBy: FollowedBy = FollowedBy(),
) {
    companion object {
        fun of(session: Session, userId: String?, screenName: String, remembered: Map<String, RememberedFollows> = emptyMap()): FollowUi {
            val account = userId?.let(session::followed) ?: session.following.firstOrNull { it.screenName.equals(screenName, ignoreCase = true) }
            return FollowUi(
                followed = account != null,
                inFeed = account?.inFeed ?: true,
                maxPosts = account?.let { QuietAccount.choiceOf(session.postCaps[it.id]) },
                followedBy = userId?.let { FollowedBy.of(it, remembered, session.following) } ?: FollowedBy(),
            )
        }
    }
}

/**
 * A profile (profile.dart): its name and picture at once from the feed, then
 * the rest from X; four tabs read as they are opened, each with its filter,
 * the device's Saved among them; the reader's note and feed filters for it.
 * Posts holds what it posted and what it reposted, in time order, as XTA
 * shows them; X keeps the two apart.
 *
 * The account is read by [userId] when the caller knows it, so a renamed
 * account still opens, else by [screenName]; the copy kept on the device
 * shows with its tabs at once while X is asked again. [openingTab] is the
 * tab a link named, before the reader's default.
 */
class ProfileViewModel(
    private val graph: AppGraph,
    private val screenName: String,
    private val userId: String? = null,
    private val openingTab: ProfileTab? = null,
) : ViewModel() {
    private val state = MutableStateFlow(ProfileUi(user = graph.posts.user(screenName)?.takeIf { userId == null || it.id == userId }))
    val ui: StateFlow<ProfileUi> = state.asStateFlow()

    val follow: StateFlow<FollowUi> =
        combine(graph.sessions.state.filterNotNull(), state.map { it.user?.id ?: userId }.distinctUntilChanged()) { session, id ->
            FollowUi.of(session, id, screenName, graph.follows.remembered())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // From the session already read, so Follow never flashes before Following.
            FollowUi.of(graph.sessions.state.value ?: Session(), state.value.user?.id ?: userId, screenName),
        )

    private var posts: MergedTimeline? = null
    private val cursors = HashMap<ProfileTab, String?>()
    private val seenTiles = HashSet<String>()
    private val jobs = HashMap<ProfileTab, Job>()

    @Volatile
    private var pinned: List<XThread> = emptyList()

    /** The ids X names as pinned on the profile: a kept first page no longer says which they are. */
    @Volatile
    private var pinnedIds: Set<String> = emptySet()

    /** A pull reads X again, past the pages kept on the device. */
    @Volatile
    private var bypassCache = false

    init {
        viewModelScope.launch {
            val opening = openingTab ?: ProfileTab.opening(runCatching { graph.settings.get(GeneralPrefs.defaultProfileTab) }.getOrNull() ?: StoredTab.POSTS)
            state.update { it.copy(tab = opening) }
            load()
        }
        viewModelScope.launch { graph.profileFilters.filters().let { filters -> state.update { it.copy(filters = filters) } } }
    }

    /** The profile and its tab read again from X, as a pull asks. */
    fun refresh() {
        bypassCache = true
        state.update { it.copy(refreshing = true) }
        load()
    }

    /**
     * The profile from X. Opened for the first time, the copy kept on the
     * device shows first with its tabs; when X then fails, that copy stays
     * with the problem above its tabs.
     */
    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null, failure = null) }
            val kept = if (state.value.complete) null else kept()
            if (kept != null) show(kept.user, kept.pinned, reloadTabs = true)
            try {
                val (user, pins) = if (userId != null) graph.client.profileById(userId) else graph.client.profile(screenName)
                graph.keep { graph.keptProfiles.remember(user, pins) }
                refreshFollowed(user)
                show(user, pins, reloadTabs = kept?.user?.id != user.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val failure = ProfileFailure.of(error)
                bypassCache = false
                state.update {
                    when {
                        it.complete -> it.copy(loading = false, refreshing = false, problem = Problem.of(error))
                        else -> it.copy(loading = false, refreshing = false, failure = failure, problem = if (failure == null) Problem.of(error) else null)
                    }
                }
            }
        }
    }

    private suspend fun kept(): KeptProfile? = runCatching {
        if (userId != null) graph.keptProfiles.byId(userId) else graph.keptProfiles.byName(screenName)
    }.getOrNull()

    /** [user] on the page; with [reloadTabs], its tabs read afresh, else the ones shown stay. */
    private suspend fun show(user: XUser, pins: List<String>, reloadTabs: Boolean) {
        pinnedIds = pins.toSet()
        graph.posts.remember(user)
        if (!reloadTabs) {
            state.update { it.copy(user = user, complete = true, loading = false) }
            return
        }
        posts = null
        state.update { it.copy(user = user, complete = true, loading = false, tabs = emptyMap()) }
        readNote(user)
        loadTab(state.value.tab)
    }

    /** A followed account's handle, name and picture as X now gives them (subscriptions.refreshProfiles). */
    private suspend fun refreshFollowed(user: XUser) {
        val session = graph.sessions.state.value ?: return
        if (ProfileRefresh.refreshed(session.following, listOf(user)).isNotEmpty()) graph.sessions.update { it.withProfilesFrom(listOf(user)) }
    }

    fun select(tab: ProfileTab) {
        state.update { it.copy(tab = tab) }
        val now = state.value.of(tab)
        if (!now.loaded && !now.loading) loadTab(tab)
    }

    fun selectPosts(filter: PostsFilter) = refilter(ProfileTab.POSTS) { it.copy(postsFilter = filter) }

    fun selectMedia(filter: MediaFilter) = refilter(ProfileTab.MEDIA) { it.copy(mediaFilter = filter) }

    fun selectArchive(filter: ArchiveFilter) = refilter(ProfileTab.SAVED) { it.copy(archiveFilter = filter) }

    private fun refilter(tab: ProfileTab, change: (ProfileUi) -> ProfileUi) {
        state.update { change(it).copy(tabs = it.tabs - tab) }
        if (tab == ProfileTab.POSTS) posts = null
        loadTab(tab)
    }

    /** Posts and reposts, merged; the pinned post is kept apart for the top. */
    private fun postsOf(userId: String) = MergedTimeline(
        listOf(
            TimelineSource("posts") { cursor -> ownPosts(userId, cursor) },
            TimelineSource("reposts") { cursor -> reposts(userId, cursor) },
        ),
    )

    private suspend fun ownPosts(userId: String, cursor: String?): XTimelinePage {
        val page = firstOrNext(ProfileCache.Timeline.POSTS, userId, cursor) { graph.client.userPosts(userId, it) }
        val pins = page.pinnedIds + pinnedIds
        if (cursor == null) pinned = page.threads.filter { it.id in pins }
        return page.copy(threads = page.threads.filterNot { it.id in pins })
    }

    private suspend fun reposts(userId: String, cursor: String?): XTimelinePage =
        firstOrNext(ProfileCache.Timeline.REPOSTS, userId, cursor) { graph.client.userReposts(userId, it) }

    private suspend fun replies(userId: String, cursor: String?): XTimelinePage =
        firstOrNext(ProfileCache.Timeline.REPLIES, userId, cursor) { graph.client.userReplies(userId, it) }

    /**
     * A page of one of the profile's timelines: the first from the device
     * when it was read in the last fifteen minutes, else from X and kept, and
     * at any age when X gives nothing; the others from X.
     */
    private suspend fun firstOrNext(timeline: ProfileCache.Timeline, userId: String, cursor: String?, read: suspend (String?) -> XTimelinePage): XTimelinePage {
        if (cursor != null) return read(cursor)
        val cache = graph.timelineCache
        val key = ProfileCache.key(userId, timeline)
        if (!bypassCache) cache.read(key, ProfileCache.MAX_AGE_MS)?.let { return it.page() }
        return try {
            read(null).also { cache.write(key, it.cached()) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            cache.readStale(key)?.page() ?: throw error
        }
    }

    private fun loadTab(tab: ProfileTab) {
        val user = state.value.user?.takeIf { state.value.complete } ?: return
        jobs[tab]?.cancel()
        cursors.remove(tab)
        jobs[tab] = viewModelScope.launch {
            setTab(tab) { it.copy(loading = true, problem = null) }
            try {
                when (tab) {
                    ProfileTab.POSTS -> loadPosts(user)
                    ProfileTab.REPLIES -> {
                        val (threads, more) = paged(tab, replies(user.id, null))
                        graph.posts.remember(threads)
                        setTab(tab) { it.copy(threads = threads, hasMore = more, loading = false, loaded = true) }
                    }
                    ProfileTab.MEDIA -> {
                        seenTiles.clear()
                        val (tiles, more) = mediaPage(user, null)
                        setTab(tab) { it.copy(tiles = tiles, hasMore = more, loading = false, loaded = true) }
                    }
                    ProfileTab.SAVED -> {
                        val threads = archive(user)
                        setTab(tab) { it.copy(threads = threads, loading = false, loaded = true) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Failed) {
                setTab(tab) { it.copy(loading = false, problem = error.problem) }
            } catch (error: Throwable) {
                setTab(tab) { it.copy(loading = false, problem = Problem.of(error)) }
            } finally {
                // A pull reads past the kept pages of the tab it was made on; the next tab opened is kept again.
                bypassCache = false
                state.update { if (it.refreshing) it.copy(refreshing = false) else it }
            }
        }
    }

    /** The Posts tab under its filter: all of it merged, the own posts alone, or the reposts alone. */
    private suspend fun loadPosts(user: XUser) {
        val filter = state.value.postsFilter
        val (threads, more) = when (filter) {
            PostsFilter.ALL -> postsOf(user.id).also { posts = it }.first().let { page ->
                if (page.threads.isEmpty() && pinned.isEmpty()) Problem.of(page.failures)?.let { throw Failed(it) }
                page.threads to page.hasMore
            }
            PostsFilter.POSTS -> paged(ProfileTab.POSTS, ownPosts(user.id, null)).let { (threads, more) -> threads.filter(filter::accepts) to more }
            PostsFilter.REPOSTS -> paged(ProfileTab.POSTS, reposts(user.id, null)).let { (threads, more) -> threads.filter(filter::accepts) to more }
        }
        graph.posts.remember(threads + pinned)
        state.update { it.copy(pinned = if (filter == PostsFilter.REPOSTS) emptyList() else pinned) }
        setTab(ProfileTab.POSTS) { it.copy(threads = threads, hasMore = more, loading = false, loaded = true) }
    }

    fun loadMore() {
        val tab = state.value.tab
        val now = state.value.of(tab)
        val user = state.value.user ?: return
        if (!now.loaded || now.loading || now.loadingMore || !now.hasMore) return
        jobs[tab] = viewModelScope.launch {
            setTab(tab) { it.copy(loadingMore = true, moreProblem = null) }
            try {
                if (tab == ProfileTab.MEDIA) {
                    val (tiles, more) = mediaPage(user, cursors[tab])
                    setTab(tab) { it.copy(tiles = it.tiles + tiles, hasMore = more, loadingMore = false) }
                    return@launch
                }
                val filter = state.value.postsFilter
                val (threads, more) = when {
                    tab == ProfileTab.POSTS && filter == PostsFilter.ALL -> posts!!.more().let { it.threads to it.hasMore }
                    tab == ProfileTab.POSTS && filter == PostsFilter.POSTS -> paged(tab, ownPosts(user.id, cursors[tab]))
                    tab == ProfileTab.POSTS -> paged(tab, graph.client.userReposts(user.id, cursors[tab]))
                    else -> paged(tab, graph.client.userReplies(user.id, cursors[tab]))
                }
                graph.posts.remember(threads)
                setTab(tab) {
                    val known = it.threads.mapTo(HashSet()) { thread -> thread.id }
                    val fresh = threads.filter { thread -> thread.id !in known && (tab != ProfileTab.POSTS || filter.accepts(thread)) }
                    it.copy(threads = it.threads + fresh, hasMore = more && fresh.isNotEmpty(), loadingMore = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setTab(tab) { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }

    /** A page of a profile timeline and whether there is more. */
    private fun paged(tab: ProfileTab, page: XTimelinePage): Pair<List<XThread>, Boolean> {
        val before = cursors[tab]
        cursors[tab] = page.bottomCursor
        return page.threads to (page.bottomCursor != null && page.bottomCursor != before && page.threads.isNotEmpty())
    }

    /**
     * The grid's next tiles. Media posts are sparse, so a few more pages are
     * read before the grid says there are none; broadcasts are found among
     * the profile's posts, the rest in its media timeline.
     */
    private suspend fun mediaPage(user: XUser, cursor: String?): Pair<List<MediaGridItem>, Boolean> {
        val filter = state.value.mediaFilter
        var next = cursor
        var tiles = emptyList<MediaGridItem>()
        var reads = 0
        do {
            val page = if (filter.readsPosts) graph.client.userPosts(user.id, next) else graph.client.userMedia(user.id, next)
            graph.posts.remember(page.threads)
            tiles = MediaGridItems.unseen(MediaGridItems.of(page.threads).filter(filter::accepts), seenTiles)
            next = page.bottomCursor?.takeIf { it != next }
            reads++
        } while (tiles.isEmpty() && next != null && reads <= filter.lookahead)
        cursors[ProfileTab.MEDIA] = next
        return tiles to (next != null)
    }

    /** The account's posts saved and liked on this device, under the Saved tab's filter. */
    private suspend fun archive(user: XUser): List<XThread> {
        val items = profileArchive(graph.saved.library().posts, graph.likes.likes(), user.id, state.value.archiveFilter)
        val threads = items.mapNotNull { item -> item.content?.let { XtaTweetJson.decode(J(it)) }?.let { XThread(it.id, listOf(it)) } }
        graph.posts.remember(threads)
        return threads
    }

    private suspend fun readNote(user: XUser) {
        val note = runCatching { graph.profileNotes.of(user.id)?.note }.getOrNull().orEmpty()
        state.update { it.copy(note = note) }
    }

    fun saveNote(note: String) {
        val user = state.value.user ?: return
        viewModelScope.launch {
            graph.profileNotes.set(user.id, note)
            state.update { it.copy(note = note.trim()) }
        }
    }

    fun hideReposts(hidden: Boolean) = changeFilters { user -> graph.profileFilters.setRepostsHidden(user.id, user.screenName, hidden) }

    fun hideReplies(hidden: Boolean) = changeFilters { user -> graph.profileFilters.setRepliesHidden(user.id, user.screenName, hidden) }

    private fun changeFilters(change: suspend (XUser) -> Unit) {
        val user = state.value.user ?: return
        viewModelScope.launch {
            change(user)
            val filters = graph.profileFilters.filters()
            state.update { it.copy(filters = filters) }
        }
    }

    /** XTA's "Max posts per feed load": off, or a cap on the account's posts in each group feed load. */
    fun capPosts(max: Int?) = changeFollow { user, session -> session.withPostCap(user.id, max) }

    private fun changeFollow(change: (XUser, Session) -> Session) {
        val user = state.value.user ?: return
        viewModelScope.launch { graph.sessions.update { change(user, it) } }
    }

    private fun setTab(tab: ProfileTab, change: (TabUi) -> TabUi) =
        state.update { it.copy(tabs = it.tabs + (tab to change(it.of(tab)))) }

    private class Failed(val problem: Problem) : Exception()
}
