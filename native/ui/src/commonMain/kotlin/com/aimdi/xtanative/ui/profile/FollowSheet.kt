package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.follow_create
import com.aimdi.xtanative.ui.generated.resources.follow_group_name
import com.aimdi.xtanative.ui.generated.resources.follow_groups
import com.aimdi.xtanative.ui.generated.resources.follow_here_only
import com.aimdi.xtanative.ui.generated.resources.follow_in_feed
import com.aimdi.xtanative.ui.generated.resources.follow_new_group
import com.aimdi.xtanative.ui.generated.resources.follow_unfollow
import com.aimdi.xtanative.ui.generated.resources.posts_follow
import com.aimdi.xtanative.ui.generated.resources.posts_followed
import com.aimdi.xtanative.ui.post.PostIcons
import org.jetbrains.compose.resources.stringResource

/** [FollowButton] for an account whose place in the Following feed is not known: drawn as in it. */
@Composable
fun FollowButton(followed: Boolean, onFollow: () -> Unit, onManage: () -> Unit) = FollowButton(followed, inFeed = true, onFollow, onManage)

/**
 * Follow, or Following: the latter opens [FollowSheet]. Both stay in XTA
 * Native. As XTA's follow face, its icon says what the follow is: a check,
 * or the eye struck through for an account kept out of the Following feed.
 */
@Composable
fun FollowButton(followed: Boolean, inFeed: Boolean, onFollow: () -> Unit, onManage: () -> Unit) {
    val padding = PaddingValues(start = 12.dp, end = 10.dp)
    if (followed) {
        OutlinedButton(
            onClick = onManage,
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        ) {
            Icon(if (inFeed) Icons.Filled.Check else PostIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.posts_followed), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
    } else {
        Button(
            onClick = onFollow,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.posts_follow), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * What following an account here means: the Following feed, its groups, or
 * no longer. For an account not followed yet, only its groups: filing it in
 * one follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSheet(
    screenName: String,
    follow: FollowUi,
    onInFeed: (Boolean) -> Unit,
    onGroup: (String, Boolean) -> Unit,
    onNewGroup: (String) -> Unit,
    onUnfollow: () -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("@$screenName", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(Res.string.follow_here_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp),
            )
            if (follow.followed) {
                ToggleRow(stringResource(Res.string.follow_in_feed), follow.inFeed, Role.Switch, onInFeed) { checked ->
                    Switch(checked = checked, onCheckedChange = null)
                }
                Hairline(Modifier.padding(vertical = 4.dp))
            }
            Text(
                stringResource(Res.string.follow_groups),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            follow.groups.forEach { group ->
                val name = listOfNotNull(group.emoji, group.name).joinToString(" ")
                ToggleRow(name, group.member, Role.Checkbox, { onGroup(group.id, it) }) { checked ->
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
            TextButton(onClick = { naming = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.follow_new_group), modifier = Modifier.padding(start = 8.dp))
            }
            if (follow.followed) {
                Hairline(Modifier.padding(vertical = 4.dp))
                TextButton(
                    onClick = {
                        onUnfollow()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(Res.string.follow_unfollow))
                }
            }
        }
    }
    if (naming) {
        NewGroupDialog(
            onCreate = { name ->
                onNewGroup(name)
                naming = false
            },
            onDismiss = { naming = false },
        )
    }
}

/** A whole row that switches [checked], as one control for TalkBack. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, role: Role, onChange: (Boolean) -> Unit, control: @Composable (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = role, onValueChange = onChange)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        control(checked)
    }
}

@Composable
private fun NewGroupDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.follow_new_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.follow_group_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.follow_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
