package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.feed.MuteList
import com.aimdi.xtanative.core.feed.MutedAccount
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.posts_mute_confirm
import com.aimdi.xtanative.ui.generated.resources.settings_delete
import com.aimdi.xtanative.ui.generated.resources.settings_mute_add_hint
import com.aimdi.xtanative.ui.generated.resources.settings_mute_empty
import com.aimdi.xtanative.ui.generated.resources.settings_mute_list
import com.aimdi.xtanative.ui.generated.resources.settings_mute_search
import com.aimdi.xtanative.ui.generated.resources.settings_mute_until
import com.aimdi.xtanative.ui.post.ExpiryChips
import com.aimdi.xtanative.ui.post.MuteExpiries
import com.aimdi.xtanative.ui.post.Mutes
import com.aimdi.xtanative.ui.saved.Keeper
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.jetbrains.compose.resources.stringResource

/** One entry of the mute list as Settings lists it: a word or phrase, or an account. */
internal sealed interface MuteEntry {
    val label: String
    val until: Instant?

    data class Word(val keyword: MutedKeyword) : MuteEntry {
        override val label: String get() = keyword.term
        override val until: Instant? get() = keyword.until
    }

    data class Account(val account: MutedAccount) : MuteEntry {
        override val label: String get() = "@" + account.handle
        override val until: Instant? get() = account.until
    }
}

/** What Settings' mute list shows and does, apart from drawing it. */
internal object MuteEntries {
    /** The entries still in force at [now], accounts first, each kind in the order it was muted, those [query] finds (in any case). */
    fun shown(mutes: MuteList, query: String, now: Instant): List<MuteEntry> {
        val active = mutes.activeAt(now)
        val all = active.accounts.map(MuteEntry::Account) + active.words.map(MuteEntry::Word)
        val needle = query.trim().lowercase()
        return if (needle.isEmpty()) all else all.filter { needle in it.label.lowercase() }
    }

    fun removed(mutes: MuteList, entry: MuteEntry): MuteList = when (entry) {
        is MuteEntry.Word -> mutes.withoutWord(entry.keyword.term)
        is MuteEntry.Account -> mutes.withoutAccount(entry.account)
    }

    /** [entry] muted for [duration] from [now] instead, for good with null. */
    fun retimed(mutes: MuteList, entry: MuteEntry, duration: Duration?, now: Instant): MuteList {
        val until = duration?.let(now::plus)
        return when (entry) {
            is MuteEntry.Word -> mutes.copy(words = mutes.words.map { if (it == entry.keyword) it.copy(until = until) else it })
            is MuteEntry.Account -> mutes.copy(accounts = mutes.accounts.map { if (it == entry.account) it.copy(until = until) else it })
        }
    }
}

/**
 * Settings › Posts › Muted words and accounts: a field that adds a word, a
 * phrase or an @account for the chosen time, a search over what is muted,
 * and each entry, whose time a click changes and whose × takes it out.
 */
@Composable
internal fun MuteListDialog(mutes: MuteList, keeper: Keeper, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableIntStateOf(0) }
    val now = remember(mutes) { Instant.now() }
    val shown = remember(mutes, query, now) { MuteEntries.shown(mutes, query, now) }
    val add = {
        if (typed.isNotBlank()) {
            val until = MuteExpiries.choices[expiry].first?.let(Instant.now()::plus)
            val text = typed
            Mutes.change(keeper) { it.withTyped(text, until) }
            typed = ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_mute_list)) },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.settings_mute_add_hint)) },
                    trailingIcon = {
                        IconTip(stringResource(Res.string.posts_mute_confirm)) {
                            IconButton(onClick = add, enabled = typed.isNotBlank()) { Icon(Icons.Filled.Add, stringResource(Res.string.posts_mute_confirm)) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.padding(vertical = 8.dp)) { ExpiryChips(expiry) { expiry = it } }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(Res.string.settings_mute_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(
                        stringResource(Res.string.settings_mute_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                        items(shown, key = { (if (it is MuteEntry.Account) "a:" else "w:") + it.label }) { entry ->
                            MuteEntryRow(entry, onRetime = { duration -> Mutes.change(keeper) { MuteEntries.retimed(it, entry, duration, Instant.now()) } }) {
                                Mutes.change(keeper) { MuteEntries.removed(it, entry) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

private val untilFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

/** An entry: what it mutes and until when; a click offers other times, × removes it. */
@Composable
private fun MuteEntryRow(entry: MuteEntry, onRetime: (Duration?) -> Unit, onRemove: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { picking = true }.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.until?.let { until ->
                    Text(
                        stringResource(Res.string.settings_mute_until, untilFormat.format(until.atZone(ZoneId.systemDefault()))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconTip(stringResource(Res.string.settings_delete)) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(Res.string.settings_delete)) }
            }
        }
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
            MuteExpiries.choices.forEach { (duration, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    picking = false
                    onRetime(duration)
                })
            }
        }
    }
}
