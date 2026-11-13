package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsMore
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CoroutineScope

/** The quotes of the post Meta keeps under [mediaId] as far as they have been read: the first page, and those after it as the reader scrolls. */
internal class ThreadsQuotesModel(private val app: ThreadsApp, private val mediaId: String, scope: CoroutineScope) {
    val first = Fetch<Page<ThreadsPost>>(scope) { app.ready(); app.nextPage { app.direct.quotes(mediaId) } }
    val more = ThreadsPaging(scope, { it.shortcode ?: it.id }) { cursor ->
        app.nextPage { app.direct.quotes(mediaId, cursor).let { ThreadsMore(it.items, it.next) } }
    }
}

/**
 * The posts that quote a post, as Threads lists them under its Quotes: each
 * as a feed card with the quoted post boxed in it, page after page, pull to
 * read again. Threads shows the list only to a signed-in reader, so without
 * a session it says where to sign in. Read only: nothing is quoted from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsQuotesScreen(app: ThreadsApp, mediaId: String) {
    val settings by app.prefs.collectAsState()
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    PluginScaffold(title = pluginString(Strings.quotes), onBack = app.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!signedIn) {
                SignedOut(app)
            } else {
                QuotesList(app, mediaId)
            }
            NoticeHost(app.notices)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotesList(app: ThreadsApp, mediaId: String) {
    val model = rememberKept("threads-quotes-$mediaId") { scope -> ThreadsQuotesModel(app, mediaId, scope) }
    LaunchedEffect(model) { model.first.ensure() }
    val first by model.first.state.collectAsState()
    val more by model.more.state.collectAsState()
    LaunchedEffect(first.value) { first.value?.let { model.more.start(it.items, it.next) } }
    val posts = remember(first.value, more.items) { ThreadsPostLists.shown(first.value?.items.orEmpty(), more.items) }
    val body = ThreadsPeopleView.body(signedIn = true, read = first.value != null, problem = first.problem != null, people = posts.size, typed = "")
    val state = rememberLazyListState()
    ReadsMoreNear(state, model.more, more)
    PullToRefreshBox(isRefreshing = first.loading && first.value != null, onRefresh = { model.first.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            when (body) {
                ThreadsPeopleView.Body.PROBLEM -> item(key = "problem") {
                    RetryMessage(ThreadsFailures.postList(first.problem, currentLanguage()), onRetry = { model.first.load(force = true) })
                }
                ThreadsPeopleView.Body.LOADING -> item(key = "loading") { LoadingRow() }
                ThreadsPeopleView.Body.PEOPLE -> items(posts, key = { "quote:" + (it.shortcode ?: it.id) }, contentType = { "post" }) { post -> ThreadsPostCard(app, post) }
                else -> item(key = "empty") { MessageText(pluginString(Strings.quotes_empty)) }
            }
            if (body == ThreadsPeopleView.Body.PEOPLE) moreFoot("quotes", more) { model.more.retry() }
        }
    }
}
