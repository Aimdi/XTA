package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackFeedFilter
import com.aimdi.xtanative.networks.substack.SubstackHomeFeed
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackLibrarySection
import com.aimdi.xtanative.networks.substack.SubstackLoadedOptions
import com.aimdi.xtanative.networks.substack.SubstackNotes
import com.aimdi.xtanative.networks.substack.SubstackNotesFeed
import com.aimdi.xtanative.networks.substack.postReads
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CoroutineScope

/** The home tab's panes, in XTA's order. */
internal enum class HomePane(val label: PluginText, val icon: ImageVector) {
    HOME(Strings.home, Icons.Outlined.Home),
    INBOX(Strings.inbox, SubstackIcons.Inbox),
    NOTES(Strings.tab_notes, SubstackIcons.Notes),
    LIBRARY(Strings.library, Icons.Outlined.Person),
}

/**
 * What the reader chose on the home tab for as long as it is open, as XTA's
 * SubstackHomeControlsStore: each feed's search and order, Home's content
 * filter, and the Library's section and search. None of it asks Substack.
 */
internal class HomeControls {
    var home by mutableStateOf(SubstackLoadedOptions())
    var inbox by mutableStateOf(SubstackLoadedOptions())
    var filter by mutableStateOf(SubstackFeedFilter.ALL)
    var librarySection by mutableStateOf(SubstackLibrarySection.FOLLOWING)
    var libraryQuery by mutableStateOf("")

    fun options(pane: HomePane): SubstackLoadedOptions = if (pane == HomePane.INBOX) inbox else home

    fun configure(pane: HomePane, options: SubstackLoadedOptions) {
        if (pane == HomePane.INBOX) inbox = options else home = options
    }

    fun reset(pane: HomePane) {
        configure(pane, SubstackLoadedOptions())
        if (pane == HomePane.HOME) filter = SubstackFeedFilter.ALL
    }
}

/** The home tab's state, kept while Home is on the back stack: the feed, the notes, and the controls. */
internal class HomeState(app: SubstackApp, scope: CoroutineScope) {
    val feed = SubstackHomeFeed(app.client.postReads(), scope, recall = app::recallFeed, remember = app::rememberFeed)
    val notes = SubstackNotesFeed(SubstackNotes.of(app.client) { app.publications.value.orEmpty() }, scope, { app.publications.value.orEmpty() })
    val controls = HomeControls()
}

/**
 * Substack's home tab, as XTA's SubstackScreen: Home (every followed
 * publication's posts), Inbox (the unread ones), Notes (the public Notes
 * mix) and Library (follows, saved and liked posts), with Discover, Add
 * publication and Mark all as read in its menu. Following, likes and saves
 * stay on this device.
 */
@Composable
internal fun SubstackHome(app: SubstackApp, frame: PluginFrame) {
    val state = rememberKept("substack-home") { scope -> HomeState(app, scope) }
    var pane by rememberSaveable { mutableIntStateOf(0) }
    val selected = HomePane.entries[pane]
    val publications by app.publications.collectAsState()
    // A change of follows reads what it changed; a pane coming into view refreshes what is stale.
    LaunchedEffect(publications) { publications?.let(state.feed::follow) }
    LaunchedEffect(selected) {
        when (selected) {
            HomePane.HOME, HomePane.INBOX -> state.feed.refresh(force = false)
            HomePane.NOTES -> state.notes.refresh(force = false)
            HomePane.LIBRARY -> Unit
        }
    }
    RefreshesOn(frame.refresh) {
        if (selected == HomePane.NOTES) state.notes.refresh() else state.feed.refresh()
    }
    Column(Modifier.fillMaxSize()) {
        HomeHeader(app, state, selected) { pane = it.ordinal }
        Hairline()
        Box(Modifier.weight(1f).swipeTabs(HomePane.entries, selected) { pane = it.ordinal }) {
            when (selected) {
                HomePane.HOME -> HomeFeedPane(app, state, frame)
                HomePane.INBOX -> InboxPane(app, state, frame)
                HomePane.NOTES -> NotesPane(app, state.notes, frame)
                HomePane.LIBRARY -> LibraryPane(app, state.controls, frame)
            }
        }
    }
}

@Composable
private fun HomeHeader(app: SubstackApp, state: HomeState, selected: HomePane, onSelect: (HomePane) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomePane.entries.forEach { pane ->
                FilterChip(
                    selected = pane == selected,
                    onClick = { onSelect(pane) },
                    label = { Text(pluginString(pane.label)) },
                    leadingIcon = { Icon(pane.icon, null, Modifier.size(18.dp)) },
                )
            }
        }
        IconButton(onClick = app::openDiscover) { Icon(Icons.Default.Search, pluginString(Strings.search)) }
        HomeMenu(app, state, selected)
    }
}

@Composable
private fun HomeMenu(app: SubstackApp, state: HomeState, selected: HomePane) {
    var open by rememberSaveable { mutableStateOf(false) }
    val feed by state.feed.state.collectAsState()
    val read by app.read.state.collectAsState()
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.discover)) }, onClick = { open = false; app.openDiscover() })
            DropdownMenuItem(text = { Text(pluginString(Strings.add)) }, onClick = { open = false; app.openAdd() })
            if (selected.ordinal < 2 && SubstackHomeRules.anyUnread(feed.posts, read)) {
                DropdownMenuItem(text = { Text(pluginString(Strings.mark_all_read)) }, onClick = { open = false; app.launch { app.read.markRead(feed.posts) } })
            }
        }
    }
}
