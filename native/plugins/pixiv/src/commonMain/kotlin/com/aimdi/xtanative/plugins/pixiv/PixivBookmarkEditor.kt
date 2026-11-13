package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkDetail
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException

/**
 * The bookmark editor of a work on Pixiv (pixiv_bookmark_editor.dart): its
 * bookmark read from Pixiv first, then Add bookmark, or Edit bookmark with
 * Remove while it is bookmarked, over the work's title; Private bookmark; its
 * tags with Select all or Clear all, a field adding tags (suggesting the
 * reader's own bookmark tags on Pixiv as they type), the checklist, then
 * Cancel and Bookmark or Save. A write that fails says why here, the sheet
 * left open; Save and Remove wait while any write for the work runs.
 */
@Composable
internal fun BookmarkEditor(sheet: PixivSheet.EditWork, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val illust = sheet.illust
    val haptics = rememberHaptics()
    val marks by runtime.bookmarking.works.state.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var loaded by remember(illust.id) { mutableStateOf<PixivBookmarkDetail?>(null) }
    var failure by remember(illust.id) { mutableStateOf<Throwable?>(null) }
    var draft by remember(illust.id) { mutableStateOf<PixivBookmarkDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(illust.id, attempt) {
        failure = null
        try {
            val detail = runtime.bookmarking.detail(illust)
            draft = PixivBookmarkDraft.fromDetail(detail, runtime.bookmarking.defaultRestrict, runtime.bookmarking.autoTags(illust.tags.map { it.name }))
            loaded = detail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        }
    }
    val known = rememberKnownTags()
    val write: (suspend (PixivBookmarking) -> PixivBookmarkOutcome?) -> Unit = { edit ->
        saving = true
        saveError = null
        sheets.editBookmark(sheet, haptics, edit) { error ->
            saving = false
            if (error == null) sheets.close() else saveError = error
        }
    }
    PixivSheetFrame(sheets) {
        val current = draft
        val detail = loaded
        when {
            current != null && detail != null -> {
                val writing = saving || marks.isBusy(illust.id)
                EditorTitle(illust.title, bookmarked = detail.isBookmarked, enabled = !writing) { write { it.remove(illust) } }
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.padding(start = 12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        SwitchRow(pluginString(Strings.bookmark_private), pluginString(Strings.bookmark_private_hint), current.private) { if (!saving) draft = current.copy(private = it) }
                    }
                }
                TagsHeader(current) { draft = current.allToggled() }
                TagField(current, known) { draft = it }
                if (current.tags.isEmpty()) {
                    Text(
                        pluginString(Strings.bookmark_tags_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                current.tags.forEach { tag -> TagCheck(tag, tag in current.checked) { draft = current.toggled(tag) } }
                saveError?.let { Text(pixivErrorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = sheets::close, enabled = !saving) { Text(pluginString(Strings.cancel)) }
                    Button(onClick = { write { it.save(illust, current.restrict, current.saved()) } }, enabled = !writing) {
                        Text(pluginString(if (detail.isBookmarked) Strings.save else Strings.bookmark))
                    }
                }
            }
            failure != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pixivErrorText(failure))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { attempt++ }) { Text(pluginString(Strings.retry)) }
            }
            else -> LoadingRow(Modifier.heightIn(min = 200.dp))
        }
    }
}

/** Add bookmark for a work not bookmarked yet, else Edit bookmark with Remove (pixiv_bookmark_editor.dart's title row). */
@Composable
private fun EditorTitle(title: String, bookmarked: Boolean, enabled: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pluginString(if (bookmarked) Strings.bookmark_edit else Strings.bookmark_add), style = MaterialTheme.typography.titleMedium)
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (bookmarked) {
            TextButton(onClick = onRemove, enabled = enabled) { Text(pluginString(Strings.unbookmark), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Tags, with Select all or Clear all once there are tags to check. */
@Composable
private fun TagsHeader(draft: PixivBookmarkDraft, onToggleAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluginString(Strings.bookmark_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (draft.tags.isNotEmpty()) {
            TextButton(onClick = onToggleAll) { Text(pluginString(if (draft.allChecked) Strings.bookmark_clear_all else Strings.bookmark_select_all)) }
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

/** The reader's bookmark tags on Pixiv to suggest, public and private (up to ten pages each), once read; a read that fails suggests none. */
@Composable
private fun rememberKnownTags(): List<String> {
    val runtime = LocalPixiv.current
    var known by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        if (runtime.signedIn) {
            known = listOf(PixivRestrict.PUBLIC, PixivRestrict.PRIVATE).flatMap { restrict -> runCatching { runtime.bookmarkApi.allTags(restrict) }.getOrDefault(emptyList()) }
                .map { it.name }.distinct()
        }
    }
    return known
}
