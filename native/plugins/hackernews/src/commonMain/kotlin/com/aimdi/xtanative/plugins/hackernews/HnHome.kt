package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnFeed
import com.aimdi.xtanative.networks.hackernews.HnFollowingReader
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.PluginReadingColumn
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.PullToReload
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.ShellMenuButton
import com.aimdi.xtanative.ui.plugins.ShellStatus
import com.aimdi.xtanative.ui.plugins.ShellSubtitle
import com.aimdi.xtanative.ui.plugins.loaded
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The tabs of XTA's Hacker News reader, in its order: the six lists, then what is saved here and the accounts followed here. */
internal enum class HnTab(val feed: HnFeed?, val label: PluginText) {
    TOP(HnFeed.TOP, Strings.tab_top),
    NEW(HnFeed.NEWEST, Strings.tab_new),
    BEST(HnFeed.BEST, Strings.tab_best),
    ASK(HnFeed.ASK, Strings.tab_ask),
    SHOW(HnFeed.SHOW, Strings.tab_show),
    JOBS(HnFeed.JOBS, Strings.tab_jobs),
    SAVED(null, Strings.tab_saved),
    FOLLOWING(null, Strings.tab_following),
    ;

    /** The glyph XTA's tab strip draws before its name (hn_screen.dart). */
    val icon: ImageVector
        get() = when (this) {
            TOP -> HnIcons.Whatshot
            NEW -> GroupIcons.ScheduleOutlined
            BEST -> HnIcons.EmojiEvents
            ASK -> HnIcons.HelpOutline
            SHOW -> HnIcons.Slideshow
            JOBS -> HnIcons.WorkOutline
            SAVED -> SavedIcons.BookmarkBorder
            FOLLOWING -> GroupIcons.PeopleOutlined
        }
}

/** XTA's Hacker News reader (hn_screen.dart): its tabs with the search and the settings beside them (one bar on its own screen), and the tab chosen. */
@Composable
internal fun HnHome(app: HnApp, frame: PluginFrame) {
    var tab by rememberSaveable { mutableStateOf(HnTab.TOP) }
    val panes = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        ReadingControlsBar {
            PluginHomeTabs(HnTab.entries, tab, { tab = it }, icon = { it.icon }, label = { pluginString(it.label) }, accent = HnBrand) {
                IconButton(onClick = { app.host.openSearch(null) }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
                ShellMenuButton { close ->
                    DropdownMenuItem(text = { Text(pluginString(Strings.settings)) }, onClick = {
                        close()
                        app.host.openSettings()
                    })
                }
            }
        }
        Hairline()
        Box(Modifier.weight(1f).swipeTabs(HnTab.entries, tab) { tab = it }) {
            panes.SaveableStateProvider(tab.name) {
                val feed = tab.feed
                when {
                    feed != null -> FeedPane(app, feed, frame)
                    tab == HnTab.SAVED -> SavedPane(app, frame)
                    else -> FollowingPane(app, frame)
                }
            }
        }
    }
}

/** One of Hacker News' lists, ranked, read page by page as its end comes into view. */
@Composable
private fun FeedPane(app: HnApp, feed: HnFeed, frame: PluginFrame) {
    val pages = rememberKept("hn-feed:${feed.name}") { scope ->
        PagedItems<HnStory>(scope, key = { "${it.id}" }) { cursor -> app.client.feed(feed, cursor) }
    }
    val paged by pages.state.collectAsState()
    val ranks = remember(paged.items) { paged.items.withIndex().associate { (index, story) -> story.id to index + 1 } }
    val read = rememberKept("hn-feed-read:${feed.name}") { scope -> ReadTime(pages, scope) }
    UpdatedLine(read)
    RefreshesOn(frame.refresh) { pages.refresh() }
    HnStoryList(app, pages, PostIcons.Forum, pluginString(Strings.feed_empty), frame) { story ->
        HnStoryRow(app, story, rank = ranks[story.id])
    }
}

/** The stories saved on this device, newest first; a pull reads what is kept here again, as XTA's Saved tab does. */
@Composable
private fun SavedPane(app: HnApp, frame: PluginFrame) {
    val kept by app.kept.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToReload(reload = { app.host.settings.loaded() }) {
        PluginReadingColumn(frame.padding, list) { column -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = column) {
            if (kept.saved.isEmpty()) item(key = "empty") { EmptyPane(SavedIcons.BookmarkBorder, pluginString(Strings.saved_empty)) }
            items(kept.saved, key = { "${it.id}" }) { story -> HnStoryRow(app, story) }
        } }
    }
}

/** What the accounts followed here posted lately, read each time the tab opens or the follows change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingPane(app: HnApp, frame: PluginFrame) {
    val kept by app.kept.collectAsState()
    val reader = rememberKept("hn-following") { scope -> HnFollowingReader(app.client, { app.kept.value.follows }, scope) }
    val state by reader.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(kept.follows) { reader.refresh() }
    ScrollsToTop(list, frame.toTop)
    RefreshesOn(frame.refresh) { reader.refresh() }
    val stories = state.value.orEmpty()
    val error = state.error
    PullToRefreshBox(isRefreshing = state.loading && stories.isNotEmpty(), onRefresh = reader::refresh, modifier = Modifier.fillMaxSize()) {
        PluginReadingColumn(frame.padding, list) { column -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = column) {
            when {
                stories.isEmpty() && state.loading -> item(key = "loading") { FeedSkeleton() }
                stories.isEmpty() && error != null -> item(key = "problem") { NetworkProblemView(problemOf(error), app.network, reader::refresh) }
                stories.isEmpty() -> item(key = "empty") {
                    EmptyPane(GroupIcons.PeopleOutlined, pluginString(if (kept.follows.isEmpty()) Strings.following_empty else Strings.feed_empty)) {
                        TextButton(onClick = { app.host.openSearch(null) }) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(pluginString(Strings.search), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            items(stories, key = { "${it.id}" }) { story -> HnStoryRow(app, story) }
        } }
    }
}

/** Hacker News' orange. */
internal val HnBrand = Color(0xFFFF6600)

/**
 * When [pages]' first page was last read whole: set as a read or a refresh
 * of it arrives, not as a later page does nor as the list is shown again,
 * and kept with the list ([rememberKept]).
 */
private class ReadTime(pages: PagedItems<*>, scope: CoroutineScope) {
    private val mutable = MutableStateFlow<Instant?>(null)
    val at: StateFlow<Instant?> = mutable.asStateFlow()

    init {
        scope.launch {
            pages.state.map { it.loading }.distinctUntilChanged().collect { loading ->
                val now = pages.state.value
                if (!loading && now.loaded && now.problem == null) mutable.value = Instant.now()
            }
        }
    }
}

/**
 * When the list shown was last read ([ReadTime]), under Hacker News' name in
 * the app's shell ("Updated 5m ago"), live while that is under ten minutes;
 * the line moves on each minute.
 */
@Composable
private fun UpdatedLine(time: ReadTime) {
    val read = time.at.collectAsState().value ?: return
    val minute by produceState(0, read) {
        while (true) {
            delay(60_000)
            value++
        }
    }
    key(minute) {
        val fresh = Duration.between(read, Instant.now()) < LIVE_FOR
        ShellSubtitle(pluginString(Strings.updated_ago, agoText(read)), if (fresh) ShellStatus.LIVE else ShellStatus.NONE)
    }
}

/** How long a list read counts as live. */
private val LIVE_FOR: Duration = Duration.ofMinutes(10)
