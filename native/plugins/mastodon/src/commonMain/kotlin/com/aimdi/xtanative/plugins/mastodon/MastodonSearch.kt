package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonSearchReader
import com.aimdi.xtanative.networks.mastodon.MastodonSearchTab
import com.aimdi.xtanative.networks.mastodon.MastodonTag
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkPersonRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginTabs
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * XTA's Mastodon search (mastodon_search_sheet.dart): names, #tags,
 * `user@instance` and post addresses, over the reader's instances and the
 * built-in ones; it opens on the first of Accounts, Posts and Hashtags that
 * found something.
 */
internal class MastodonSearch(private val app: MastodonApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = SearchResults(app, query, frame)
}

@Composable
private fun SearchResults(app: MastodonApp, query: String, frame: PluginFrame) {
    val reader = rememberKept("mastodon-search:$query") { scope -> MastodonSearchReader(app.client, app::discovery, query, scope) }
    val state by reader.state.collectAsState()
    val error = state.error
    when {
        state.loading -> LoadingRow()
        error != null -> RetryMessage(pluginString(problemText(error)), reader::search)
        else -> Column(Modifier.fillMaxSize()) {
            val labels = listOf(Strings.accounts, Strings.posts, Strings.hashtags).map { pluginString(it) }
            PluginTabs(labels, state.tab.ordinal, { reader.select(MastodonSearchTab.entries[it]) })
            Box(Modifier.weight(1f)) { SearchPane(app, state, frame) }
        }
    }
}

@Composable
private fun SearchPane(app: MastodonApp, state: MastodonSearchReader.State, frame: PluginFrame) {
    val results = state.results
    when (state.tab) {
        MastodonSearchTab.ACCOUNTS -> if (results.accounts.isEmpty()) MessageText(pluginString(Strings.no_results)) else LazyColumn(contentPadding = frame.padding) {
            items(results.accounts, key = { it.acct }) { profile ->
                NetworkPersonRow(profile.displayName.ifEmpty { profile.acct }, profile.acct, profile.avatarUrl, { app.openProfile(profile.acct) })
            }
        }
        MastodonSearchTab.POSTS -> if (results.posts.isEmpty()) MessageText(pluginString(Strings.no_posts)) else LazyColumn(contentPadding = frame.padding) {
            items(results.posts, key = { it.url }) { post -> MastodonPostRow(app, post) }
        }
        MastodonSearchTab.HASHTAGS -> if (results.tags.isEmpty()) MessageText(pluginString(Strings.no_hashtags)) else TagChips(app, results.tags)
    }
}

/** Hashtags as chips, each opening its timeline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(app: MastodonApp, tags: List<MastodonTag>) {
    FlowRow(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> AssistChip(onClick = { app.openTag(tag.name) }, label = { Text("#" + tag.name) }) }
    }
}
