package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.GroupPack
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.groups_add_to_group
import com.aimdi.xtanative.ui.generated.resources.groups_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.groups_choose_emoji
import com.aimdi.xtanative.ui.generated.resources.groups_choose_icon
import com.aimdi.xtanative.ui.generated.resources.groups_choose_icon_number
import com.aimdi.xtanative.ui.generated.resources.groups_deck_pin
import com.aimdi.xtanative.ui.generated.resources.groups_deck_unpin
import com.aimdi.xtanative.ui.generated.resources.groups_delete
import com.aimdi.xtanative.ui.generated.resources.groups_delete_confirm
import com.aimdi.xtanative.ui.generated.resources.groups_in_following
import com.aimdi.xtanative.ui.generated.resources.groups_mark_auto
import com.aimdi.xtanative.ui.generated.resources.groups_mark_emoji
import com.aimdi.xtanative.ui.generated.resources.groups_mark_icon
import com.aimdi.xtanative.ui.generated.resources.groups_mark_label
import com.aimdi.xtanative.ui.generated.resources.groups_mark_nsfw
import com.aimdi.xtanative.ui.generated.resources.groups_merge_into
import com.aimdi.xtanative.ui.generated.resources.groups_name
import com.aimdi.xtanative.ui.generated.resources.groups_nest_inside
import com.aimdi.xtanative.ui.generated.resources.groups_nest_nothing
import com.aimdi.xtanative.ui.generated.resources.groups_no
import com.aimdi.xtanative.ui.generated.resources.groups_no_other_groups
import com.aimdi.xtanative.ui.generated.resources.groups_ok
import com.aimdi.xtanative.ui.generated.resources.groups_pack_export
import com.aimdi.xtanative.ui.generated.resources.groups_pack_exported
import com.aimdi.xtanative.ui.generated.resources.groups_pick_color
import com.aimdi.xtanative.ui.generated.resources.groups_pin
import com.aimdi.xtanative.ui.generated.resources.groups_please_enter_a_name
import com.aimdi.xtanative.ui.generated.resources.groups_search_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_toggle_all
import com.aimdi.xtanative.ui.generated.resources.groups_unmark_nsfw
import com.aimdi.xtanative.ui.generated.resources.groups_unpin
import com.aimdi.xtanative.ui.generated.resources.groups_yes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which pickers the edit sheet has open over it. */
private enum class EditPicker { NONE, COLOR, EMOJI, ICON, MERGE, NEST, DELETE, ADD }

