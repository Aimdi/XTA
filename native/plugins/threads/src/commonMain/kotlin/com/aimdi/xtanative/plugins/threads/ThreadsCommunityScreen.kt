package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsCommunityFeed
import com.aimdi.xtanative.networks.threads.ThreadsGuardLimits
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsMore
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchFound
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.networks.threads.attempt
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginReadingColumn
import com.aimdi.xtanative.ui.plugins.PluginReadingWidth
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/** Where a community's posts are read: its own page where Meta named one, else the posts filed under its name. */
internal object ThreadsCommunityReads {
    /** The address its page opens in a browser, and shares. */
    fun webUrl(topic: ThreadsTopic): String = topic.url ?: ThreadsLinks.searchUrl(topic.name, tag = true).toString()

    /**
     * The community's posts: its own page where Meta named one and it shows
     * a guest something, else the posts filed under its name (which CI read
     * as a guest: twenty posts); the second is whether they are its own page's.
     */
    suspend fun read(
        topic: ThreadsTopic,
        ownPage: suspend (String) -> ThreadsSearchFound,
        topicSearch: suspend () -> ThreadsSearchFound,
    ): Pair<ThreadsSearchFound, Boolean> {
        val url = topic.url ?: return topicSearch() to false
        val found = attempt { ownPage(url) }?.takeIf { it.posts.isNotEmpty() }
        return if (found != null) found to true else topicSearch() to false
    }

    /**
     * The community's posts as [read] says, the reader's account first where
     * [account] is given (the reader opened it, a session path is known and
     * there is a session, [ThreadsCommunityFeed.usesAccount]): its feed as
     * the signed-in reader is shown it. That read failing, resting or empty
     * falls back to the guest's reading, quietly.
     */
    suspend fun readWithAccount(
        topic: ThreadsTopic,
        account: (suspend () -> Page<ThreadsPost>)?,
        ownPage: suspend (String) -> ThreadsSearchFound,
        topicSearch: suspend () -> ThreadsSearchFound,
    ): Pair<ThreadsSearchFound, ThreadsCommunitySource> {
        val signedIn = account?.let { attempt { it() } }?.takeIf { it.items.isNotEmpty() }
        if (signedIn != null) return ThreadsSearchFound(signedIn.items, next = signedIn.next) to ThreadsCommunitySource.ACCOUNT
        val (found, own) = read(topic, ownPage, topicSearch)
        return found to if (own) ThreadsCommunitySource.OWN_PAGE else ThreadsCommunitySource.TOPIC
    }

    /** [topic] with the marks [found] carried for it, its own link kept. */
    fun refined(topic: ThreadsTopic, found: ThreadsSearchFound?): ThreadsTopic {
        val seen = found?.topics?.firstOrNull { it.key == topic.key } ?: return topic
        return topic.copy(emoji = topic.emoji ?: seen.emoji, iconUrl = topic.iconUrl ?: seen.iconUrl, url = topic.url ?: seen.url)
    }
}

/** Whose reading a community's posts came from. */
internal enum class ThreadsCommunitySource {
    /** The reader's account: the community as the signed-in reader is shown it. */
    ACCOUNT,

    /** The community's own page, as a guest. */
    OWN_PAGE,

    /** The posts filed under its name, as a guest. */
    TOPIC,
}

/**
 * A community's first page and the pages after it, as the reader scrolls on:
 * with the reader's account when [ThreadsCommunityFeed.usesAccount] says so
 * (only here, when the community is opened; Home's followed communities are
 * read as a guest), its pages then capped as every session list's are.
 */
internal class ThreadsCommunityModel(app: ThreadsApp, topic: ThreadsTopic, scope: kotlinx.coroutines.CoroutineScope) {
    /** Where the posts shown came from. */
    @Volatile
    var source = ThreadsCommunitySource.TOPIC
        private set

    val page = Fetch<ThreadsSearchFound>(scope) { force ->
        app.ready()
        val account = ThreadsCommunityFeed.usesAccount(app.session(), app.direct.guard.refresh().state)
            .takeIf { it }?.let { suspend { app.direct.communityFeed(topic.name, fresh = force) } }
        ThreadsCommunityReads.readWithAccount(topic, account, { app.direct.communityPage(it) }, { app.direct.searchFound(topic.name, tag = true) })
            .also { source = it.second }.first
    }

