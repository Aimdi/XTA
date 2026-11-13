package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchFound
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.networks.threads.attempt
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
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

    /** [topic] with the marks [found] carried for it, its own link kept. */
    fun refined(topic: ThreadsTopic, found: ThreadsSearchFound?): ThreadsTopic {
        val seen = found?.topics?.firstOrNull { it.key == topic.key } ?: return topic
        return topic.copy(emoji = topic.emoji ?: seen.emoji, iconUrl = topic.iconUrl ?: seen.iconUrl, url = topic.url ?: seen.url)
    }
}

/** A community's first page and the pages after it, as the reader scrolls on. */
internal class ThreadsCommunityModel(app: ThreadsApp, topic: ThreadsTopic, scope: kotlinx.coroutines.CoroutineScope) {
    /** Whether the posts shown came from the community's own page; the topic search's otherwise. */
    @Volatile
    private var own = false

    val page = Fetch<ThreadsSearchFound>(scope) {
        app.ready()
        ThreadsCommunityReads.read(topic, { app.direct.communityPage(it) }, { app.direct.searchFound(topic.name, tag = true) }).also { own = it.second }.first
    }

    val more = ThreadsPaging(scope, ThreadsPost::id) { cursor ->
        val url = topic.url
        app.nextPage { if (own && url != null) app.direct.moreCommunity(url, cursor) else app.direct.moreFound(topic.name, tag = true, recent = false, cursor = cursor) }
    }
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
    val paged by model.more.state.collectAsState()
    val found = state.value
    val topic = remember(start, found) { ThreadsCommunityReads.refined(start, found) }
    LaunchedEffect(found) { found?.let { model.more.start(it.posts, it.next) } }
    val list = rememberLazyListState()
    ReadsMoreNear(list, model.more, paged)
    val web = ThreadsCommunityReads.webUrl(topic)
    PluginScaffold(title = topic.name, onBack = app.host::back, actions = { CommunityActions(app, web) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(isRefreshing = state.loading && found != null, onRefresh = { model.page.load(force = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    item(key = "header") { ThreadsTopicHeader(app, topic) }
                    val posts = found?.posts.orEmpty() + paged.items
                    when {
                        found == null && state.problem != null ->
                            item(key = "problem") { RetryMessage(ThreadsFailures.search(state.problem, currentLanguage()), onRetry = { model.page.load() }) }
                        found == null -> item(key = "loading") { PluginFeedSkeleton() }
                        posts.isEmpty() -> item(key = "empty") { NothingHere(web) }
                        else -> items(posts.distinctBy { it.id }, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
                    }
                    moreFoot("community", paged, model.more::retry)
                }
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
    IconButton(onClick = { if (!share(url)) app.notices.tell(copied) }) { Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link)) }
    IconButton(onClick = { outside(url) }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser)) }
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
