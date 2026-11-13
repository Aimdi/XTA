package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.aimdi.xtanative.ui.home.rememberRetappedList
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.groups_add_to_group
import com.aimdi.xtanative.ui.generated.resources.groups_hide_from_main_feed
import com.aimdi.xtanative.ui.generated.resources.groups_import_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_no_results
import com.aimdi.xtanative.ui.generated.resources.groups_no_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account_description
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account_off
import com.aimdi.xtanative.ui.generated.resources.groups_search
import com.aimdi.xtanative.ui.generated.resources.groups_show_in_main_feed
import com.aimdi.xtanative.ui.generated.resources.groups_unsubscribe
import org.jetbrains.compose.resources.stringResource

/** What a row of the people list can be asked. */
class PeopleActions(
    val open: (SubscriptionRow) -> Unit,
    val unfollow: (SubscriptionRow) -> Unit,
    val pickGroups: (SubscriptionRow) -> Unit,
    val setInFeed: (SubscriptionRow, Boolean) -> Unit,
    val setPostCap: (SubscriptionRow, Int?) -> Unit,
    val move: (Int, Int) -> Unit,
)

/** The people followed here (_list.dart): in the chosen order or the reader's own, which a long press drags rows into. */
@Composable
fun PeoplePage(state: SubscriptionsUi, actions: PeopleActions, onImport: () -> Unit, onSearch: () -> Unit, contentPadding: PaddingValues) {
    when {
        state.followedCount == 0 -> NobodyFollowed(onImport, onSearch, contentPadding)
        state.people.isEmpty() -> Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.groups_no_results))
        }
        else -> PeopleList(state, actions, contentPadding)
    }
}

@Composable
private fun NobodyFollowed(onImport: () -> Unit, onSearch: () -> Unit, contentPadding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(start = 24.dp, end = 24.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(GroupIcons.PeopleOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Text(stringResource(Res.string.groups_no_subscriptions), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImport) {
            Icon(GroupIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.groups_import_subscriptions), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.groups_search), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PeopleList(state: SubscriptionsUi, actions: PeopleActions, contentPadding: PaddingValues) {
    val list = rememberRetappedList()
    val drag = rememberRowDrag(list)
    val rows = state.people.associateBy { it.key }
    val keys: List<Any> = state.people.map { it.key }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(drag.shown(keys), key = { it }) { key ->
            val row = rows[key] ?: return@items
            val dragged = drag.key == key
            val reorder = if (!state.canDragPeople) {
                Modifier
            } else {
                Modifier.pointerInput(key, keys) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { drag.start(key, keys) },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.drag(amount.y)
                        },
                        onDragEnd = { drag.end()?.let { (from, to) -> actions.move(from, to) } },
                        onDragCancel = { drag.end() },
                    )
                }
            }
            Box(
                Modifier
                    .graphicsLayer { translationY = if (dragged) drag.offset else 0f }
                    .let { if (dragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)) else it }
                    .then(reorder),
            ) {
                SubscriptionRowView(row, actions)
            }
        }
    }
}

/**
 * One followed account, saved search or plugin's account (_list.dart's
 * buildSubscriptionTile): its face or mark, its name and the line under it,
 * Add to group beside a search's and a plugin account's menu, and its menu.
 */
@Composable
private fun SubscriptionRowView(row: SubscriptionRow, actions: PeopleActions) {
    var menu by remember { mutableStateOf(false) }
    var capping by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { actions.open(row) }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubscriptionFace(row, 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (row.verified) {
                    Icon(XIcons.Verified, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
            }
            Text(
                subscriptionSubtitle(row),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!row.isX) {
            IconButton(onClick = { actions.pickGroups(row) }) {
                Icon(GroupIcons.Group, contentDescription = stringResource(Res.string.groups_add_to_group))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    if (row.inFeed || !row.isX) GroupIcons.PersonRemove else GroupIcons.VisibilityOff,
                    contentDescription = stringResource(Res.string.groups_unsubscribe),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.groups_unsubscribe)) }, onClick = {
                    menu = false
                    actions.unfollow(row)
                })
                DropdownMenuItem(text = { Text(stringResource(Res.string.groups_add_to_group)) }, onClick = {
                    menu = false
                    actions.pickGroups(row)
                })
                if (row.isX) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (row.inFeed) Res.string.groups_hide_from_main_feed else Res.string.groups_show_in_main_feed)) },
                        onClick = {
                            menu = false
                            actions.setInFeed(row, !row.inFeed)
                        },
                    )
                    DropdownMenuItem(text = { Text(stringResource(Res.string.groups_quiet_account)) }, onClick = {
                        menu = false
                        capping = true
                    })
                }
            }
        }
    }
    if (capping) {
        PostCapDialog(row.screenName, SubscriptionList.capChoice(row.postCap), onPick = { actions.setPostCap(row, it) }, onDismiss = { capping = false })
    }
}

/** XTA's "max posts per feed load" for one account: off, or one of its few choices. */
@Composable
fun PostCapDialog(screenName: String, current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_quiet_account)) },
        text = {
            Column {
                Text("@$screenName", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.groups_quiet_account_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SubscriptionList.POST_CAPS.forEach { cap ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = cap == current, role = Role.RadioButton) {
                                onPick(cap)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = cap == current, onClick = null)
                        Text(cap?.toString() ?: stringResource(Res.string.groups_quiet_account_off), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