    val more = ThreadsPaging(scope, ThreadsPost::id) { cursor ->
        val url = topic.url
        app.nextPage { if (source == ThreadsCommunitySource.OWN_PAGE && url != null) app.direct.moreCommunity(url, cursor) else app.direct.moreFound(topic.name, tag = true, recent = false, cursor = cursor) }
    }

    /** The pages after an account's first: four on their own, then each on Load more. */
    val accountMore = ThreadsPaging(scope, ThreadsPost::id, autoPages = ThreadsGuardLimits.AUTO_PAGES) { cursor ->
        app.nextPage { app.direct.communityFeed(topic.name, cursor).let { ThreadsMore(it.items, it.next) } }
    }

    /** The pager for where the posts shown came from. */
    val pager: ThreadsPaging<ThreadsPost> get() = if (source == ThreadsCommunitySource.ACCOUNT) accountMore else more
}

/**
 * A community's page, as threads.com opens one from a post: its mark and
 * name, Follow on this device (its posts then come to Home), and its posts,
 * page after page where Meta names a next one. With no address of its own
 * known, the posts filed under its name stand in. A read that fails says
 * why with Retry; an empty one leads to threads.com.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsCommunityScreen(app: ThreadsApp, start: ThreadsTopic) {
    val model = rememberKept("threads-community-${start.key}-${start.url}") { scope -> ThreadsCommunityModel(app, start, scope) }
    LaunchedEffect(model) { model.page.ensure() }
    val state by model.page.state.collectAsState()
    val found = state.value
    val usedAccount = found != null && model.source == ThreadsCommunitySource.ACCOUNT
    val pager = remember(found) { model.pager }
    val paged by pager.state.collectAsState()
    val topic = remember(start, found) { ThreadsCommunityReads.refined(start, found) }
    LaunchedEffect(found) { found?.let { pager.start(it.posts, it.next) } }
    val list = rememberLazyListState()
    ReadsMoreNear(list, pager, paged)
    val web = ThreadsCommunityReads.webUrl(topic)
    PluginScaffold(title = topic.name, onBack = app.host::back, column = PluginReadingWidth, actions = { CommunityActions(app, web) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(isRefreshing = state.loading && found != null, onRefresh = { model.page.load(force = true) }, modifier = Modifier.fillMaxSize()) {
                PluginReadingColumn(PaddingValues(), list) { column -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = column) {
                    item(key = "header") { ThreadsTopicHeader(app, topic) }
                    if (usedAccount) item(key = "account") { AccountInUse(app) }
                    val posts = found?.posts.orEmpty() + paged.items
                    when {
                        found == null && state.problem != null ->
                            item(key = "problem") { RetryMessage(ThreadsFailures.search(state.problem, currentLanguage()), onRetry = { model.page.load() }) }
                        found == null -> item(key = "loading") { PluginFeedSkeleton() }
                        posts.isEmpty() -> item(key = "empty") { NothingHere(web) }
                        else -> items(posts.distinctBy { it.id }, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
                    }
                    sessionMoreFoot("community", paged, pager::loadMore.takeIf { usedAccount }, pager::retry)
                } }
            }
            NoticeHost(app.notices)
        }
    }
}

@Composable
private fun CommunityActions(app: ThreadsApp, url: String) {
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val copied = Strings.link_copied.get(currentLanguage())
    IconTip(pluginString(Strings.share_link)) { IconButton(onClick = { if (!share(url)) app.notices.tell(copied) }) { Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link)) } }
    IconTip(pluginString(Strings.open_in_browser)) { IconButton(onClick = { outside(url) }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser)) } }
}

/** Nothing a guest is shown here: said so, with the way to threads.com. */
@Composable
private fun NothingHere(url: String) {
    val outside = rememberOpenOutside()
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MessageText(pluginString(Strings.no_posts))
        TextButton(onClick = { outside(url) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(PostIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.open_in_browser), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
