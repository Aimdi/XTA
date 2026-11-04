package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_add_to_group
import com.aimdi.xtanative.ui.generated.resources.discover_group_create
import com.aimdi.xtanative.ui.generated.resources.discover_hide_from_main_feed
import com.aimdi.xtanative.ui.generated.resources.discover_in_group
import com.aimdi.xtanative.ui.generated.resources.discover_in_groups
import com.aimdi.xtanative.ui.generated.resources.discover_in_no_group
import com.aimdi.xtanative.ui.generated.resources.discover_in_two_groups
import com.aimdi.xtanative.ui.generated.resources.discover_no_group_found
import com.aimdi.xtanative.ui.generated.resources.discover_no_groups_yet
import com.aimdi.xtanative.ui.generated.resources.discover_search_or_create
import com.aimdi.xtanative.ui.generated.resources.discover_show_in_main_feed
import com.aimdi.xtanative.ui.generated.resources.discover_subscribe
import com.aimdi.xtanative.ui.generated.resources.discover_unsubscribe
import java.util.UUID
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** [change] applied to the session, to its end even when the screen that asked goes away. */
private fun AppGraph.changeSession(change: (Session) -> Session) {
    keep { sessions.update(change) }
}

/**
 * XTA's follow button: an icon for whether [target] is followed here, and a
 * menu to Subscribe or Unsubscribe, Add to group and, for an account, show it
 * in the Following feed or keep it out. Nothing is followed on X.
 */
@Composable
fun FollowMenu(target: FollowTarget, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val state = session?.let(target::state) ?: Followed()
    var open by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val toggle = stringResource(if (state.followed) Res.string.discover_unsubscribe else Res.string.discover_subscribe)
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            val icon = when {
                !state.followed -> DiscoverIcons.PersonAdd
                state.inFeed -> DiscoverIcons.PersonRemove
                else -> DiscoverIcons.VisibilityOff
            }
            Icon(icon, contentDescription = toggle)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(toggle) },
                onClick = {
                    open = false
                    graph.changeSession { target.toggled(it, System.currentTimeMillis()) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.discover_add_to_group)) },
                onClick = {
                    open = false
                    picking = true
                },
            )
            if (state.followed && target is FollowTarget.Account) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (state.inFeed) Res.string.discover_hide_from_main_feed else Res.string.discover_show_in_main_feed)) },
                    onClick = {
                        open = false
                        graph.changeSession(target::feedToggled)
                    },
                )
            }
        }
    }
    if (picking) GroupsSheet(target, onDismiss = { picking = false })
}

/**
 * XTA's group picker: the groups, found or made by typing, with a box for
 * each saying whether [target] is in it. Putting it in a group follows it
 * here first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsSheet(target: FollowTarget, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val picks = remember(session, target) { session?.let { GroupPicker.picks(it, target.memberId) }.orEmpty() }
    var typed by rememberSaveable { mutableStateOf("") }
    val shown = GroupPicker.matching(picks, typed)
    val now = { System.currentTimeMillis() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(Res.string.discover_add_to_group),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.discover_search_or_create)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                picks.isEmpty() && typed.isBlank() -> SheetNote(stringResource(Res.string.discover_no_groups_yet))
                shown.isEmpty() && typed.isNotBlank() -> SheetNote(stringResource(Res.string.discover_no_group_found))
            }
            if (GroupPicker.canCreate(picks, typed)) {
                CreateRow(stringResource(Res.string.discover_group_create, typed.trim())) {
                    val name = typed.trim()
                    typed = ""
                    graph.changeSession { GroupPicker.created(it, target, UUID.randomUUID().toString(), name, now()) }
                }
            }
            shown.forEach { pick ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(value = pick.member, role = Role.Checkbox) { member ->
                            graph.changeSession { GroupPicker.inGroup(it, target, pick.id, member, now()) }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(pick.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Checkbox(checked = pick.member, onCheckedChange = null)
                }
            }
            Hairline(Modifier.padding(vertical = 4.dp))
            PlacedLine(GroupPicker.placed(picks))
        }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun CreateRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "In Cats and News": where the account or search now is. */
@Composable
private fun PlacedLine(placed: Placed) {
    val text = when (placed) {
        Placed.None -> stringResource(Res.string.discover_in_no_group)
        is Placed.One -> stringResource(Res.string.discover_in_group, placed.name)
        is Placed.Two -> stringResource(Res.string.discover_in_two_groups, placed.first, placed.second)
        is Placed.Many -> pluralStringResource(Res.plurals.discover_in_groups, placed.count, placed.count)
    }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (placed != Placed.None) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (placed == Placed.None) 0.dp else 8.dp),
        )
    }
}
