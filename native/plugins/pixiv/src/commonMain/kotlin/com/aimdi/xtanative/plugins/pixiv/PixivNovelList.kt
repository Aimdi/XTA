package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * A paged list of novels or other rows as the sections show them: the next
 * page asked for before the end, pull to refresh, and the list's loading,
 * failure and emptiness in its place. [header] scrolls above the rows,
 * [divided] draws a hairline under each.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> PixivRows(
    list: PixivList<T>,
    key: (T) -> Any,
    empty: String,
    padding: PaddingValues = PaddingValues(),
    toTop: Int = 0,
    refresh: Int = 0,
    shown: (List<T>) -> List<T> = { it },
    divided: Boolean = true,
    header: LazyListScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val state by list.state.collectAsState()
    val rows = shown(state.items)
    val scroll = rememberLazyListState()
    LaunchedEffect(list) { list.ensure() }
    ListToTop(scroll, toTop)
    RefreshesOn(refresh) { list.refresh() }
    RowsReadAhead(scroll, state) { list.more() }
    PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = list::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scroll, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            header()
            when {
                state.items.isEmpty() && state.error != null -> item(key = "state") { PixivProblem(state.error, list::refresh) }
                state.waiting -> item(key = "state") { FeedSkeleton(count = 4) }
                rows.isEmpty() && state.loaded && !state.loading -> item(key = "state") { PixivEmpty(empty, list::refresh) }
            }
            items(rows, key = { "row-" + key(it) }) { item ->
                row(item)
                if (divided) HorizontalDivider()
            }
            item(key = "footer") {
                when {
                    state.loadingMore -> LoadingRow()
                    state.moreFailed -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = list::retryMore) { Text(pluginString(Strings.retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> RowsReadAhead(scroll: LazyListState, state: PixivListState<T>, more: () -> Unit) {
    val nearEnd by remember(scroll) {
        derivedStateOf {
            val info = scroll.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.hasMore) { if (nearEnd && state.hasMore) more() }
}

/** Scrolls [scroll] to the top each time [toTop] grows past what it last answered. */
@Composable
internal fun ListToTop(scroll: LazyListState, toTop: Int) {
    var answered by rememberSaveable { mutableIntStateOf(toTop) }
    LaunchedEffect(toTop) {
        if (toTop > answered) {
            answered = toTop
            scroll.animateScrollToItem(0)
        }
    }
}

/** A paged novel list under the reader's mutes. */
@Composable
internal fun PixivNovelFeed(list: PixivList<PixivNovel>, empty: String, padding: PaddingValues = PaddingValues(), toTop: Int = 0, refresh: Int = 0, mutes: (PixivMutes) -> PixivMutes = { it }) {
    val current = pixivMutes()
    PixivRows(list, { it.id }, empty, padding, toTop, refresh, shown = { mutes(current).filterNovels(it) }) { NovelCard(it) }
}

/**
 * One novel in a list: cover, title, author, length, ratings, series, tags,
 * and its heart, which bookmarks it on Pixiv. A tap opens the novel, a
 * long press its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelCard(novel: PixivNovel, order: Int? = null, showSeries: Boolean = true, onTap: (() -> Unit)? = null, onLongPress: (() -> Unit)? = null) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = onTap ?: { runtime.openNovel(novel) },
            onLongClick = onLongPress ?: { sheets.show(PixivSheet.NovelActions(novel)) },
        ).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NovelCover(novel.coverUrl)
        NovelDetails(novel, order, showSeries, Modifier.weight(1f))
        NovelHeart(novel)
    }
}

@Composable
private fun NovelDetails(novel: PixivNovel, order: Int?, showSeries: Boolean, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order?.let { Text(pluginString(Strings.novel_chapter_number, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (novel.title.isNotEmpty()) Text(novel.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (novel.user.name.isNotEmpty()) Text(novel.user.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        NovelRatings(novel)
        if (showSeries) novel.series?.takeIf { it.title.isNotEmpty() }?.let { SeriesLink(it, dense = true, novel = true) }
        if (novel.tags.isNotEmpty()) {
            Text(novel.tags.joinToString("  ") { "#" + it.displayName }, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The novel's length and its R-18, R-18G and AI labels. */
@Composable
private fun NovelRatings(novel: PixivNovel) {
    val aiBadge = pixivSettings()[PixivPrefs.aiBadge]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (novel.textLength > 0) {
            Text(
                pluginPlural(Strings.novel_characters, novel.textLength.toInt(), novel.textLength.toInt(), countText(novel.textLength)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (novel.isR18) Rating(pluginString(if (novel.isR18G) Strings.r18g else Strings.r18), ai = false)
        if (novel.isAi && aiBadge) Rating(pluginString(Strings.ai), ai = true)
    }
}

@Composable
private fun Rating(label: String, ai: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (ai) scheme.onTertiaryContainer else scheme.onErrorContainer,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (ai) scheme.tertiaryContainer else scheme.errorContainer).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A novel's cover at card size, or a book on a plain ground when Pixiv sent none. */
@Composable
internal fun NovelCover(url: String?, width: Dp = 80.dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.size(width, width * 4 / 3).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url == null) Icon(PixivIcons.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) else PixivPicture(url, Modifier.fillMaxSize())
    }
}

/** A novel's heart: a tap bookmarks it on Pixiv or takes the bookmark away, a long press bookmarks it privately; Pixiv's count under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelHeart(novel: PixivNovel) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val marks by runtime.bookmarking.novels.state.collectAsState()
    val bookmarked = marks.on(novel.id, novel.isBookmarked)
    val busy = marks.isBusy(novel.id)
    Column(
        Modifier.width(48.dp).clip(CircleShape).combinedClickable(
            enabled = !busy,
            onLongClickLabel = pluginString(Strings.novel_bookmark_private),
            onClick = { sheets.toggleBookmark(novel, haptics) },
            onLongClick = {
                haptics.medium()
                sheets.bookmarkPrivately(novel, haptics)
            },
        ).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            pluginString(if (bookmarked) Strings.unbookmark else Strings.bookmark),
            tint = (if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).let { if (busy) it.copy(alpha = 0.5f) else it },
        )
        Spacer(Modifier.size(2.dp))
        Text(countText(marks.count(novel.id, novel.isBookmarked, novel.totalBookmarks)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
