package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** How many columns a works grid [width] × [height] dp gets, as the reader's settings for the orientation and the text size ask. */
@Composable
internal fun gridColumns(width: Float, height: Float): Int {
    val settings = pixivSettings()
    val density = LocalDensity.current
    return PixivLayout.columns(width, PixivLayout.picked(settings, landscape = width > height), largeText = density.fontScale > 1.4f)
}

/**
 * A paged works list as the sections show it: tiles in staggered columns
 * without what the reader muted, the next page asked for well before the end,
 * pull to refresh, and the list's loading, failure and emptiness in its place.
 * [header] scrolls above the works, each item across every column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PixivWorksGrid(
    list: PixivList<PixivIllust>,
    empty: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
    toTop: Int = 0,
    refresh: Int = 0,
    mutes: (PixivMutes) -> PixivMutes = { it },
    hideMuted: Boolean = true,
    tile: (@Composable (works: List<PixivIllust>, index: Int) -> Unit)? = null,
    header: LazyStaggeredGridScope.() -> Unit = {},
) {
    val runtime = LocalPixiv.current
    val state by list.state.collectAsState()
    val current = pixivMutes()
    val shown = remember(state.items, current, hideMuted) { if (hideMuted) mutes(current).filter(state.items) else state.items }
    val grid = rememberLazyStaggeredGridState()
    LaunchedEffect(list) { list.ensure() }
    GridToTop(grid, toTop)
    RefreshesOn(refresh) { list.refresh() }
    ReadsAhead(grid, state) { list.more() }
    PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = list::refresh, modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(gridColumns(maxWidth.value, maxHeight.value)),
                state = grid,
                contentPadding = padding.plus(4.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                header()
                pagedState(state, shown.isEmpty(), empty, onRetry = list::refresh)
                itemsIndexed(shown, key = { _, illust -> "work-" + illust.id }) { index, illust ->
                    if (tile != null) tile(shown, index) else WorkTile(illust, onOpen = { runtime.openWork(shown, index, list) })
                }
                pagedFooter(state, list::retryMore)
            }
        }
    }
}

/** A list's first load, failure or emptiness, across the whole grid. */
internal fun <T> LazyStaggeredGridScope.pagedState(state: PixivListState<T>, nothingShown: Boolean, empty: String, onRetry: () -> Unit) {
    when {
        state.items.isEmpty() && state.error != null -> item(key = "state", span = StaggeredGridItemSpan.FullLine) { PixivProblem(state.error, onRetry) }
        state.waiting -> item(key = "state", span = StaggeredGridItemSpan.FullLine) { LoadingRow() }
        nothingShown && state.loaded && !state.loading -> item(key = "state", span = StaggeredGridItemSpan.FullLine) { PixivEmpty(empty, onRetry) }
    }
}

/** The next page's spinner, or Retry after it failed. */
internal fun <T> LazyStaggeredGridScope.pagedFooter(state: PixivListState<T>, onRetry: () -> Unit) {
    item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
        when {
            state.loadingMore -> LoadingRow()
            state.moreFailed -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) }
            }
        }
    }
}

/** Asks for [more] once the end of the grid comes near, once per page. */
@Composable
internal fun <T> ReadsAhead(grid: LazyStaggeredGridState, state: PixivListState<T>, more: () -> Unit) {
    val nearEnd by remember(grid) {
        derivedStateOf {
            val info = grid.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.hasMore) { if (nearEnd && state.hasMore) more() }
}

/** Scrolls [grid] to the top each time [toTop] grows past what it last answered. */
@Composable
internal fun GridToTop(grid: LazyStaggeredGridState, toTop: Int) {
    var answered by rememberSaveable { mutableIntStateOf(toTop) }
    LaunchedEffect(toTop) {
        if (toTop > answered) {
            answered = toTop
            grid.animateScrollToItem(0)
        }
    }
}

private fun PaddingValues.plus(extra: Dp): PaddingValues = PaddingValues(
    start = calculateLeftPadding(LayoutDirection.Ltr) + extra,
    end = calculateRightPadding(LayoutDirection.Ltr) + extra,
    top = calculateTopPadding() + extra,
    bottom = calculateBottomPadding() + extra,
)

/**
 * One cell of a works grid, picture first: its page count, ugoira, R-18 and
 * AI marks over it, a check once a page is saved here and the heart that
 * bookmarks it on this device; title, series, creator and Pixiv's bookmark
 * count under it. A long press opens the work's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkTile(illust: PixivIllust, onOpen: () -> Unit, onLongPress: (() -> Unit)? = null) {
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    val url = remember(illust, settings[PixivPrefs.qualityFeed]) { PixivImages.tileUrl(illust, PixivQualitySlot.FEED.of(settings)) }
    Column(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress ?: { sheets.show(PixivSheet.WorkActions(illust)) }),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.toFloat().coerceIn(0.45f, 1.6f))) {
            PixivPicture(url, Modifier.fillMaxSize(), contentDescription = illust.title)
            TileBadges(illust, settings[PixivPrefs.aiBadge])
            SavedMark(illust, Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 48.dp))
            TileHeart(illust, Modifier.align(Alignment.BottomEnd))
        }
        TileCaption(illust)
    }
}

@Composable
private fun BoxScope.TileBadges(illust: PixivIllust, aiBadge: Boolean) {
    if (illust.pageCount > 1) PixivBadge("${illust.pageCount}", PixivIcons.Collections, Modifier.align(Alignment.TopEnd).padding(6.dp))
    if (illust.isUgoira) PixivBadge(pluginString(Strings.ugoira), PixivIcons.PlayCircle, Modifier.align(Alignment.TopStart).padding(6.dp))
    val ai = illust.isAi && aiBadge
    if (illust.isR18 || ai) {
        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (illust.isR18) PixivBadge(pluginString(Strings.r18))
            if (ai) PixivBadge(pluginString(Strings.ai), GroupIcons.AutoAwesome)
        }
    }
}

/** A small check once any page of the work is saved on this device. */
@Composable
private fun SavedMark(illust: PixivIllust, modifier: Modifier) {
    val saved = PixivSavedPages.savedCount(PixivSavedPages.of(pixivSettings()), illust)
    if (saved == 0) return
    Box(modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(4.dp)) {
        Icon(Icons.Filled.Done, pluginString(Strings.saved_badge), tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** The round heart over a tile: a tap bookmarks the work on this device or takes the bookmark away, a long press opens the editor. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileHeart(illust: PixivIllust, modifier: Modifier) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val marks by runtime.bookmarks.state.collectAsState()
    val bookmarked = marks.orEmpty().any { it.kind == PixivKind.WORK && it.kept.id == illust.id }
    Box(
        modifier.size(48.dp).clip(CircleShape).combinedClickable(
            onClick = { sheets.toggleBookmark(illust, haptics) },
            onLongClick = {
                haptics.medium()
                sheets.show(PixivSheet.EditWork(illust))
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(
                if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                pluginString(if (bookmarked) Strings.unbookmark else Strings.bookmark),
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Title, series, creator and Pixiv's bookmark count, filled when the reader bookmarked it on Pixiv. */
@Composable
private fun TileCaption(illust: PixivIllust) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
        if (illust.title.isNotEmpty()) {
            Text(illust.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        illust.series?.takeIf { it.title.isNotEmpty() }?.let { SeriesLink(it, dense = true) }
        Spacer(Modifier.size(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                illust.userName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (illust.isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (illust.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(countText(illust.totalBookmarks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A grid's spinner while a part of a screen loads. */
@Composable
internal fun SmallSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