/**
 * Making or editing a group (_groups_edit.dart): its name, colour and mark,
 * its members, and for a group that exists its pin, NSFW mark, deck column,
 * place in Following, pack, merge, nesting and deletion. [groupId] is null
 * for a new group. Nothing changes until OK, except the group actions, which
 * XTA applies at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupEditSheet(
    session: Session,
    rows: List<SubscriptionRow>,
    groupId: String?,
    actions: GroupActions,
    savePack: (fileName: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = session.groups.firstOrNull { it.id == groupId }
    var draft by remember(groupId) { mutableStateOf(GroupEdits.draftOf(session, groupId)) }
    var listMembers by remember(groupId) { mutableStateOf(draft.members) }
    val candidates = remember(rows, listMembers) { GroupEdits.candidates(rows, listMembers) }
    var query by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(EditPicker.NONE) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var nameTouched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun save() {
        nameTouched = true
        if (!draft.canSave) return
        actions.save(draft, candidates.map { it.id })
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight(0.9f).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupMark(draft.name, draft.seedColor, draft.emoji, draft.icon, draft.markStyle, 40.dp)
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = draft.name,
                    onValueChange = {
                        draft = draft.copy(name = it)
                        nameTouched = true
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.groups_name)) },
                    isError = nameTouched && !draft.canSave,
                    supportingText = if (nameTouched && !draft.canSave) ({ Text(stringResource(Res.string.groups_please_enter_a_name)) }) else null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { picker = EditPicker.COLOR }) {
                    Icon(GroupIcons.Palette, stringResource(Res.string.groups_pick_color), tint = groupColor(draft.seedColor))
                }
            }
            Text(stringResource(Res.string.groups_mark_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            MarkStyleRow(draft.shownStyle) { style ->
                when (style) {
                    GroupMarkStyle.EMOJI -> picker = EditPicker.EMOJI
                    GroupMarkStyle.SYMBOL -> picker = EditPicker.ICON
                    else -> draft = draft.automatic()
                }
            }
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                FilledTonalButton(onClick = { picker = EditPicker.ADD }) {
                    Icon(GroupIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.groups_add_to_group), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
                Discreet(GroupIcons.Checklist, Res.string.groups_toggle_all) { draft = draft.toggledAll(rows.map { it.id }) }
                if (existing != null) {
                    GroupActionsRow(existing, session, actions, onMerge = {
                        message = null
                        if (GroupEdits.mergeTargets(session, existing.id).isEmpty()) message = Res.string.groups_no_other_groups else picker = EditPicker.MERGE
                    }, onNest = {
                        message = null
                        if (GroupEdits.nestTargets(session, existing.id).isEmpty() && session.nesting[existing.id] == null) {
                            message = Res.string.groups_no_other_groups
                        } else {
                            picker = EditPicker.NEST
                        }
                    }, onExport = {
                        val pack = GroupActions.packOf(session, draft)
                        savePack(GroupActions.packFileName(System.currentTimeMillis()), GroupPack.encode(pack))
                        message = Res.string.groups_pack_exported
                    })
                }
            }
            if (existing != null) InFollowingRow(existing.id !in session.offFollowing) { actions.setInFollowing(existing.id, it) }
            message?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                placeholder = { Text(stringResource(Res.string.groups_search_subscriptions)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(GroupEdits.visible(candidates, query), key = { it.key }) { row ->
                    MemberCheckRow(row, row.id in draft.members) { draft = draft.toggled(row.id, it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picker = EditPicker.DELETE }, enabled = existing != null) { Text(stringResource(Res.string.groups_delete)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                TextButton(onClick = ::save) { Text(stringResource(Res.string.groups_ok)) }
            }
        }
    }

    when (picker) {
        EditPicker.COLOR -> ColorDialog(draft.name, draft.color, onPick = { draft = draft.copy(color = it) }, onDismiss = { picker = EditPicker.NONE })
        EditPicker.EMOJI -> EmojiDialog(draft.emoji.orEmpty(), onPick = { draft = draft.withEmoji(it) }, onDismiss = { picker = EditPicker.NONE })
        EditPicker.ICON -> IconDialog(onPick = { draft = draft.withIcon(it) }, onDismiss = { picker = EditPicker.NONE })
        EditPicker.MERGE -> existing?.let { group ->
            GroupChoiceSheet(GroupEdits.mergeTargets(session, group.id), current = null, noneLabel = null, onPick = { target ->
                picker = EditPicker.NONE
                if (target != null) {
                    actions.merge(group.id, target)
                    onDismiss()
                }
            }, onDismiss = { picker = EditPicker.NONE })
        }
        EditPicker.NEST -> existing?.let { group ->
            GroupChoiceSheet(GroupEdits.nestTargets(session, group.id), current = session.nesting[group.id], noneLabel = Res.string.groups_nest_nothing, onPick = { parent ->
                picker = EditPicker.NONE
                scope.launch {
                    if (actions.nest(group.id, parent)) onDismiss() else message = Res.string.groups_no_other_groups
                }
            }, onDismiss = { picker = EditPicker.NONE })
        }
        EditPicker.DELETE -> existing?.let { group ->
            AlertDialog(
                onDismissRequest = { picker = EditPicker.NONE },
                title = { Text(stringResource(Res.string.groups_are_you_sure)) },
                text = { Text(stringResource(Res.string.groups_delete_confirm, group.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        picker = EditPicker.NONE
                        actions.delete(group.id)
                        onDismiss()
                    }) { Text(stringResource(Res.string.groups_yes)) }
                },
                dismissButton = { TextButton(onClick = { picker = EditPicker.NONE }) { Text(stringResource(Res.string.groups_no)) } },
            )
        }
        EditPicker.ADD -> AddMemberSheet(actions, onDone = { added ->
            picker = EditPicker.NONE
            if (added.isNotEmpty()) {
                draft = draft.copy(members = draft.members + added)
                listMembers = draft.members
            }
        })
        EditPicker.NONE -> Unit
    }
}

/** Automatic, emoji or icon: the last two ask which one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkStyleRow(selected: Int, onPick: (Int) -> Unit) {
    val styles = listOf(GroupMarkStyle.AUTO to Res.string.groups_mark_auto, GroupMarkStyle.EMOJI to Res.string.groups_mark_emoji, GroupMarkStyle.SYMBOL to Res.string.groups_mark_icon)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        styles.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = selected == style,
                onClick = { onPick(style) },
                shape = SegmentedButtonDefaults.itemShape(index, styles.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

/** The group actions XTA applies at once: pin, NSFW, deck, export, merge and nesting. */
@Composable
private fun GroupActionsRow(group: FollowGroup, session: Session, actions: GroupActions, onMerge: () -> Unit, onNest: () -> Unit, onExport: () -> Unit) {
    Discreet(if (group.pinned) GroupIcons.PushPin else GroupIcons.PushPinOutlined, if (group.pinned) Res.string.groups_unpin else Res.string.groups_pin) {
        actions.setPinned(group.id, !group.pinned)
    }
    Discreet(
        if (group.nsfw) GroupIcons.VisibilityOff else GroupIcons.VisibilityOffOutlined,
        if (group.nsfw) Res.string.groups_unmark_nsfw else Res.string.groups_mark_nsfw,
    ) { actions.setNsfw(group.id, !group.nsfw) }
    val decked = group.id in session.deck
    Discreet(if (decked) GroupIcons.ViewColumn else GroupIcons.ViewColumnOutlined, if (decked) Res.string.groups_deck_unpin else Res.string.groups_deck_pin) {
        actions.toggleDeck(group.id)
    }
    Discreet(GroupIcons.UploadOutlined, Res.string.groups_pack_export, onExport)
    Discreet(GroupIcons.Merge, Res.string.groups_merge_into, onMerge)
    Discreet(GroupIcons.FolderOutlined, Res.string.groups_nest_inside, onNest)
}

