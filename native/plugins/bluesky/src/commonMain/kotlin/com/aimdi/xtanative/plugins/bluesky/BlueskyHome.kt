package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyTab
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.ShellFeeds
import com.aimdi.xtanative.ui.plugins.ShellFilter
import com.aimdi.xtanative.ui.plugins.ShellMenuButton
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * The Bluesky tab, as XTA's Bluesky screen: Following (the accounts followed
 * here), Discover (custom feeds), Lists and Liked (the likes kept on this
 * device), the one open kept for the next launch; search, and a menu to
 * add an account, open what was saved, import follows and open the settings.
 * [onHome] is its page among Home's timelines, where the people its feed
 * brings fold away under the third post.
 */
@Composable
internal fun BlueskyHome(app: BlueskyApp, frame: PluginFrame, onHome: Boolean) {
    val reading by app.prefs.reading.collectAsState()
    var tab by rememberSaveable { mutableStateOf(reading.tab) }
    val select: (BlueskyTab) -> Unit = { chosen ->
        tab = chosen
        app.launch { app.prefs.reading { it.copy(tab = chosen) } }
    }
    val following = rememberReaderFilter(app, likes = false)
    val liked = rememberReaderFilter(app, likes = true)
    val filters = when (tab) {
        BlueskyTab.FOLLOWING -> following
        BlueskyTab.LIKED -> liked
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        ReadingControlsBar { HomeBar(app, tab, select, filters) }
        Hairline()
        Box(Modifier.weight(1f).swipeTabs(BlueskyTab.entries, tab, select)) {
            when (tab) {
                BlueskyTab.FOLLOWING -> FollowingPane(app, frame, onHome, following)
                BlueskyTab.DISCOVER -> FeedsPane(app, frame)
                BlueskyTab.LISTS -> ListsPane(app, frame)
                BlueskyTab.LIKED -> LikedPane(app, frame, liked)
            }
        }
    }
}

private val BlueskyTab.label: PluginText
    get() = when (this) {
        BlueskyTab.FOLLOWING -> Strings.tab_following
        BlueskyTab.DISCOVER -> Strings.tab_discover
        BlueskyTab.LISTS -> Strings.tab_lists
        BlueskyTab.LIKED -> Strings.tab_liked
    }

private val BlueskyTab.icon: ImageVector
    get() = when (this) {
        BlueskyTab.FOLLOWING -> Icons.Outlined.Home
        BlueskyTab.DISCOVER -> PostIcons.Sparkle
        BlueskyTab.LISTS -> BlueskyIcons.ListAlt
        BlueskyTab.LIKED -> Icons.Filled.FavoriteBorder
    }

/** XTA's chrome for the tab (PluginHomeChrome with its dock): its four tabs, then search, the list's [filters] where it has some, and the menu. */
@Composable
private fun HomeBar(app: BlueskyApp, tab: BlueskyTab, onTab: (BlueskyTab) -> Unit, filters: ReaderFilter?) {
    if (LocalPluginClientBar.current != null) return ShellBar(app, tab, onTab, filters)
    PluginHomeTabs(BlueskyTab.entries, tab, onTab, icon = { it.icon }, label = { pluginString(it.label) }) {
        IconTip(pluginString(Strings.search)) { IconButton(onClick = { app.openSearch() }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) } }
        if (filters != null) FiltersButton(filters.first, filters.second)
        HomeMenu(app)
    }
}

/**
 * Bluesky in the app's shell (the unified plugin shell): its four lists as
 * the shell's feed row, the list's filters, where it has some, as its
 * filter; its search alone on the bar, its menu in the plugin sheet. No
 * sections of its own, so no bottom bar.
 */
@Composable
private fun ShellBar(app: BlueskyApp, tab: BlueskyTab, onTab: (BlueskyTab) -> Unit, filters: ReaderFilter?) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    PluginHomeTabs(emptyList()) {
        IconTip(pluginString(Strings.search)) { IconButton(onClick = { app.openSearch() }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) } }
        HomeMenu(app)
    }
    ShellFeeds(shellFeeds(app, tab, onTab), filter = filters?.let { ShellFilter(it.first.setCount() > 0) { filtering = true } })
    if (filtering && filters != null) FiltersSheet(filters.first, filters.second) { filtering = false }
}

/**
 * The shell's feed row: Following, Discover and each custom feed pinned
 * (which open in Discover's place, as its chips did), then Lists and Liked.
 */
@Composable
private fun shellFeeds(app: BlueskyApp, tab: BlueskyTab, onTab: (BlueskyTab) -> Unit): List<PluginHomeTab> {
    val feeds = app.feeds
    val state by feeds.state.collectAsState()
    val pinned by app.prefs.pinnedFeeds.collectAsState()
    val discover = pluginString(Strings.tab_discover)
    val offered = listOf(Bluesky.DISCOVER_FEED_URI to discover) + feeds.offered(pinned).map { it.uri to it.displayName }
    // A feed opened another way (a pasted address) has a word too, so the row says what is shown.
    val open = state.selected.takeIf { selected -> selected.uri.isNotEmpty() && offered.none { it.first == selected.uri } }
    val custom = offered + listOfNotNull(open?.let { it.uri to it.name.ifBlank { discover } })
    val inDiscover = custom.map { (uri, name) ->
        PluginHomeTab(BlueskyTab.DISCOVER.icon, name, tab == BlueskyTab.DISCOVER && state.selected.uri == uri) {
            feeds.open(uri, name)
            onTab(BlueskyTab.DISCOVER)
        }
    }
    val (following, rest) = listOf(BlueskyTab.FOLLOWING, BlueskyTab.LISTS, BlueskyTab.LIKED)
        .map { other -> PluginHomeTab(other.icon, pluginString(other.label), tab == other) { onTab(other) } }
        .let { it.take(1) to it.drop(1) }
    return following + inDiscover + rest
}

/** XTA's Bluesky menu: add an account, what was saved from Bluesky, import follows three ways, the settings. */
@Composable
private fun HomeMenu(app: BlueskyApp) {
    var adding by rememberSaveable { mutableStateOf(false) }
    ShellMenuButton { close ->
        listOf<Pair<PluginText, () -> Unit>>(
            Strings.add_account to { adding = true },
            Strings.saved to { app.host.open(BlueskyRoutes.SAVED) },
            Strings.import_following to { app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING)) },
            Strings.import_list to { app.host.open(BlueskyRoutes.import(ImportKind.LIST)) },
            Strings.import_starter to { app.host.open(BlueskyRoutes.import(ImportKind.STARTER)) },
            Strings.settings to { app.host.openSettings() },
        ).forEach { (text, action) ->
            DropdownMenuItem(text = { Text(pluginString(text)) }, onClick = {
                close()
                action()
            })
        }
    }
    if (adding) AddAccountDialog(app) { adding = false }
}

/** Asks for a handle or DID and follows that account here once the AppView knows it. */
@Composable
internal fun AddAccountDialog(app: BlueskyApp, onDismiss: () -> Unit) {
    val invalid = pluginString(Strings.invalid_handle)
    TextPrompt(pluginString(Strings.add_account), pluginString(Strings.handle_hint), onDismiss) { typed ->
        val actor = BlueskyUrls.handle(typed) ?: return@TextPrompt invalid
        app.follows.follow(app.client.profile(actor), System.currentTimeMillis())
        null
    }
}
