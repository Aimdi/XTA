package com.aimdi.xtanative.plugins.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.rss.RssTimelineRules
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ShellMenuButton
import com.aimdi.xtanative.ui.plugins.pluginString

/** The RSS tab's panes, in XTA's order. */
internal enum class RssPane(val label: PluginText, val icon: ImageVector) {
    HOME(Strings.home, Icons.Outlined.Home),
    FEEDS(Strings.feeds, RssIcons.Feed),
}

/**
 * RSS's home tab, as XTA's RssScreen: Home (every followed feed's items,
 * filtered by unread and by tag) and Feeds (the feeds followed, each with
 * its tags), with Add feed, Mark all as read and Settings beside them.
 * Follows, read marks and tags stay on this device.
 */
@Composable
internal fun RssHome(app: RssApp, frame: PluginFrame) {
    val selected by app.pane.collectAsState()
    val feeds by app.feeds.collectAsState()
    // XTA reads its feeds, then the timeline once the tab is up, unless it is fresh; other follows read it again.
    LaunchedEffect(feeds) { if (feeds != null) app.timeline.refresh() }
    RefreshesOn(frame.refresh) { app.timeline.refresh(force = true) }
    Column(Modifier.fillMaxSize()) {
        ReadingControlsBar { Header(app, selected) { app.pane.value = it } }
        Hairline()
        Box(Modifier.weight(1f).swipeTabs(RssPane.entries, selected) { app.pane.value = it }) {
            val followed = feeds
            when {
                followed == null -> PluginFeedSkeleton()
                selected == RssPane.HOME -> RssHomePane(app, followed, frame)
                else -> RssFeedsPane(app, followed, frame)
            }
        }
    }
}

/** XTA's chrome for the tab (PluginHomeChrome): Home and Feeds, then Add feed and the menu. */
@Composable
private fun Header(app: RssApp, selected: RssPane, onSelect: (RssPane) -> Unit) {
    PluginHomeTabs(RssPane.entries, selected, onSelect, icon = { it.icon }, label = { pluginString(it.label) }) {
        // In the app's shell Add is in the plugin sheet ([HomeMenu]), and the bar keeps nothing else.
        if (LocalPluginClientBar.current == null) IconButton(onClick = app::openAdd) { Icon(Icons.Default.Add, pluginString(Strings.add)) }
        HomeMenu(app, selected)
    }
}

@Composable
private fun HomeMenu(app: RssApp, selected: RssPane) {
    val timeline by app.timeline.state.collectAsState()
    val read by app.read.collectAsState()
    val shell = LocalPluginClientBar.current != null
    ShellMenuButton { close ->
        if (shell) DropdownMenuItem(text = { Text(pluginString(Strings.add)) }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }, onClick = { close(); app.openAdd() })
        if (selected == RssPane.HOME && RssTimelineRules.anyUnread(timeline.items, read)) {
            DropdownMenuItem(text = { Text(pluginString(Strings.mark_all_read)) }, onClick = { close(); app.launch { app.markRead(timeline.items.map { it.id }) } })
        }
        DropdownMenuItem(text = { Text(pluginString(Strings.settings)) }, onClick = { close(); app.host.openSettings() })
    }
}
