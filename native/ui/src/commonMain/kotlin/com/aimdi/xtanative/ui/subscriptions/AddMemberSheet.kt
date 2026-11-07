package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_add_member_failed
import com.aimdi.xtanative.ui.generated.resources.groups_add_member_hint
import com.aimdi.xtanative.ui.generated.resources.groups_no_results
import com.aimdi.xtanative.ui.generated.resources.groups_ok
import com.aimdi.xtanative.ui.generated.resources.groups_search
import com.aimdi.xtanative.ui.generated.resources.groups_search_failed
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.pluginstore.PluginMark
import com.aimdi.xtanative.ui.plugins.currentLanguage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Adding something new to a group without leaving it (_group_add_member.dart):
 * an X search, and, as it is typed, a row for each running plugin's network
 * the text could name (a subreddit, a Threads or Bluesky handle, a Fediverse
 * address, a newsletter or a feed: [PluginFollows.followables]). A tap follows
 * it here and ticks it; [onDone] hears the member ids added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberSheet(actions: GroupActions, onDone: (Set<String>) -> Unit) {
    val graph = LocalGraph.current
    val follows = LocalPlugins.current.follows
    val scope = rememberCoroutineScope()
    val model = remember { AddMemberModel(scope) }
    val state by model.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun find() = model.find { query -> graph.client.searchUsers(query, count = 20).users }

    fun add(user: XUser) {
        scope.launch { model.add(user.id) { actions.follow(user); user.id } }
    }

    fun add(row: FollowableRow) {
        if (state.adding || row.key in state.addedRows) return
        scope.launch {
            if (!model.add(row.key, row.follow)) snackbars.showSnackbar(getString(Res.string.groups_add_member_failed))
        }
    }

    ModalBottomSheet(onDismissRequest = { onDone(state.added) }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight(0.85f).imePadding().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = model::type,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.groups_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { find() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                if (state.adding) LinearProgressIndicator(Modifier.fillMaxWidth())
                val rows = remember(state.query, follows) { follows.followables(state.query) }
                Results(state, rows, ::add, ::add)
                SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(Modifier.weight(1f))
                TextButton(onClick = { onDone(state.added) }, enabled = !state.adding) { Text(stringResource(Res.string.groups_ok)) }
            }
        }
    }
}

@Composable
private fun Results(state: AddMemberUi, rows: List<FollowableRow>, onAdd: (XUser) -> Unit, onFollow: (FollowableRow) -> Unit) {
    val users = state.users
    if (state.query.isBlank()) {
        Text(
            stringResource(Res.string.groups_add_member_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
        )
        return
    }
    LazyColumn {
        items(rows, key = { "row:" + it.key }) { row -> NetworkRow(row, added = row.key in state.addedRows, enabled = !state.adding, onFollow) }
        when {
            state.failed -> item(key = "failed") { Text(stringResource(Res.string.groups_search_failed), modifier = Modifier.padding(24.dp)) }
            state.searching -> item(key = "searching") { LoadingRow() }
            users.isNullOrEmpty() && rows.isEmpty() -> item(key = "none") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.groups_no_results)) }
            }
            else -> items(users.orEmpty(), key = { it.id }) { user ->
                val added = user.id in state.added
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = !state.adding && !added) { onAdd(user) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(user.avatarUrl, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${user.screenName}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (added) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null)
                }
            }
        }
    }
}

/** Another network's row: its mark, what it would follow, the network's name, and its tick once followed. */
@Composable
private fun NetworkRow(row: FollowableRow, added: Boolean, enabled: Boolean, onFollow: (FollowableRow) -> Unit) {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    val owner = running.firstOrNull { it.plugin.id == row.plugin }
    val language = currentLanguage()
    val network = owner?.let { runCatching { it.plugin.title(language) }.getOrNull() } ?: row.plugin
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled && !added) { onFollow(row) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginMark(owner, network, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(network, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (added) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null)
    }
}
