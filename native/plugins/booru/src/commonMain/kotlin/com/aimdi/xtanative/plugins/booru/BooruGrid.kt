package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlin.math.ceil
import kotlin.math.max

/**
 * [state]'s posts in a staggered grid (booru_grid.dart), as many columns as
 * the reader set or the width fits, reading the next page as its end comes
 * into view; under it, a spinner while that page loads, a row with Retry when
 * it failed, and a note once the feed has nothing more. A post opens the
 * viewer, which swipes on into what [feed] loads next.
 */
@Composable
internal fun PostGrid(
    app: BooruApp,
    state: BooruFeedState,
    feed: BooruFeed,
    grid: LazyStaggeredGridState,
    padding: PaddingValues,
    isNew: ((BooruPost) -> Boolean)?,
    search: BooruSearch?,
) {
    val settings by app.settings.collectAsState()
    val display = remember(settings) { BooruConfig.display(settings ?: Settings()) }
    val largeText = LocalDensity.current.fontScale > 1.4f
    ReadsMoreNearEnd(grid, state) { feed.loadMore() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val width = maxWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction) - 16.dp
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(display.columnsFor(width.value, largeText)),
            state = grid,
            contentPadding = PaddingValues(
                start = 8.dp + padding.calculateStartPadding(direction),
                end = 8.dp + padding.calculateEndPadding(direction),
                top = 8.dp + padding.calculateTopPadding(),
                bottom = 8.dp + padding.calculateBottomPadding(),
            ),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.posts, key = { _, post -> post.key }) { index, post ->
                PostTile(post, display, isNew?.invoke(post) == true) { app.openPost(state.posts, index, feed, search) }
            }
            item(key = "booru-footer", span = StaggeredGridItemSpan.FullLine) { PagingFooter(state.paging, feed) }
        }
    }
}

/** Asks for the next page as the grid's end comes into view, once per page. */
@Composable
private fun ReadsMoreNearEnd(grid: LazyStaggeredGridState, state: BooruFeedState, more: () -> Unit) {
    val nearEnd by remember(grid) {
        derivedStateOf {
            val info = grid.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - NEAR_END
        }
    }
    LaunchedEffect(nearEnd, state.posts.size, state.paging) { if (nearEnd) more() }
}

private const val NEAR_END = 8

/** Under the grid: a spinner while the next page loads, one row with Retry when it failed, and a note once there is nothing more. */
@Composable
private fun PagingFooter(paging: BooruPaging, feed: BooruFeed) {
    when (paging) {
        BooruPaging.LOADING -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        BooruPaging.FAILED -> RetryRow(pluginString(Strings.load_more_failed), onRetry = feed::retryMore)
        BooruPaging.END -> Text(
            pluginString(Strings.end_of_results),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
        )
        BooruPaging.IDLE -> Spacer(Modifier.size(1.dp))
    }
}

/**
 * One post in the grid: its picture at its shape (kept between 0.45 and 1.6),
 * blurred when the reader asked for questionable and explicit ones to be,
 * marked as a video, as Q or E, or as new; and under it, when the reader
 * wants details, its host, first tags and score.
 */
@Composable
internal fun PostTile(post: BooruPost, display: BooruDisplay, isNew: Boolean, onOpen: () -> Unit) {
    val ratio = post.aspectRatio.toFloat().coerceIn(0.45f, 1.6f)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
                TileImage(post, display, ratio)
                if (display.blurs(post)) {
                    Icon(GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center))
                }
                if (post.isVideo) TileBadge(BooruIcons.PlayCircle, pluginString(Strings.video), Modifier.align(Alignment.TopStart))
                val rating = post.rating
                if (rating == BooruRating.EXPLICIT || rating == BooruRating.QUESTIONABLE) {
                    TileBadge(GroupIcons.WarningAmber, rating.code.uppercase(), Modifier.align(Alignment.TopEnd))
                }
                if (isNew) TileBadge(BooruIcons.NewPost, pluginString(Strings.new_post), Modifier.align(Alignment.BottomStart))
            }
            if (display.tileDetails) TileDetails(post)
        }
    }
}

/** The picture at the size the tile shows it: as wide as the tile, or wider where the tile crops a picture wider than [ratio]. */
@Composable
private fun TileImage(post: BooruPost, display: BooruDisplay, ratio: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pixels = with(density) { ceil(maxWidth.toPx() * max(1f, post.aspectRatio.toFloat() / ratio)).toInt() }
        val url = remember(post, display.smallThumbnails, pixels) { display.tileUrl(post, pixels.takeIf { it > 0 }) }
        BooruImage(url, Modifier.fillMaxSize().let { if (display.blurs(post)) it.blur(20.dp) else it }, description = tileLabel(post))
    }
}

@Composable
private fun TileDetails(post: BooruPost) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Text(post.host, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (post.tags.isNotEmpty()) {
            Text(post.tags.take(4).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        post.score?.let { score ->
            Text(pluginString(Strings.score, score), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TileBadge(icon: ImageVector, label: String, modifier: Modifier) {
    Row(
        modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
