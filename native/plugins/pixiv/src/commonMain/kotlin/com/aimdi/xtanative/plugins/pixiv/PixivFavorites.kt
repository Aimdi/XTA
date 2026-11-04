package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkTag
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * Favorites narrowed to one bookmark tag, as XTA's picker: All and
 * Uncategorized on top, then the reader's tags with their counts, public and
 * private on Pixiv or those of this device; typing narrows them, and Enter
 * (or "Use …") takes what was typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkTagPicker(source: PixivFavoritesSource, tag: String?, onPick: (PixivFavoritesSource, String?) -> Unit, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val sources = if (source == PixivFavoritesSource.DEVICE) listOf(source) else listOf(PixivFavoritesSource.PUBLIC, PixivFavoritesSource.PRIVATE)
    var shown by remember { mutableIntStateOf(sources.indexOf(source).coerceAtLeast(0)) }
    val pick: (String?) -> Unit = { picked ->
        onPick(sources[shown], picked)
        onClose()
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Text(pluginString(Strings.bookmark_tag_filter), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(pluginString(Strings.bookmark_tag_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { query.trim().takeIf { it.isNotEmpty() }?.let(pick) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (sources.size > 1) {
                PrimaryTabRow(selectedTabIndex = shown) {
                    Tab(selected = shown == 0, onClick = { shown = 0 }, text = { Text(pluginString(Strings.bookmarks_public)) })
                    Tab(selected = shown == 1, onClick = { shown = 1 }, text = { Text(pluginString(Strings.bookmarks_private)) })
                }
            }
            val selected = if (sources[shown] == source) tag else ""
            if (sources[shown] == PixivFavoritesSource.DEVICE) DeviceTagList(query.trim(), selected, pick) else PixivTagList(sources[shown], query.trim(), selected, pick)
        }
    }
}

/** One of the account's tag lists on Pixiv, read a page at a time, more while a search finds too few. */
@Composable
private fun PixivTagList(source: PixivFavoritesSource, query: String, selected: String?, onPick: (String?) -> Unit) {
    val runtime = LocalPixiv.current
    val scope = rememberCoroutineScope()
    val list = remember(source) { PixivList<PixivBookmarkTag>(scope, { it.name }, { next -> runtime.bookmarkApi.tags(source.api, next) }).also { it.ensure() } }
    val state by list.state.collectAsState()
    val tags = state.items.map { it.name to it.count }
    val matches = PixivBookmarkDraft.matches(tags.map { it.first }, query)
    LaunchedEffect(query, state.items.size, state.hasMore) {
        if (query.isNotEmpty() && state.hasMore && matches.size < PixivBookmarkDraft.SUGGESTED) list.more()
    }
    TagRows(query, tags, selected, onPick, readMore = { if (query.isEmpty() && state.hasMore) list.more() }) {
        when {
            state.waiting || state.loadingMore -> item(key = "loading") { LoadingRow() }
            state.error != null && state.items.isEmpty() -> item(key = "error") {
                ListItem(headlineContent = { Text(pixivErrorText(state.error)) }, trailingContent = { TextButton(onClick = list::refresh) { Text(pluginString(Strings.retry)) } })
            }
            state.loaded && state.items.isEmpty() -> item(key = "none") { NoTags() }
        }
    }
}

/** The tags of this device's work bookmarks, the most used first. */
@Composable
private fun DeviceTagList(query: String, selected: String?, onPick: (String?) -> Unit) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    val tags = PixivDeviceBookmarks.tagCounts(marks.orEmpty(), PixivKind.WORK).map { (name, count) -> name to count.toLong() }
    TagRows(query, tags, selected, onPick, readMore = {}) { if (tags.isEmpty()) item(key = "none") { NoTags() } }
}

@Composable
private fun TagRows(query: String, tags: List<Pair<String, Long>>, selected: String?, onPick: (String?) -> Unit, readMore: () -> Unit, footer: LazyListScope.() -> Unit) {
    val scroll = rememberLazyListState()
    val nearEnd by remember(scroll) {
        derivedStateOf { scroll.layoutInfo.let { info -> info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8 } }
    }
    LaunchedEffect(nearEnd, tags.size) { if (nearEnd) readMore() }
    LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (query.isEmpty()) {
            item(key = "all") { TagRow(PixivIcons.Collections, null, null, selected, onPick) }
            item(key = "untagged") { TagRow(PixivIcons.Label, PixivDeviceBookmarks.UNTAGGED, null, selected, onPick) }
            item(key = "rule") { HorizontalDivider() }
            items(tags, key = { "tag-" + it.first }) { (name, count) -> TagRow(PixivIcons.Tag, name, count, selected, onPick) }
            footer()
        } else {
            val counts = tags.toMap()
            val matches = PixivBookmarkDraft.matches(tags.map { it.first }, query)
            items(matches, key = { "match-$it" }) { name -> TagRow(PixivIcons.Tag, name, counts[name], selected, onPick) }
            if (query !in matches) {
                item(key = "use") {
                    ListItem(
                        headlineContent = { Text(pluginString(Strings.bookmark_tag_use, query)) },
                        leadingContent = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.clickable { onPick(query) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagRow(icon: ImageVector, tag: String?, count: Long?, selected: String?, onPick: (String?) -> Unit) {
    val chosen = selected == tag
    ListItem(
        headlineContent = { Text(tagLabel(tag)) },
        leadingContent = { Icon(icon, null) },
        trailingContent = count?.let { { Text(countText(it)) } },
        colors = if (chosen) ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary, leadingIconColor = MaterialTheme.colorScheme.primary) else ListItemDefaults.colors(),
        modifier = Modifier.clickable { onPick(tag) },
    )
}

@Composable
private fun NoTags() {
    ListItem(headlineContent = { Text(pluginString(Strings.bookmark_tags_none), color = MaterialTheme.colorScheme.onSurfaceVariant) })
}

/**
 * What this device keeps bookmarked, newest first: works in the grid (under
 * [tag] when one is picked) or novels in their list. Pixiv is never asked.
 */
@Composable
internal fun DeviceBookmarks(kind: PixivKind, tag: String?, padding: PaddingValues, toTop: Int) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    val under = PixivDeviceBookmarks.under(marks.orEmpty(), kind, tag)
    if (kind == PixivKind.NOVEL) {
        val novels = rememberKept("pixiv-device-novels") { scope -> PixivLocalList<PixivDeviceBookmark, PixivNovel>(scope, { it.id }) { it.kept.toNovel() } }
        LaunchedEffect(under) { novels.show(under) }
        PixivNovelFeed(novels.list, pluginString(Strings.device_novel_bookmarks_empty), padding, toTop)
    } else {
        val works = rememberKept("pixiv-device-works") { scope -> PixivLocalList<PixivDeviceBookmark, PixivIllust>(scope, { it.id }) { it.kept.toIllust() } }
        LaunchedEffect(under) { works.show(under) }
        PixivWorksGrid(works.list, pluginString(if (tag == null) Strings.device_bookmarks_empty else Strings.bookmark_tag_empty), padding = padding, toTop = toTop, hideMuted = false)
    }
}
