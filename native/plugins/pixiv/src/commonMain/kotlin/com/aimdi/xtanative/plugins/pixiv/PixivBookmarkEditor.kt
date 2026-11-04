package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * The bookmark editor, kept on this device: the work's title with Remove
 * while it is bookmarked here, Select all or Clear all, a field adding tags
 * (suggesting the reader's own as they type, from this device's bookmarks
 * and their bookmark tags on Pixiv), the checklist, then Cancel and Save.
 */
@Composable
internal fun BookmarkEditor(
    kind: PixivKind,
    id: Long,
    title: String,
    workTags: List<String>,
    sheets: PixivSheets,
    onSave: (List<String>, PixivHaptics) -> Unit,
) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    val filed = marks.orEmpty().firstOrNull { it.kind == kind && it.kept.id == id }
    val known = rememberKnownTags(kind)
    val haptics = rememberHaptics()
    var draft by remember(id, filed == null) {
        mutableStateOf(PixivBookmarkDraft.of(workTags, filed?.tags, known, runtime.bookmarking.autoTags(workTags)))
    }
    PixivSheetFrame(sheets) {
        EditorTitle(title, removable = filed != null) {
            sheets.close()
            sheets.removeBookmark(kind, id, haptics)
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { draft = draft.allToggled() }) {
                Text(pluginString(if (draft.allChecked) Strings.bookmark_clear_all else Strings.bookmark_select_all))
            }
        }
        TagField(draft, known) { draft = it }
        if (draft.tags.isEmpty()) {
            Text(
                pluginString(Strings.bookmark_tags_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        draft.tags.forEach { tag -> TagCheck(tag, tag in draft.checked) { draft = draft.toggled(tag) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = sheets::close) { Text(pluginString(Strings.cancel)) }
            Button(onClick = {
                sheets.close()
                onSave(draft.saved(), haptics)
            }) { Text(pluginString(Strings.save)) }
        }
    }
}

@Composable
private fun EditorTitle(title: String, removable: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pluginString(Strings.bookmark_edit), style = MaterialTheme.typography.titleMedium)
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (removable) {
            TextButton(onClick = onRemove) { Text(pluginString(Strings.unbookmark), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** The add-tag field; up to eight of the reader's tags holding what is typed show under it. */
@Composable
private fun TagField(draft: PixivBookmarkDraft, known: List<String>, onChange: (PixivBookmarkDraft) -> Unit) {
    OutlinedTextField(
        value = draft.typed,
        onValueChange = { onChange(draft.copy(typed = it)) },
        singleLine = true,
        placeholder = { Text(pluginString(Strings.bookmark_tag_hint)) },
        trailingIcon = {
            IconButton(onClick = { onChange(draft.withTyped()) }) { Icon(Icons.Filled.Add, pluginString(Strings.bookmark_tag_add)) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onChange(draft.withTyped()) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    PixivBookmarkDraft.matches(known, PixivTyping.typedWord(draft.typed)).filterNot { it in draft.tags && it in draft.checked }.forEach { tag ->
        Text(
            "#$tag",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { onChange(draft.copy(typed = PixivTyping.replacedTypedWord(draft.typed, tag)).withTyped()) }
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TagCheck(tag: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text("#$tag", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The reader's bookmark tags to suggest: those of this device's bookmarks of
 * [kind], then, once read, their works' bookmark tags on Pixiv (public and
 * private, up to ten pages each); a read that fails suggests what is here.
 */
@Composable
private fun rememberKnownTags(kind: PixivKind): List<String> {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    var pixiv by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(kind) {
        if (kind == PixivKind.WORK && runtime.signedIn) {
            pixiv = listOf("public", "private").flatMap { restrict -> runCatching { runtime.bookmarkApi.allTags(restrict) }.getOrDefault(emptyList()) }
                .map { it.name }
        }
    }
    val here = PixivDeviceBookmarks.tagCounts(marks.orEmpty(), kind).map { it.first }
    return (here + pixiv).distinct()
}
