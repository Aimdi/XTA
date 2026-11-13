package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import java.util.Locale

/**
 * Galleries as tiles in a grid (eh_grid.dart), as many columns as the width
 * and the text size allow; a tile opens the gallery's page. With [onRefresh]
 * it is pulled to read again; with [onNearEnd] it reads on as its end comes
 * into view, a spinner below while [loadingMore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryGrid(
    app: EhApp,
    galleries: List<EhGallery>,
    padding: PaddingValues,
    grid: LazyGridState,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
) {
    val settings by app.settings.collectAsState()
    val japanese = EhConfig.preferJapanese(settings ?: Settings())
    val shown = remember(galleries) { galleries.distinctBy { it.gid } }
    if (onNearEnd != null) ReadsOnNearEnd(grid, shown.size, onNearEnd)
    val content: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = EhLayout.galleryColumns(maxWidth.value, LocalDensity.current.fontScale)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = withGap(padding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.gid }) { gallery -> GalleryTile(app, gallery, japanese) { app.openGallery(gallery) } }
                if (loadingMore) item(key = "eh-more", span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
            }
        }
    }
    if (onRefresh != null) PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { content() } else content()
}

/** [padding] and the grid's own 8 dp around its tiles. */
@Composable
internal fun withGap(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = 8.dp + padding.calculateStartPadding(direction),
        end = 8.dp + padding.calculateEndPadding(direction),
        top = 8.dp + padding.calculateTopPadding(),
        bottom = 8.dp + padding.calculateBottomPadding(),
    )
}

/** Asks for more as the last of [count] tiles comes near, once for each count. */
@Composable
internal fun ReadsOnNearEnd(grid: LazyGridState, count: Int, more: () -> Unit) {
    val near by remember(grid) {
        derivedStateOf {
            val info = grid.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - NEAR_END
        }
    }
    LaunchedEffect(near, count) { if (near) more() }
}

private const val NEAR_END = 6

/** A gallery's cover with its category and language, then its title, uploader, rating and length. */
@Composable
private fun GalleryTile(app: EhApp, gallery: EhGallery, japanese: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onOpen),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                EhImage(app, gallery.thumbUrl, Modifier.fillMaxSize())
                gallery.category?.let { CategoryBadge(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
                gallery.tagLanguage?.let { LanguageBadge(it, Modifier.align(Alignment.BottomStart).padding(6.dp)) }
            }
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                Text(gallery.titleFor(japanese), style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                gallery.uploader?.takeIf(String::isNotEmpty)?.let { uploader ->
                    Text(uploader, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                if (gallery.pageCount != null || gallery.rating != null) RatingAndPages(gallery, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** `EN`, `JA`: the gallery's language, read aloud by its name. */
@Composable
private fun LanguageBadge(language: String, modifier: Modifier) {
    Text(
        EhFormats.languageCode(language),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics { contentDescription = language },
    )
}

/** `★ 4.5 · 24 pages` on one line, so tiles keep their height. */
@Composable
private fun RatingAndPages(gallery: EhGallery, modifier: Modifier) {
    val locale = Locale.forLanguageTag(currentLanguage())
    val rating = gallery.rating
    val pages = gallery.pageCount
    val style = MaterialTheme.typography.labelSmall
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (rating != null) {
            val value = EhFormats.shortRating(rating, locale)
            val spoken = pluginString(Strings.rating, value)
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(14.dp))
            Text(value, style = style, modifier = Modifier.semantics { contentDescription = spoken })
        }
        if (rating != null && pages != null) Text(" · ", style = style)
        if (pages != null) Text(pluginPlural(Strings.pages, pages, pages), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
