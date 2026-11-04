package com.aimdi.xtanative.plugins.bluesky

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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyTab
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * The Bluesky tab, as XTA's Bluesky screen: Following (the accounts followed
 * here), Discover (custom feeds), Lists and Liked (the likes kept on this
 * device), the one open kept for the next launch; search, and a menu to
 * add an account, import follows and open the settings.
 */
@Composable
internal fun BlueskyHome(app: BlueskyApp, frame: PluginFrame) {
    val reading by app.prefs.reading.collectAsState()
    var tab by rememberSaveable { mutableStateOf(reading.tab) }
    Column(Modifier.fillMaxSize()) {
        HomeBar(app, tab) { chosen ->
            tab = chosen
            app.launch { app.prefs.reading { it.copy(tab = chosen) } }
        }
        Hairline()
        Box(Modifier.weight(1f)) {
            when (tab) {
                BlueskyTab.FOLLOWING -> FollowingPane(app, frame)
                BlueskyTab.DISCOVER -> FeedsPane(app, frame)
                BlueskyTab.LISTS -> ListsPane(app, frame)
                BlueskyTab.LIKED -> LikedPane(app, frame)
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

@Composable
private fun HomeBar(app: BlueskyApp, tab: BlueskyTab, onTab: (BlueskyTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlueskyTab.entries.forEach { choice ->
                FilterChip(
                    selected = tab == choice,
                    onClick = { onTab(choice) },
                    label = { Text(pluginString(choice.label), maxLines = 1) },
                    leadingIcon = { Icon(choice.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        IconButton(onClick = { app.openSearch() }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
        HomeMenu(app)
    }
}

/** XTA's Bluesky menu: add an account, import follows three ways, the settings. */
@Composable
private fun HomeMenu(app: BlueskyApp) {
    var open by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf<Pair<PluginText, () -> Unit>>(
                Strings.add_account to { adding = true },
                Strings.import_following to { app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING)) },
                Strings.import_list to { app.host.open(BlueskyRoutes.import(ImportKind.LIST)) },
                Strings.import_starter to { app.host.open(BlueskyRoutes.import(ImportKind.STARTER)) },
                Strings.settings to { app.host.openSettings() },
            ).forEach { (text, action) ->
                DropdownMenuItem(text = { Text(pluginString(text)) }, onClick = {
                    open = false
                    action()
                })
            }
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