/** A small, low-contrast action, as XTA styles the sheet's secondary ones. */
@Composable
private fun Discreet(icon: ImageVector, label: StringResource, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Whether the group's members are read in Following and For you (XTA's home filter). */
@Composable
private fun InFollowingRow(shown: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = shown, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.groups_in_following), modifier = Modifier.weight(1f))
        Switch(checked = shown, onCheckedChange = null)
    }
}

@Composable
private fun MemberCheckRow(row: SubscriptionRow, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubscriptionFace(row, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(
                subscriptionSubtitle(row),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/**
 * The colours a group can be given (group_color_picker.dart): the one made
 * from its name first, as itself, then X's accents and the rest. A tap is the
 * choice; Cancel keeps the colour it had.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(name: String, current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_pick_color)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val auto = stringResource(Res.string.groups_mark_auto)
                Swatch(GroupLook.fallbackColor(name), current == null, auto) {
                    onPick(null)
                    onDismiss()
                }
                GroupLook.COLOR_CHOICES.forEach { color ->
                    Swatch(color, current == color, null) {
                        onPick(color)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun Swatch(color: Long, selected: Boolean, label: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(groupColor(color))
            .let { if (selected) it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else it }
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                if (label != null) contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Icon(GroupIcons.AutoAwesome, contentDescription = null, tint = onGroupColor(color), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmojiDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_choose_emoji)) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.groups_choose_emoji)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onPick(text.trim())
                    onDismiss()
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(text.trim())
                onDismiss()
            }) { Text(stringResource(Res.string.groups_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** XTA's chosen icons, five to a row; each says which of how many it is, since they have no names worth showing. */
@Composable
private fun IconDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val keys = GroupLook.ICON_KEYS.filter { GroupIcons.mark(it) != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_choose_icon)) },
        text = {
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.size(width = 320.dp, height = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(keys) { index, key ->
                    val label = stringResource(Res.string.groups_choose_icon_number, index + 1, keys.size)
                    IconButton(onClick = {
                        onPick(key)
                        onDismiss()
                    }) {
                        Icon(GroupIcons.mark(key)!!, contentDescription = label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * A short list of groups to pick one from: where to merge into, or where to
 * nest, with [noneLabel] as the first row when the answer may be none (null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChoiceSheet(groups: List<FollowGroup>, current: String?, noneLabel: StringResource?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            if (noneLabel != null) {
                item(key = "none") {
                    ChoiceRow(selected = current == null, onClick = { onPick(null) }) {
                        Icon(GroupIcons.North, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text(stringResource(noneLabel), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            items(groups, key = { it.id }) { group ->
                ChoiceRow(selected = current == group.id, onClick = { onPick(group.id) }) {
                    GroupMark(group, 32.dp)
                    Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
