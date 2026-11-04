package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** The tags pinned as saved searches: a tap searches one; Edit reorders them and takes them off. */
@Composable
internal fun PixivFavoriteTagsScreen() {
    val runtime = LocalPixiv.current
    val tags = PixivFavoriteTags.of(pixivSettings())
    var editing by rememberSaveable { mutableStateOf(false) }
    PluginScaffold(
        title = pluginString(Strings.search_favorite_tags),
        onBack = runtime.host::back,
        actions = {
            if (tags.isNotEmpty()) {
                IconButton(onClick = { editing = !editing }) {
                    Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, pluginString(if (editing) Strings.search_favorite_tags_done else Strings.search_favorite_tags_edit))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tags.isEmpty()) PixivEmpty(pluginString(Strings.search_favorite_tags_empty), icon = PixivIcons.Label) else FavoriteTagList(tags, editing)
        }
    }
}

@Composable
private fun FavoriteTagList(tags: List<PixivTag>, editing: Boolean) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val language = currentLanguage()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (editing) item(key = "hint") { Text(pluginString(Strings.search_favorite_tags_edit_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
        itemsIndexed(tags, key = { _, tag -> tag.name }) { index, tag ->
            ListItem(
                headlineContent = { Text("#" + tag.name) },
                supportingContent = tag.translation?.let { { Text(it) } },
                leadingContent = { Icon(PixivIcons.Label, null) },
                trailingContent = if (!editing) null else {
                    {
                        Row {
                            IconButton(onClick = { runtime.settings.update { PixivFavoriteTags.moved(it, index, index - 1) } }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                            IconButton(onClick = { runtime.settings.update { PixivFavoriteTags.moved(it, index, index + 1) } }, enabled = index < tags.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                            IconButton(onClick = {
                                val remove = Strings.search_favorite_remove.get(language)
                                sheets.ask.ask(remove, remove) { runtime.settings.update { PixivFavoriteTags.removed(it, tag.name) } }
                            }) { Icon(Icons.Outlined.Delete, pluginString(Strings.search_favorite_remove)) }
                        }
                    }
                },
                modifier = Modifier.clickable(enabled = !editing) { runtime.openTagSearch(tag.name) },
            )
        }
    }
}

/** One muted thing as a chip: a tap unmutes it after asking, the cross at once, a long press copies what it can. */
private class MuteChip(val label: String, val copy: String?, val unmute: () -> Unit)

/**
 * Everything muted on this device (pixiv_settings_mute.dart): the tags with
 * the field to mute one (a pattern too, written r'…'), then the creators,
 * works, comments and novels muted.
 */
@Composable
internal fun PixivMuteScreen() {
    val runtime = LocalPixiv.current
    val mutes = pixivMutes()
    PluginScaffold(title = pluginString(Strings.mute_settings), onBack = runtime.host::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(pluginString(Strings.muted_section), style = MaterialTheme.typography.titleSmall)
            if (mutes.isEmpty) Text(pluginString(Strings.muted_empty), modifier = Modifier.padding(top = 8.dp))
            MuteGroup(pluginString(Strings.mute_tags), PixivIcons.Label, mutes.tags.sorted().map { MuteChip(PixivMuteTags.label(it), it) { runtime.mutes.unmuteTag(it) } }) { TagField() }
            MuteGroup(pluginString(Strings.mute_users), GroupIcons.PersonRemove, mutes.authorIds.sorted().map { id ->
                MuteChip(mutes.authorNames[id]?.ifEmpty { null }?.let { "$it · $id" } ?: "$id", null) { runtime.mutes.unmuteAuthor(id) }
            })
            MuteGroup(pluginString(Strings.mute_works), PostIcons.VisibilityOff, mutes.illustIds.sorted().map { id -> MuteChip("$id", null) { runtime.mutes.unmuteIllust(id) } })
            MuteGroup(pluginString(Strings.mute_comments), PixivIcons.VolumeOff, mutes.commentIds.sorted().map { id -> MuteChip("$id", null) { runtime.mutes.unmuteComment(id) } })
            MuteGroup(pluginString(Strings.mute_novels), PixivIcons.MenuBook, mutes.novelIds.sorted().map { id -> MuteChip("$id", null) { runtime.mutes.unmuteNovel(id) } })
        }
    }
}

@Composable
private fun TagField() {
    val runtime = LocalPixiv.current
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val add = {
        val entry = text.trim()
        if (entry.isNotEmpty()) {
            invalid = !runtime.mutes.muteTag(entry)
            if (!invalid) text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        placeholder = { Text(pluginString(Strings.mute_tag_hint)) },
        supportingText = { Text(pluginString(if (invalid) Strings.mute_tag_invalid else Strings.mute_tag_help)) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = { IconButton(onClick = add) { Icon(Icons.Filled.Add, pluginString(Strings.mute_tag_add)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A titled group of muted things, shown once it holds one, or always with a [leading] field. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MuteGroup(title: String, icon: ImageVector, chips: List<MuteChip>, leading: (@Composable () -> Unit)? = null) {
    if (chips.isEmpty() && leading == null) return
    val sheets = LocalPixivSheets.current
    val copy = rememberCopy()
    val language = currentLanguage()
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge)
    }
    leading?.let {
        it()
        if (chips.isNotEmpty()) Spacer(Modifier.height(8.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            val shape = RoundedCornerShape(8.dp)
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clip(shape).combinedClickable(
                    onClick = { sheets.ask.ask(Strings.unmute_question.get(language, chip.label), Strings.unmute.get(language), chip.unmute) },
                    onLongClick = chip.copy?.let { text -> { copy(text, Strings.copied) } },
                ),
            ) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chip.label, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = chip.unmute) { Icon(Icons.Filled.Close, pluginString(Strings.unmute), Modifier.size(18.dp)) }
                }
            }
        }
    }
}
