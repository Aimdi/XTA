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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_add_member_hint
import com.aimdi.xtanative.ui.generated.resources.groups_no_results
import com.aimdi.xtanative.ui.generated.resources.groups_ok
import com.aimdi.xtanative.ui.generated.resources.groups_search
import com.aimdi.xtanative.ui.generated.resources.groups_search_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource

/** The add box as it stands: what was typed, what X found for it, and who was added. */
private data class AddMemberUi(
    val query: String = "",
    val users: List<XUser>? = null,
    val failed: Boolean = false,
    val searching: Boolean = false,
    val adding: Boolean = false,
    val added: Set<String> = emptySet(),
)

/**
 * Adding someone new to a group without leaving it (_group_add_member.dart):
 * an X search, and a tap follows the account here and ticks it. [onDone]
 * hears the ids added. XTA's box takes other networks' handles too; those
 * networks' follows are not in XTA Native's session, so this one searches X.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberSheet(actions: GroupActions, onDone: (Set<String>) -> Unit) {
    val graph = LocalGraph.current
    var state by remember { mutableStateOf(AddMemberUi()) }
    var search by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun find() {
        val query = state.query.trim()
        if (query.isEmpty()) return
        search?.cancel()
        state = state.copy(searching = true, failed = false, users = null)
        search = scope.launch {
            state = try {
                val users = withTimeout(30_000) { graph.client.searchUsers(query, count = 20).users }
                if (state.query.trim() == query) state.copy(users = users, searching = false) else state
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.copy(failed = true, searching = false)
            }
        }
    }

    fun add(user: XUser) {
        if (state.adding || user.id in state.added) return
        state = state.copy(adding = true)
        scope.launch {
            actions.follow(user)
            state = state.copy(adding = false, added = state.added + user.id)
        }
    }

    ModalBottomSheet(onDismissRequest = { onDone(state.added) }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight(0.85f).imePadding().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = {
                    search?.cancel()
                    state = state.copy(query = it, users = null, failed = false, searching = false)
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.groups_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { find() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                if (state.adding) LinearProgressIndicator(Modifier.fillMaxWidth())
                Results(state, ::add)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(Modifier.weight(1f))
                TextButton(onClick = { onDone(state.added) }, enabled = !state.adding) { Text(stringResource(Res.string.groups_ok)) }
            }
        }
    }
}

@Composable
private fun Results(state: AddMemberUi, onAdd: (XUser) -> Unit) {
    val users = state.users
    when {
        state.query.isBlank() -> Text(
            stringResource(Res.string.groups_add_member_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
        )
        state.failed -> Text(stringResource(Res.string.groups_search_failed), modifier = Modifier.padding(24.dp))
        state.searching -> LoadingRow()
        users != null && users.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.groups_no_results))
        }
        users != null -> LazyColumn {
            items(users, key = { it.id }) { user ->
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
