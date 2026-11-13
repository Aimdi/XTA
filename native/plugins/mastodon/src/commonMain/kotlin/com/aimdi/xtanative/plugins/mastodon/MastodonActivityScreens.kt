package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.ui.plugins.NetworkPersonRow
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/** Who boosted a post, as XTA's activity list shows them: by the instance's order, or by their followers. */
@Composable
internal fun MastodonRepostersScreen(app: MastodonApp, target: MastodonTarget) {
    val seed = remember(target) { app.seed(target) }
    val pages = rememberKept("mastodon-reposts:" + target.url) { scope ->
        PagedItems<MastodonProfile>(scope, key = { it.acct }) { cursor -> app.client.rebloggedBy(app.candidates(target.acct), seed, cursor) }
    }
    var order by rememberSaveable { mutableStateOf(app.reposterOrder) }
    ActivityScreen(app, Strings.reposted_by, target.url, pages, { it.acct }, MastodonOrders.reposterOrders, order, { order = it; app.reposterOrder = it }, MastodonOrders::reposters) { person ->
        NetworkPersonRow(person.displayName.ifEmpty { person.acct }, person.acct, person.avatarUrl, { app.openProfile(person.acct) })
    }
}

/** The posts quoting a post, newest, oldest or most liked first. */
@Composable
internal fun MastodonQuotesScreen(app: MastodonApp, target: MastodonTarget) {
    val seed = remember(target) { app.seed(target) }
    val pages = rememberKept("mastodon-quotes:" + target.url) { scope ->
        PagedItems<MastodonPost>(scope, key = { it.url }) { cursor -> app.client.quotes(app.candidates(target.acct), seed, cursor) }
    }
    var order by rememberSaveable { mutableStateOf(app.quoteOrder) }
    ActivityScreen(app, Strings.quotes, target.url, pages, { it.url }, MastodonOrders.quoteOrders, order, { order = it; app.quoteOrder = it }, MastodonOrders::quotes) { post ->
        MastodonPostRow(app, post)
    }
}

/**
 * A list about one post (plugin_activity.dart), read page by page: its
 * order when it has one to choose, the browser in its bar, and when a
 * server keeps the list to signed-in readers, XTA's word for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> ActivityScreen(
    app: MastodonApp,
    title: PluginText,
    url: String,
    pages: PagedItems<T>,
    key: (T) -> String,
    orders: List<MastodonOrder>,
    order: MastodonOrder,
    onOrder: (MastodonOrder) -> Unit,
    arrange: (List<T>, MastodonOrder) -> List<T>,
    row: @Composable (T) -> Unit,
) {
    val paged by pages.state.collectAsState()
    val shown = remember(paged, order) { paged.copy(items = arrange(paged.items, order)) }
    val list = rememberLazyListState()
    val openOutside = rememberOpenOutside()
    val empty = pluginString(Strings.empty_list)
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PluginScaffold(
        title = pluginString(title),
        onBack = app.host::back,
        actions = { IconButton(onClick = { openOutside(url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) } },
    ) { padding ->
        PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp), modifier = Modifier.fillMaxSize()) {
                if (paged.items.size > 1) item(key = "order") { OrderChoice(orders, order, MastodonOrders::label, onOrder) }
                pagedRows(shown, pages, key, empty, { pluginString(problemText(it, activity = true)) }, row)
            }
        }
    }
}
