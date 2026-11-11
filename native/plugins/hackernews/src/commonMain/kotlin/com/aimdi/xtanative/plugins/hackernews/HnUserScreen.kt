package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnUser
import com.aimdi.xtanative.networks.hackernews.HnUserReader
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/** A Hacker News account (hn_user_screen.dart): its karma, Follow on this device, Add to group, its about text, and its latest stories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HnUserScreen(app: HnApp, id: String) {
    val reader = rememberKept("hn-user:$id") { scope -> HnUserReader(app.client, id, scope).also { it.refresh() } }
    val state by reader.state.collectAsState()
    val kept by app.kept.collectAsState()
    val page = state.value
    PluginScaffold(title = id, onBack = app.host::back) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && page != null, onRefresh = reader::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp), modifier = Modifier.fillMaxSize()) {
                val error = state.error
                when {
                    page == null && error != null -> item(key = "problem") { NetworkProblemView(problemOf(error), app.network, reader::refresh) }
                    page == null -> item(key = "loading") { FeedSkeleton() }
                    else -> {
                        item(key = "lead") { Lead(page.user, kept.isFollowing(page.user.id), { app.toggleFollow(page.user.id) }) { app.addToGroup(page.user.id) } }
                        if (page.stories.isEmpty()) item(key = "empty") { EmptyPane(HnIcons.PersonOutline, pluginString(Strings.user_empty)) }
                        items(page.stories, key = { "${it.id}" }) { story -> HnStoryRow(app, story) }
                    }
                }
            }
        }
    }
}

/** The account's name and karma with Follow on this device, Add to group under them, then its about text (hn_user_screen.dart's _UserLead). */
@Composable
private fun Lead(user: HnUser, following: Boolean, onFollow: () -> Unit, onGroup: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.id, style = MaterialTheme.typography.bodyLarge)
                Text(pluginPlural(Strings.user_karma, user.karma, user.karma), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onFollow) { Text(pluginString(if (following) Strings.unfollow else Strings.follow)) }
        }
        OutlinedButton(onClick = onGroup, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Icon(PostIcons.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(addToGroupText(), modifier = Modifier.padding(start = 8.dp))
        }
        if (user.about.isNotEmpty()) Text(user.about, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        Hairline()
    }
}
