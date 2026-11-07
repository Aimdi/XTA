package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonFollow
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.NetworkPersonRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.ReadingControlsPinItem
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/** Which of XTA's two Mastodon screens this is: the compact reader on Home, or the client opened on its own. */
internal enum class MastodonSurface(val key: String) { HOME("home"), CLIENT("client") }

private fun MastodonTimeline.label(): PluginText = when (this) {
    MastodonTimeline.EXPLORE -> Strings.tab_explore
    MastodonTimeline.LOCAL -> Strings.tab_local
    MastodonTimeline.FEDERATED -> Strings.tab_federated
    MastodonTimeline.FOLLOWING -> Strings.tab_following
}

private fun MastodonTimeline.icon(): ImageVector = when (this) {
    MastodonTimeline.EXPLORE -> MastodonIcons.Explore
    MastodonTimeline.LOCAL -> MastodonIcons.Home
    MastodonTimeline.FEDERATED -> MastodonIcons.Public
    MastodonTimeline.FOLLOWING -> PostIcons.People
}

/**
 * XTA's Mastodon reader (mastodon_screen.dart): Explore, Local, Federated and
 * Following, with search, Saved and the settings beside them; Following shows
 * the posts of the accounts followed here or the accounts themselves, and
 * adds one. It opens on the timeline left last, each where it was left.
 */
@Composable
internal fun MastodonHome(app: MastodonApp, frame: PluginFrame, surface: MastodonSurface) {
    val left = remember { app.places.current() }
    var timeline by rememberSaveable { mutableStateOf(MastodonTimeline.entries.getOrElse(left.tab) { MastodonTimeline.EXPLORE }) }
    var people by rememberSaveable { mutableStateOf(left.people) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val panes = rememberSaveableStateHolder()
    val select: (MastodonTimeline) -> Unit = { chosen ->
        timeline = chosen
        app.places.select(chosen.ordinal, people)
    }
    Column(Modifier.fillMaxSize()) {
        ReadingControlsBar {
            Column {
                SectionBar(app, timeline, select)
                if (timeline == MastodonTimeline.FOLLOWING) {
                    FollowingControls(people, onSelect = {
                        people = it
                        app.places.select(timeline.ordinal, it)
                    }, onAdd = { adding = true })
                }
            }
        }
        Hairline()
        Box(Modifier.weight(1f).swipeTabs(MastodonTimeline.entries, timeline, select)) {
            val key = if (timeline == MastodonTimeline.FOLLOWING && people) "people" else timeline.name
            panes.SaveableStateProvider(key) {
                if (key == "people") PeoplePane(app, frame) { adding = true } else Pane(app, timeline, "${surface.key}:${timeline.ordinal}", frame)
            }
        }
    }
    if (adding) AddAccountDialog(app) { adding = false }
}

@Composable
private fun Pane(app: MastodonApp, timeline: MastodonTimeline, slot: String, frame: PluginFrame) {
    val follows by app.follows.state.collectAsState()
    TimelinePane(app, timeline, slot, frame) {
        when {
            timeline != MastodonTimeline.FOLLOWING -> EmptyTimeline(pluginString(Strings.empty_public))
            follows.isNullOrEmpty() -> EmptyTimeline(pluginString(Strings.empty_following), pluginString(Strings.discover)) { app.host.openSearch(null) }
            else -> EmptyTimeline(pluginString(Strings.no_posts), pluginString(Strings.retry)) { app.following.refresh(force = true) }
        }
    }
}

/** The four timelines as chips, then search, and Saved and Settings in the menu. */
@Composable
private fun SectionBar(app: MastodonApp, selected: MastodonTimeline, onSelect: (MastodonTimeline) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MastodonTimeline.entries.forEach { timeline ->
                FilterChip(
                    selected = timeline == selected,
                    onClick = { onSelect(timeline) },
                    label = { Text(pluginString(timeline.label()), maxLines = 1) },
                    leadingIcon = { Icon(timeline.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        IconButton(onClick = { app.host.openSearch(null) }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
        MoreMenu(app)
    }
}

@Composable
private fun MoreMenu(app: MastodonApp) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.saved)) }, onClick = {
                open = false
                app.openSaved()
            })
            DropdownMenuItem(text = { Text(pluginString(Strings.settings)) }, onClick = {
                open = false
                app.host.openSettings()
            })
            ReadingControlsPinItem()
        }
    }
}

/** Following's own row: its posts or its accounts, and Add account. */
@Composable
private fun FollowingControls(people: Boolean, onSelect: (Boolean) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !people, onClick = { onSelect(false) }, label = { Text(pluginString(Strings.posts)) }, leadingIcon = { Icon(MastodonIcons.Stream, null, Modifier.size(18.dp)) })
        FilterChip(selected = people, onClick = { onSelect(true) }, label = { Text(pluginString(Strings.accounts)) }, leadingIcon = { Icon(PostIcons.People, null, Modifier.size(18.dp)) })
        Box(Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(MastodonIcons.PersonAdd, pluginString(Strings.add_account)) }
    }
}

/** The accounts followed here, by name, each opening its profile and each to put in the app's groups. */
@Composable
private fun PeoplePane(app: MastodonApp, frame: PluginFrame, onAdd: () -> Unit) {
    val follows by app.follows.state.collectAsState()
    LaunchedEffect(Unit) { app.follows.load() }
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        if (follows?.isEmpty() == true) item(key = "none") { EmptyTimeline(pluginString(Strings.empty_following), pluginString(Strings.add_account), onAdd) }
        items(follows.orEmpty(), key = MastodonFollow::acct) { follow ->
            NetworkPersonRow(follow.name, follow.acct, follow.avatarUrl, { app.openProfile(follow.acct) }) { AddToGroupButton(app.host, follow.acct) }
        }
    }
}
