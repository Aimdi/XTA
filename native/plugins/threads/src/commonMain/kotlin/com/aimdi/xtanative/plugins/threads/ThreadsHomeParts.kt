package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.common.XIcons

/** Who is followed (a sheet of them) and the filters, as the tab's bar holds them in Home's strip. */
@Composable
internal fun DockRow(app: ThreadsApp, options: ThreadsFeedOptions, onAdd: () -> Unit) {
    var following by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { following = true }) {
            Icon(PostIcons.People, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.following), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { filtering = true }) {
            BadgedBox(badge = { if (options.filtered) Badge { Text("${options.activeCount}") } }) {
                Icon(PostIcons.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(pluginString(Strings.filters), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (following) FollowingSheet(app, onAdd) { following = false }
    if (filtering) FilterSheet(app) { filtering = false }
}

/** Every account followed here: each opens its profile and can be unfollowed, and one more can be added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingSheet(app: ThreadsApp, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val rows by app.accounts.state.collectAsState()
    var unfollowing by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = 8.dp)) {
            item(key = "title") { Text(pluginString(Strings.following), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
            items(ThreadsAccounts.accounts(rows), key = { it.handle }) { account ->
                ListItem(
                    leadingContent = { PluginAvatar(account.avatarUrl, account.name, account.handle, 40.dp) },
                    headlineContent = { Text("@" + account.handle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { unfollowing = account.handle }) {
                            Icon(ThreadsIcons.PersonRemove, contentDescription = pluginString(Strings.unfollow))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onDismiss(); app.openProfile(account.handle) },
                )
            }
            item(key = "add") { SheetRow(ThreadsIcons.PersonAdd, pluginString(Strings.add_account)) { onDismiss(); onAdd() } }
        }
    }
    unfollowing?.let { handle -> UnfollowDialog(handle, onConfirm = { app.unfollow(handle) }) { unfollowing = null } }
}

/** "Unfollow @handle?", as XTA asks before an account leaves the tab. */
@Composable
internal fun UnfollowDialog(handle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.unfollow)) },
        text = { Text("@$handle") },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(pluginString(Strings.unfollow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/**
 * What the tab shows: all posts, only media or only links, with or without
 * replies and reposts, the words and the accounts muted here, and the way
 * back for the posts hidden one by one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(app: ThreadsApp, onDismiss: () -> Unit) {
    val options by app.options.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(pluginString(Strings.filters), style = MaterialTheme.typography.titleLarge)
            val contents = listOf(ThreadsFeedContent.ALL to Strings.all, ThreadsFeedContent.MEDIA to Strings.media, ThreadsFeedContent.LINKS to Strings.links)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                contents.forEachIndexed { index, (content, label) ->
                    SegmentedButton(
                        selected = options.content == content,
                        onClick = { app.setOptions(options.copy(content = content)) },
                        shape = SegmentedButtonDefaults.itemShape(index, contents.size),
                        icon = { SegmentIcon(content) },
                    ) { Text(pluginString(label), maxLines = 1) }
                }
            }
            SwitchRow(Strings.hide_replies, options.hideReplies) { app.setOptions(options.copy(hideReplies = it)) }
            SwitchRow(Strings.hide_reposts, options.hideReposts) { app.setOptions(options.copy(hideReposts = it)) }
            MutedWords(options) { app.setOptions(it) }
            MutedAccounts(options.mutedAccounts) { app.muteAccount(it, muted = false) }
            if (options.hiddenPosts.isNotEmpty()) {
                TextButton(onClick = app::showHiddenPosts, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(PostIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(pluginString(Strings.show_hidden_posts), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (options.resettable) {
                TextButton(onClick = { app.setOptions(options.reset()) }) {
                    Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(pluginString(Strings.reset_filters), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SegmentIcon(content: ThreadsFeedContent) {
    when (content) {
        ThreadsFeedContent.ALL -> Unit
        ThreadsFeedContent.MEDIA -> Icon(PostIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
        ThreadsFeedContent.LINKS -> Icon(XIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/**
 * The words muted on this device, as the groups' muted words are written
 * (custom_feed_muted_keywords): a field to add some, and a chip for each,
 * whose cross takes it out. They hide a post in Home and in For you.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MutedWords(options: ThreadsFeedOptions, onChange: (ThreadsFeedOptions) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val add = {
        if (typed.isNotBlank()) onChange(options.muting(typed))
        typed = ""
    }
    val hint = pluginString(Strings.muted_words_hint)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(pluginString(Strings.muted_words), style = MaterialTheme.typography.titleSmall)
        Text(pluginString(Strings.muted_words_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                placeholder = { Text(hint) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = add) { Icon(Icons.Filled.Add, contentDescription = hint, tint = MaterialTheme.colorScheme.primary) }
        }
        if (options.mutedWords.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.mutedWords.forEach { word -> MutedChip(word) { onChange(options.unmuting(word)) } }
            }
        }
    }
}

/** The accounts muted on this device from a post's sheet, a chip for each, whose cross unmutes it; nothing while none is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MutedAccounts(accounts: List<String>, onUnmute: (String) -> Unit) {
    if (accounts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(pluginString(Strings.muted_accounts), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { handle -> MutedChip("@$handle", pluginString(Strings.unmute_account, "@$handle")) { onUnmute(handle) } }
        }
    }
}

@Composable
private fun MutedChip(word: String, removes: String = pluginString(Strings.delete), onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = removes, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
internal fun SwitchRow(label: PluginText, on: Boolean, detail: PluginText? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(pluginString(label)) },
        supportingContent = detail?.let { { Text(pluginString(it)) } },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!on) },
    )
}

/**
 * The authors of the feed not followed yet, the reposted ones first
 * (plugin_feed_people.dart): folded under its title in Home's strip, each
 * opening its profile, with Follow beside it.
 */
@Composable
internal fun PeopleStrip(app: ThreadsApp, people: List<ThreadsPerson>, startOpen: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { BadgedBox(badge = { Badge { Text("${people.size}") } }) { Icon(PostIcons.People, contentDescription = null, modifier = Modifier.size(20.dp)) } },
            headlineContent = { Text(pluginString(Strings.from_feed), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { open = !open },
        )
        if (open) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person -> PersonChip(app, person) }
            }
        }
    }
}

