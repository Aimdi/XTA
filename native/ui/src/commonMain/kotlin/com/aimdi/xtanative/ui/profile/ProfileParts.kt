package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_filters
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_basis
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_many
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_one
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_three
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_title
import com.aimdi.xtanative.ui.generated.resources.posts_followed_by_two
import com.aimdi.xtanative.ui.generated.resources.posts_hide_replies
import com.aimdi.xtanative.ui.generated.resources.posts_hide_replies_description
import com.aimdi.xtanative.ui.generated.resources.posts_hide_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_hide_retweets_description
import com.aimdi.xtanative.ui.generated.resources.posts_profile_note_add
import com.aimdi.xtanative.ui.generated.resources.posts_profile_note_hint
import com.aimdi.xtanative.ui.generated.resources.posts_profile_note_save
import com.aimdi.xtanative.ui.generated.resources.posts_profile_note_title
import com.aimdi.xtanative.ui.generated.resources.posts_quiet_account
import com.aimdi.xtanative.ui.generated.resources.posts_quiet_account_description
import com.aimdi.xtanative.ui.generated.resources.posts_quiet_account_off
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostIcons
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the feeds hide of this account (profile_feed_settings.dart): its
 * reposts, its replies to others, and for an account followed here, the
 * most of its posts one group feed load keeps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FeedSettingsSheet(state: ProfileUi, follow: FollowUi, vm: ProfileViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(Res.string.posts_filters), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            SwitchRow(stringResource(Res.string.posts_hide_retweets), stringResource(Res.string.posts_hide_retweets_description), state.repostsHidden, vm::hideReposts)
            SwitchRow(stringResource(Res.string.posts_hide_replies), stringResource(Res.string.posts_hide_replies_description), state.repliesHidden, vm::hideReplies)
            if (follow.followed) {
                Text(stringResource(Res.string.posts_quiet_account), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    stringResource(Res.string.posts_quiet_account_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietAccount.CHOICES.forEach { choice ->
                        FilterChip(
                            selected = choice == follow.maxPosts,
                            onClick = { vm.capPosts(choice) },
                            label = { Text(choice?.toString() ?: stringResource(Res.string.posts_quiet_account_off)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The private note across the header, one line: tapping it opens the editor (profile_note.dart). */
@Composable
internal fun NoteRow(note: String, onEdit: () -> Unit) {
    val empty = note.isEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onEdit).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PostIcons.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            if (empty) stringResource(Res.string.posts_profile_note_add) else note,
            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** The private note's editor: only on this device, never synced; an empty note removes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteSheet(note: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(note) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.posts_profile_note_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.posts_profile_note_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.posts_profile_note_hint)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = { onSave(text) }) { Text(stringResource(Res.string.posts_profile_note_save)) }
            }
        }
    }
}

/** X's "Followed by" line under the counts, from the Following lists already read; nothing until one names this profile. */
@Composable
internal fun FollowedByLine(followedBy: FollowedBy, onOpen: () -> Unit) {
    val label = followedByText(followedBy) ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Box {
            followedBy.followers.take(3).forEachIndexed { index, member ->
                Box(Modifier.offset(x = (index * 16).dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(1.5.dp)) {
                    Avatar(member.avatarUrl, 20.dp)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = (followedBy.followers.take(3).size * 16 + 12).dp),
        )
    }
}

@Composable
private fun followedByText(followedBy: FollowedBy): String? = when (val label = followedBy.label) {
    null -> null
    is FollowedBy.Label.Many -> pluralStringResource(Res.plurals.posts_followed_by_many, label.others, label.first, label.second, label.others)
    is FollowedBy.Label.Names -> when (label.names.size) {
        1 -> stringResource(Res.string.posts_followed_by_one, label.names[0])
        2 -> stringResource(Res.string.posts_followed_by_two, label.names[0], label.names[1])
        else -> stringResource(Res.string.posts_followed_by_three, label.names[0], label.names[1], label.names[2])
    }
}

/** The followed accounts behind the line, and how many of the reader's follows the app could check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowedBySheet(followedBy: FollowedBy, actions: PostActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(Res.string.posts_followed_by_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            followedBy.followers.forEach { member ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        actions.openUser(member.screenName, member.id)
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(member.avatarUrl, 40.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(member.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@" + member.screenName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Text(
                pluralStringResource(Res.plurals.posts_followed_by_basis, followedBy.total, followedBy.checked, followedBy.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
