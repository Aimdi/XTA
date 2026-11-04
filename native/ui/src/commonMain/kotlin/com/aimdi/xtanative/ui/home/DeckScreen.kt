package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import com.aimdi.xtanative.ui.group.GroupFeedBody
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.home_deck
import com.aimdi.xtanative.ui.generated.resources.home_deck_empty
import com.aimdi.xtanative.ui.post.PostActions
import org.jetbrains.compose.resources.stringResource

/**
 * The deck (XTA's deck screen): the groups pinned to it side by side, each a
 * column with its own group feed, for a tablet or a wide window. The
 * desktop's F5 and Ctrl+R read every column again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val columns = session?.deckGroups.orEmpty()
    var toTop by remember { mutableStateOf(ToTop(0, 0)) }
    var refreshed by remember { mutableIntStateOf(0) }
    DesktopShortcuts(refresh = { refreshed++ }, turn = {}, isOpen = { false }, close = {})
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.home_deck)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (columns.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.home_deck_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(Modifier.fillMaxSize().padding(padding)) {
                columns.forEachIndexed { index, group ->
                    if (index > 0) Box(Modifier.fillMaxHeight().width(Dp.Hairline).background(MaterialTheme.colorScheme.outlineVariant))
                    key(group.id) {
                        DeckColumn(group, if (toTop.page == index) toTop.count else 0, refreshed, actions, Modifier.weight(1f)) { toTop = ToTop(index, toTop.count + 1) }
                    }
                }
            }
        }
    }
}

/** One group of the deck: its name, a tap on which goes back to the top ([toTop] grows), over its group feed, read again as [refreshed] grows. */
@Composable
private fun DeckColumn(group: FollowGroup, toTop: Int, refreshed: Int, actions: PostActions, modifier: Modifier, onTitle: () -> Unit) {
    val vm = groupFeedViewModel(group.id)
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    RefreshesOn(refreshed) { vm.refresh() }
    Column(modifier.fillMaxHeight()) {
        Text(
            HomeStrip.groupLabel(group),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onTitle).padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Hairline()
        GroupFeedBody(state, vm, actions, list, PaddingValues(), mediaOnly = false, toTop = toTop)
    }
}