/** Accounts to follow before following anyone: each opens its profile, with Follow beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StarterPeople(app: ThreadsApp, people: List<ThreadsPerson>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(pluginString(Strings.try_public), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { person -> PersonChip(app, person) }
        }
    }
}

@Composable
private fun PersonChip(app: ThreadsApp, person: ThreadsPerson) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { app.openProfile(person.handle) },
            label = { Text("@" + person.handle) },
            leadingIcon = { PluginAvatar(person.avatarUrl, person.name, person.handle, 20.dp) },
        )
        TextButton(onClick = { app.follow(ThreadsAccount(person.handle, person.name, person.avatarUrl)) }) {
            Text(pluginString(Strings.follow))
        }
    }
}

/**
 * Asks for a handle, takes "@zuck", "zuck" or a profile link alike, or
 * several split by spaces, commas or lines, and follows them.
 */
@Composable
internal fun AddAccountDialog(app: ThreadsApp, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val submit = {
        val handles = ThreadsAccounts.handlesIn(text)
        if (handles == null) {
            invalid = true
        } else {
            onDismiss()
            app.followHandles(handles)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.add_account)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                maxLines = 4,
                prefix = { Text("@") },
                placeholder = { Text(pluginString(Strings.account_hint)) },
                isError = invalid,
                supportingText = { Text(pluginString(if (invalid) Strings.invalid_handle else Strings.add_accounts_hint)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text(pluginString(Strings.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
