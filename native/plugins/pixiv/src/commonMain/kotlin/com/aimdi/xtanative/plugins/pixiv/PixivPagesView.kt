package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/**
 * A work's pages side by side at the size the reader picked for work pages,
 * the grid's picture under the first while it loads, the next page asked for
 * already. A tap opens the reader,
 * a long press the page's actions; an ugoira's first page plays in place.
 * Pushing past the last page moves a pager of works on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageViewer(illust: PixivIllust, page: Int, surface: WorkSurface, modifier: Modifier) {
    val settings = pixivSettings()
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val quality = PixivQualitySlot.DETAIL.of(settings)
    val poster = PixivImages.tileUrl(illust, PixivQualitySlot.FEED.of(settings))
    val pages = illust.viewerUrls.size
    val pager = rememberPagerState(initialPage = page.coerceIn(0, (pages - 1).coerceAtLeast(0))) { pages }
    LaunchedEffect(pager.currentPage) { surface.showPage(pager.currentPage) }
    LaunchedEffect(page) { if (page != pager.currentPage && page in 0 until pages) pager.scrollToPage(page) }
    HorizontalPager(pager, modifier = modifier, beyondViewportPageCount = 1) { index ->
        val url = PixivImages.pageUrl(illust, index, quality)
        val ugoira = index == 0 && illust.isUgoira
        Box(
            Modifier.fillMaxSize().combinedClickable(
                onClick = { if (!ugoira) surface.openReader(index, vertical = false) },
                onLongClick = {
                    haptics.medium()
                    sheets.show(PixivSheet.PageActions(illust, index, surface))
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (index == 0 && poster != url) PixivPicture(poster, Modifier.fillMaxSize(), ContentScale.Fit)
            PixivPicture(url, Modifier.fillMaxSize(), ContentScale.Fit, contentDescription = pluginString(Strings.page_of, index + 1, pages), loading = true)
            if (ugoira) UgoiraPlayer(illust, Modifier.fillMaxSize())
        }
    }
}

/** The page counter, which opens every page, beside a quiet way into the reader read downwards. */
@Composable
internal fun PageBar(page: Int, pages: Int, surface: WorkSurface) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { surface.openOverview(false) }) {
            Icon(GroupIcons.GridView, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.page_of, page + 1, pages))
        }
        OutlinedButton(onClick = surface::changeDirection) {
            Icon(PixivIcons.ArrowDownward, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.read_vertically))
        }
    }
}

/** The page overview while the work's surface asks for it. */
@Composable
internal fun OverviewHost(surface: PixivOverviewSurface, illust: PixivIllust) {
    val selecting = surface.overviewRequest ?: return
    PageOverview(illust, surface.currentPage, selecting, surface)
}

/** What the page overview needs from the screen it opens over: the work's page now, and what each choice does. */
internal interface PixivOverviewSurface : PixivPageSurface {
    val currentPage: Int

    /** Whether the overview is open, and ready to pick pages; null when it is not. */
    val overviewRequest: Boolean?

    fun closeOverview()
}

/**
 * Every page of a work as thumbnails, its page actions pinned below. Select
 * pages turns the grid into a picker whose ticked pages are saved together;
 * a tap otherwise shows that page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageOverview(illust: PixivIllust, current: Int, selectingFirst: Boolean, surface: PixivOverviewSurface) {
    val sheets = LocalPixivSheets.current
    var picking by remember { mutableStateOf(selectingFirst) }
    var picked by remember { mutableStateOf(emptySet<Int>()) }
    val pages = illust.viewerUrls.size
    ModalBottomSheet(onDismissRequest = surface::closeOverview) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(illust.title.ifEmpty { pluginString(Strings.title) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                if (picking) selectedText(picked.size) else pluginPlural(Strings.pages, pages, pages),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp),
                Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items((0 until pages).toList()) { index ->
                    PageThumb(illust, index, current = index == current, selected = if (picking) index in picked else null, onTap = {
                        if (picking) {
                            picked = if (index in picked) picked - index else picked + index
                        } else {
                            surface.closeOverview()
                            surface.showPage(index)
                        }
                    })
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            if (picking) {
                PickingBar(picked, pages, onCancel = { picking = false; picked = emptySet() }, onAll = { picked = if (picked.size == pages) emptySet() else (0 until pages).toSet() }) {
                    surface.closeOverview()
                    sheets.savePages(illust, picked.sorted())
                }
            } else {
                OverviewActions(illust, surface, onPick = { picking = true })
            }
        }
    }
}

@Composable
private fun selectedText(count: Int): String = if (count == 0) pluginString(Strings.selected_pages_none) else pluginPlural(Strings.selected_pages, count, count)

/** One page in the overview: ticked while picking, outlined when it is the page on show. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumb(illust: PixivIllust, page: Int, current: Boolean, selected: Boolean?, onTap: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.aspectRatio(illust.aspectRatio.toFloat().coerceIn(0.7f, 1f)).clip(shape)
            .let { if (current) it.border(2.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .combinedClickable(onClick = onTap),
    ) {
        PixivPicture(illust.thumbUrlAt(page), Modifier.fillMaxSize())
        PixivBadge("${page + 1}", modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        if (selected != null) Checkbox(checked = selected, onCheckedChange = { onTap() }, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun OverviewActions(illust: PixivIllust, surface: PixivOverviewSurface, onPick: () -> Unit) {
    val sheets = LocalPixivSheets.current
    val pages = illust.viewerUrls.size
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        OverviewAction(SavedIcons.Download, pluginString(Strings.download_page)) {
            surface.closeOverview()
            sheets.savePages(illust, listOf(surface.currentPage))
        }
        if (pages > 1) {
            OverviewAction(SavedIcons.Download, pluginString(Strings.download_all)) {
                surface.closeOverview()
                sheets.saveAll(illust)
            }
            OverviewAction(SavedIcons.Checklist, pluginString(Strings.select_pages), onPick)
        }
        val down = surface.offersVertical
        OverviewAction(if (down) PixivIcons.ArrowDownward else Icons.AutoMirrored.Filled.KeyboardArrowRight, pluginString(if (down) Strings.read_vertically else Strings.read_horizontally)) {
            surface.closeOverview()
            surface.changeDirection()
        }
    }
}

/** An icon in a soft accent well with its label under it. */
@Composable
private fun OverviewAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(12.dp)).combinedClickableCompat(onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.size(56.dp, 40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

/** Cancel, All or None, and Save N pages while picking. */
@Composable
private fun PickingBar(picked: Set<Int>, pages: Int, onCancel: () -> Unit, onAll: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onCancel) { Text(pluginString(Strings.cancel)) }
        OutlinedButton(onClick = onAll) { Text(pluginString(if (picked.size == pages) Strings.select_none else Strings.all)) }
        Button(onClick = onSave, enabled = picked.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(SavedIcons.Download, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginPlural(Strings.save_pages, picked.size, picked.size), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
