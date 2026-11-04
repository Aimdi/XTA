package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_feed_accounts
import com.aimdi.xtanative.ui.generated.resources.home_filter_accounts
import com.aimdi.xtanative.ui.generated.resources.home_filter_accounts_explained
import com.aimdi.xtanative.ui.generated.resources.home_filter_all
import com.aimdi.xtanative.ui.generated.resources.home_filter_apply
import com.aimdi.xtanative.ui.generated.resources.home_filter_count
import com.aimdi.xtanative.ui.generated.resources.home_filter_groups_explained
import com.aimdi.xtanative.ui.generated.resources.home_filter_keep_one
import com.aimdi.xtanative.ui.generated.resources.home_filter_none
import com.aimdi.xtanative.ui.generated.resources.home_filter_only_hint
import com.aimdi.xtanative.ui.generated.resources.home_filter_reset
import com.aimdi.xtanative.ui.generated.resources.home_group_members
import com.aimdi.xtanative.ui.generated.resources.home_groups
import com.aimdi.xtanative.ui.generated.resources.home_no_results
import com.aimdi.xtanative.ui.generated.resources.search
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home feed accounts (XTA's home filter sheet): which X accounts For you
 * merges, and which groups stay off the home feeds. A tap turns one on or
 * off, a long press keeps only it on; nothing is kept until Apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeFilterSheet(ui: HomeUi, onApply: (HomeFilterDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(ui.filter) }
    var groupsShown by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val accounts = ui.session.accounts
    val accountIds = accounts.map { it.id }
    val groups = FollowGroup.ordered(ui.session.groups)
    val needle = query.trim().lowercase()
    val shownAccounts = accounts.filter { needle in it.screenName.orEmpty().lowercase() }
    val shownGroups = DrawerGroups.matching(groups, query)
    val ids = if (groupsShown) groups.map { it.id } else accountIds

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.home_feed_accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) }
            }
            TabRow(selectedTabIndex = if (groupsShown) 1 else 0, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab(selected = !groupsShown, onClick = { groupsShown = false }, text = { Text(stringResource(Res.string.home_filter_accounts)) })
                Tab(selected = groupsShown, onClick = { groupsShown = true }, text = { Text(stringResource(Res.string.home_groups)) })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Text(
                stringResource(if (groupsShown) Res.string.home_filter_groups_explained else Res.string.home_filter_accounts_explained) +
                    " " + stringResource(Res.string.home_filter_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (ids.isNotEmpty()) {
                val active = draft.active(groupsShown, ids)
                BulkBar(
                    status = if (!groupsShown && active == 0) {
                        stringResource(Res.string.home_filter_keep_one)
                    } else {
                        stringResource(Res.string.home_filter_count, active, ids.size)
                    },
                    warning = !groupsShown && active == 0,
                    onAll = { draft = draft.selecting(groupsShown, emptySet()) }.takeIf { active < ids.size },
                    onNone = { draft = draft.selecting(groupsShown, ids.toSet()) }.takeIf { active > 0 },
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                if ((groupsShown && shownGroups.isEmpty()) || (!groupsShown && shownAccounts.isEmpty())) {
                    item(key = "none") {
                        Text(stringResource(Res.string.home_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    }
                }
                if (groupsShown) {
                    items(shownGroups, key = { "g-" + it.id }) { group ->
                        FilterRow(
                            title = group.name,
                            subtitle = pluralStringResource(Res.plurals.home_group_members, group.memberIds.size, group.memberIds.size),
                            on = group.id !in draft.groupsOff,
                            onToggle = { on -> draft = draft.toggled(groups = true, group.id, on) },
                            onOnly = { draft = draft.only(groups = true, group.id, ids) },
                        ) { GroupMark(group, 32.dp) }
                    }
                } else {
                    items(shownAccounts, key = { "a-" + it.id }) { account ->
                        FilterRow(
                            title = "@" + (account.screenName ?: account.id),
                            subtitle = null,
                            on = account.id !in draft.accountsOff,
                            onToggle = { on -> draft = draft.toggled(groups = false, account.id, on) },
                            onOnly = { draft = draft.only(groups = false, account.id, ids) },
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { draft = HomeFilterDraft.NONE_OFF }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.home_filter_reset))
                }
                Button(onClick = { onApply(draft) }, enabled = draft.keepsAnAccount(accountIds), modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.home_filter_apply))
                }
            }
        }
    }
}

/** How many are on, with All and None for the section shown; [warning] when no account would be left. */
@Composable
private fun BulkBar(status: String, warning: Boolean, onAll: (() -> Unit)?, onNone: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onAll?.invoke() }, enabled = onAll != null) { Text(stringResource(Res.string.home_filter_all)) }
        TextButton(onClick = { onNone?.invoke() }, enabled = onNone != null) { Text(stringResource(Res.string.home_filter_none)) }
    }
}

/** One account or group: a tap turns it on or off, a long press keeps only it on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterRow(
    title: String,
    subtitle: String?,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    onOnly: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(role = Role.Checkbox, onLongClick = onOnly, onClick = { onToggle(!on) })
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Checkbox(checked = on, onCheckedChange = null)
    }
}
