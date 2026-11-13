package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPagingState
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsProfileTab
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberShareText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The profile's tabs: its threads, its replies, its media, what it reposted (as Threads' own tabs), and its posts the reader liked here (XTA's Saved). */
internal enum class ProfileTab(val label: PluginText, val paged: ThreadsProfilePaging.Lists?) {
    POSTS(Strings.posts, ThreadsProfilePaging.Lists.THREADS),
    REPLIES(Strings.profile_replies, ThreadsProfilePaging.Lists.REPLIES),
    MEDIA(Strings.media, ThreadsProfilePaging.Lists.BOTH),
    REPOSTS(Strings.profile_reposts, ThreadsProfilePaging.Lists.REPOSTS),
    SAVED(Strings.saved, null),
}

/**
 * One profile as it has been read, what it showed last time until then, its
 * card as soon as it is known (before its threads), its replies page once
 * its tab asked for it, and the pages after each first one as the reader
 * scrolls on.
 */
internal class ThreadsProfileModel(private val app: ThreadsApp, private val handle: String, scope: CoroutineScope) {
    val kept = MutableStateFlow<ThreadsKeptProfile?>(null)
    val card = MutableStateFlow<ThreadsProfile?>(null)
    val page = Fetch<ThreadsProfilePage>(scope) { force ->
        app.ready()
        app.reader.profile(handle, force) { card.value = it }.also { app.keepProfile(handle, it) }
    }
    val replies = Fetch<Page<ThreadsPost>>(scope) { app.reader.repliesPage(handle) }
    val morePosts = ThreadsPaging(scope, ThreadsPost::id) { cursor -> app.nextPage { app.direct.moreThreads(handle, cursor) } }
    val moreReplies = ThreadsPaging(scope, ThreadsPost::id) { cursor -> app.nextPage { app.direct.moreReplies(handle, cursor) } }
    val reposts = Fetch<Page<ThreadsPost>>(scope) { app.reader.repostsPage(handle) }
    val moreReposts = ThreadsPaging(scope, ThreadsPost::id) { cursor -> app.nextPage { app.direct.moreReposts(handle, cursor) } }

    init {
        scope.launch { kept.value = app.keptProfile(handle) }
    }
}

/**
 * One Threads profile and its public posts (threads_profile_screen.dart):
 * its header (the picture blurred as a banner, the face over it, following
 * here and its menu, name, handle with the private note, bio, links and
 * counts), then Posts, Replies, Media, Reposts and the posts of its the
 * reader liked here, each narrowed by the round toggles under the tabs and
 * by the bar's search through what is loaded, Media also as a grid of its
 * posts' first pictures ([ThreadsMediaGrid]). The replies page is asked for
 * only when its tab opens. A read that fails keeps what was shown; only a
 * profile that never had anything to show says why. More than XTA's: a
 * profile opened before shows at once what it showed then, a line at the
 * top while it is read again, and a read again that fails says so over what
 * is shown.
 */
@Composable
internal fun ThreadsProfileScreen(app: ThreadsApp, username: String) {
    val handle = remember(username) { (ThreadsLinks.normaliseHandle(username) ?: username).trim().lowercase() }
    val model = rememberKept("threads-profile-$handle") { scope -> ThreadsProfileModel(app, handle, scope) }
    LaunchedEffect(model) { model.page.ensure() }
    val state by model.page.state.collectAsState()
    val kept by model.kept.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val closeSearch = { searching = false; query = "" }
    val card by model.card.collectAsState()
    val url = "${ThreadsLinks.WEB}/@$handle"
    val page = ThreadsProfileView.shownPage(state.value, card, kept?.page)
    PluginScaffold(
        title = {
            if (searching) {
                ProfileSearchField(query, onChange = { query = it })
            } else {
                Text(
                    ThreadsProfileView.title(page?.profile, handle),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onBack = { if (searching) closeSearch() else app.host.back() },
        actions = {
            if (searching) {
                IconButton(onClick = closeSearch) { Icon(Icons.Filled.Close, contentDescription = pluginString(Strings.clear)) }
            } else {
                ProfileActions(app, url, onSearch = { searching = true })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val showingKept = state.value == null && page != null
            val problem = state.problem?.let { ThreadsFailures.profile(it, currentLanguage()) }
            when {
                page == null && problem != null -> RetryMessage(problem, onRetry = { model.page.load() })
                page?.profile == null -> LoadingRow()
                else -> ProfileBody(app, model, page, page.profile, refreshing = state.loading && !showingKept, postsPending = state.value == null && state.loading, query = query)
            }
            if (page?.profile != null) {
                ReloadStatus(state.loading && showingKept, problem, onRetry = { model.page.load(force = true) }, updating = state.value == null && kept != null)
            }
            NoticeHost(app.notices)
        }
    }
}

/** The bar's two quiet buttons: search what this profile has loaded, and share its link. */
@Composable
private fun ProfileActions(app: ThreadsApp, url: String, onSearch: () -> Unit) {
    val share = rememberShareText()
    val copied = Strings.link_copied.get(currentLanguage())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search_profile), tint = muted) }
    IconButton(onClick = { if (!share(url)) app.notices.tell(copied) }) { Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link), tint = muted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBody(
    app: ThreadsApp,
    model: ThreadsProfileModel,
    read: ThreadsProfilePage,
    profile: ThreadsProfile,
    refreshing: Boolean,
    postsPending: Boolean,
    query: String,
) {
    var tab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    var content by rememberSaveable { mutableStateOf(ThreadsFeedContent.ALL) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val replies by model.replies.state.collectAsState()
    val morePosts by model.morePosts.state.collectAsState()
    val moreReplies by model.moreReplies.state.collectAsState()
    val reposts by model.reposts.state.collectAsState()
    val moreReposts by model.moreReposts.state.collectAsState()
    LaunchedEffect(read) { model.morePosts.start(read.posts, read.next) }
    LaunchedEffect(replies.value) { replies.value?.let { model.moreReplies.start(it.items, it.next) } }
    LaunchedEffect(reposts.value) { reposts.value?.let { model.moreReposts.start(it.items, it.next) } }
    val shared = remember(reposts.value, moreReposts.items) { reposts.value?.items.orEmpty() + moreReposts.items }
    val page = remember(read, replies, morePosts.items, moreReplies.items) {
        read.copy(posts = read.posts + morePosts.items, replies = replies.value?.let { it.items + moreReplies.items })
    }
    val settings by app.prefs.collectAsState()
    val likedIds by app.likes.state.collectAsState()
    val all = remember(page, tab, settings, likedIds, shared) {
        when (tab) {
            ProfileTab.POSTS -> ThreadsProfileHeader.threads(page)
            ProfileTab.REPLIES -> page.tab(ThreadsProfileTab.REPLIES)
            ProfileTab.MEDIA -> page.tab(ThreadsProfileTab.MEDIA)
            ProfileTab.REPOSTS -> ThreadsProfileHeader.reposts(page, shared)
            ProfileTab.SAVED -> ThreadsLikes.liked(settings, likedIds).filter { it.handle == profile.username.lowercase() }
        }.distinctBy { it.id }
    }
    val mediaTab = tab == ProfileTab.MEDIA
    val chosen = ThreadsProfileView.chosen(content, mediaTab)
    val posts = remember(all, chosen, query) { ThreadsProfileView.shown(all, chosen, query) }
    var grid by rememberSaveable { mutableStateOf(false) }
    val gridRows = remember(posts) { ThreadsMediaGrid.rows(ThreadsMediaGrid.tiles(posts)) }
    val paging = ThreadsProfilePaging.of(tab.paged, morePosts, moreReplies, moreReposts)
    val pager = when {
        paging == null -> null
        paging === morePosts -> model.morePosts
        paging === moreReposts -> model.moreReposts
        else -> model.moreReplies
    }
    val waiting = all.isEmpty() && when (tab) {
        ProfileTab.REPLIES -> replies.loading
        ProfileTab.REPOSTS -> reposts.loading || reposts.value == null
        ProfileTab.SAVED -> false
        // The header came first; its threads are still on their way.
        else -> postsPending
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val select: (ProfileTab) -> Unit = { option ->
        // The tab already shown, tapped again, goes back to its top.
        if (option == tab) scope.launch { list.animateScrollToItem(0) }
        tab = option
        if (option == ProfileTab.REPLIES) model.replies.ensure()
        if (option == ProfileTab.REPOSTS) model.reposts.ensure()
    }
    LaunchedEffect(tab) {
        if (tab == ProfileTab.REPLIES) model.replies.ensure()
        if (tab == ProfileTab.REPOSTS) model.reposts.ensure()
    }
    val refresh = {
        model.page.load(force = true)
        if (tab == ProfileTab.REPLIES || tab == ProfileTab.MEDIA) model.replies.load(force = true)
        if (tab == ProfileTab.REPOSTS) model.reposts.load(force = true)
    }
    val firstPage = if (tab == ProfileTab.REPOSTS) reposts.value?.items?.size ?: 0 else read.posts.size
    ReadsMoreNear(list, pager, paging)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().swipeTabs(ProfileTab.entries, tab, select)) {
            item(key = "card") { ThreadsProfileCard(app, profile, onFilters = { sheet = true }) }
            item(key = "tabs") { ProfileTabStrip(ProfileTab.entries, tab, label = { pluginString(it.label) }, onSelect = select) }
            item(key = "filters") {
                ContentToggles(ThreadsProfileView.filters(mediaTab), chosen, grid = grid.takeIf { mediaTab }, onGrid = { grid = it }) { content = it }
            }
            when {
                waiting -> item(key = "waiting") { LoadingRow() }
                posts.isEmpty() -> item(key = "empty") { MessageText(pluginString(emptyLabel(tab, ThreadsProfileView.empty(all, chosen, query)))) }
                mediaTab && grid && gridRows.isNotEmpty() -> items(gridRows, key = { "grid:" + it.first().key }, contentType = { "grid" }) { MediaGridRow(app, it) }
                else -> items(posts, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
            }
            moreFoot("profile", paging) { pager?.retry() }
            if (ThreadsProfilePaging.endsOnWeb(paging, firstPage)) item(key = "end") { ProfileEnd(profile.username) }
        }
    }
    if (sheet) ProfileFiltersSheet(app, profile, ThreadsProfileView.filters(mediaTab), chosen, onChoose = { content = it }) { sheet = false }
}

private fun emptyLabel(tab: ProfileTab, why: ThreadsProfileEmpty): PluginText = when (why) {
    ThreadsProfileEmpty.NO_MATCH -> Strings.profile_search_empty
    ThreadsProfileEmpty.FILTERED -> Strings.empty_filter
    ThreadsProfileEmpty.NOTHING -> when (tab) {
        ProfileTab.REPLIES -> Strings.replies_empty
        ProfileTab.REPOSTS -> Strings.reposts_empty
        ProfileTab.SAVED -> Strings.liked_empty
        else -> Strings.no_posts
    }
}

/** Which of a profile's two paged lists a tab goes on with, and when its end leads to threads.com. */
internal object ThreadsProfilePaging {
    /** The lists a tab is drawn from: its threads, its replies page, both (Media), or its reposts page. */
    enum class Lists { THREADS, REPLIES, BOTH, REPOSTS }

    /** From this many threads on, a profile's list is likely cut where Meta's one guest page ends. */
    const val GUEST_PAGE_HINT = 10

    /** The paged list [lists] goes on with now: Media pages its threads, then its replies once the threads end. */
    fun <T> of(
        lists: Lists?,
        threads: ThreadsPagingState<T>,
        replies: ThreadsPagingState<T>,
        reposts: ThreadsPagingState<T>? = null,
    ): ThreadsPagingState<T>? = when (lists) {
        null -> null
        Lists.THREADS -> threads
        Lists.REPLIES -> replies
        Lists.REPOSTS -> reposts
        Lists.BOTH -> threads.takeIf { it.next != null || it.loading || it.problem != null } ?: replies
    }

    /**
     * Whether the list ends in "open in browser": the pages after the first
     * were refused or reached the memory's bound, or the first page named no
     * next page and is as long as Meta's one guest page.
     */
    fun endsOnWeb(paging: ThreadsPagingState<*>?, firstPosts: Int): Boolean = when {
        paging == null -> false
        paging.onWeb -> true
        else -> !paging.paged && firstPosts >= GUEST_PAGE_HINT
    }
}
