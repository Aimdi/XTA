package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.launch

/**
 * A source's lists (web_source_screen.dart), one labelled tab each, opened
 * on [listId]; following the list on show, the source's search when it has
 * one, its site, and removing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSourceScreen(app: WebSourcesApp, source: WebSourceDefinition, listId: String?) {
    val feeds = rememberKept("websource-${source.id}-${source.version}") { scope -> source.feeds.map { WebSourceFeed(app.client, source, it, scope) } }
    val pager = rememberPagerState(initialPage = source.feeds.indexOfFirst { it.id == listId }.coerceAtLeast(0)) { feeds.size }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WebSourceMark(source)
                            Spacer(Modifier.width(12.dp))
                            Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { BackButton(app) },
                    actions = {
                        FollowButton(app, source, pager.currentPage)
                        if (source.search != null) IconButton(onClick = { app.openSearch(source) }) { Icon(Icons.Default.Search, pluginString(Strings.search_in, source.name)) }
                        SourceMenu(app, source)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (feeds.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = pager.currentPage,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        indicator = { tabs -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabs[pager.currentPage]), color = sourceColor(source)) },
                    ) {
                        source.feeds.forEachIndexed { index, list ->
                            Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(list.name) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
            Box(Modifier.fillMaxSize()) { WebSourceListPane(app, feeds[page]) }
        }
    }
}

/** Follows the list on show, or stops following it: kept on this device. */
@Composable
private fun FollowButton(app: WebSourcesApp, source: WebSourceDefinition, page: Int) {
    val follows by app.follows.collectAsState()
    val list = source.feeds.getOrNull(page) ?: return
    val following = app.isFollowing(follows.orEmpty(), source, list)
    IconButton(onClick = { app.launch { if (following) app.unfollow(source, list) else app.follow(source, list) } }) {
        Icon(
            if (following) Icons.Filled.CheckCircle else WebSourcesIcons.AddCircle,
            pluginString(if (following) Strings.unfollow else Strings.follow),
            tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceMenu(app: WebSourcesApp, source: WebSourceDefinition) {
    var open by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.open_in_browser)) }, onClick = { open = false; app.host.openLink(source.homepage.toString()) })
            DropdownMenuItem(text = { Text(pluginString(Strings.remove)) }, onClick = { open = false; removing = true })
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(pluginString(Strings.remove_confirm, source.name)) },
            confirmButton = { Button(onClick = { removing = false; app.launch { app.remove(source.id) }; app.host.back() }) { Text(pluginString(Strings.remove)) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(pluginString(Strings.cancel)) } },
        )
    }
}
