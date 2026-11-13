package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons

/** A work's screen: alone, or as one page of a pager over the list it was opened from. */
@Composable
internal fun PixivWorkScreen(route: PixivRoute.Work) {
    val runtime = LocalPixiv.current
    val handoff = remember(route) { runtime.shelf.get(route.shelf) }
    when (handoff) {
        is PixivWorkHandoff -> WorkPager(handoff)
        else -> WorkPage(route.id, handoff as? PixivIllust)
    }
}

/** A work opened from a list, in a sideways pager over its neighbours; past the last one the list's next page loads. */
@Composable
private fun WorkPager(handoff: PixivWorkHandoff) {
    val runtime = LocalPixiv.current
    val model = rememberKept("pixiv-pager") { scope -> PixivPagerModel(handoff, scope) { runtime.mutes.now.filter(it) } }
    val works by model.works.collectAsState()
    val tail by model.tail.collectAsState()
    val pages = works.size + if (tail == PixivPagerTail.NONE) 0 else 1
    val pager = rememberPagerState(initialPage = handoff.index) { pages }
    LaunchedEffect(pager.currentPage, works.size) { model.near(pager.currentPage) }
    HorizontalPager(pager, key = { works.getOrNull(it)?.id ?: -1L }, beyondViewportPageCount = 0) { page ->
        val work = works.getOrNull(page)
        if (work != null) WorkPage(work.id, work) else PagerTailPage(tail, model::loadMore)
    }
}

/** What comes after a pager's last work: the next page loading, its failure with Retry, or the end. */
@Composable
private fun PagerTailPage(tail: PixivPagerTail, onRetry: () -> Unit) {
    val runtime = LocalPixiv.current
    PluginScaffold(title = "", onBack = runtime.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (tail) {
                PixivPagerTail.FAILED -> PixivEmpty(pluginString(Strings.pager_load_failed), onRetry)
                PixivPagerTail.END, PixivPagerTail.NONE -> PixivEmpty(pluginString(Strings.pager_no_more), icon = Icons.Filled.Done)
                PixivPagerTail.MORE, PixivPagerTail.LOADING -> LoadingRow()
            }
        }
    }
}

/** One work: behind its notice while muted, else its pages, details, the creator's other works and related works. */
@Composable
internal fun WorkPage(id: Long, seed: PixivIllust?) {
    val runtime = LocalPixiv.current
    val model = rememberKept("pixiv-work-$id") { scope -> PixivWorkModel(runtime, scope, id, seed) }
    val state by model.state.collectAsState()
    val illust = state.illust
    if (illust == null) {
        PluginScaffold(title = pluginString(Strings.title), onBack = runtime.host::back) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.error != null) PixivProblem(state.error, model::load) else LoadingRow()
            }
        }
        return
    }
    MuteGate(illust) { WorkScaffold(model, state, illust) }
}

/** A muted work waits behind a notice saying why, with a way to see it this once; one already shown stays. */
@Composable
internal fun MuteGate(illust: PixivIllust, content: @Composable () -> Unit) {
    val runtime = LocalPixiv.current
    val mutes = pixivMutes()
    var shown by rememberSaveable(illust.id) { mutableStateOf(!runtime.mutes.now.isMuted(illust)) }
    val reason = mutes.reasonFor(illust)
    if (shown || reason == null) {
        content()
        return
    }
    PluginScaffold(title = pluginString(Strings.title), onBack = runtime.host::back) { padding ->
        MutedNotice(reason, Modifier.padding(padding)) { shown = true }
    }
}

/** Why something is muted, with a way to see it this once and the way to the mute settings. */
@Composable
internal fun MutedNotice(reason: PixivMuteReason, modifier: Modifier = Modifier, onShow: () -> Unit) {
    val runtime = LocalPixiv.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(PostIcons.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(pluginString(Strings.mute_gate_title), style = MaterialTheme.typography.titleMedium)
        Text(muteReasonText(reason))
        Button(onClick = onShow) { Text(pluginString(Strings.mute_gate_show)) }
        TextButton(onClick = { runtime.open(PixivRoute.Mute) }) { Text(pluginString(Strings.mute_settings)) }
    }
}

@Composable
internal fun muteReasonText(reason: PixivMuteReason): String = when (reason) {
    PixivMuteReason.Work -> pluginString(Strings.mute_gate_work)
    is PixivMuteReason.Author -> pluginString(Strings.mute_gate_author, reason.name)
    is PixivMuteReason.Tag -> pluginString(Strings.mute_gate_tag, PixivMuteTags.label(reason.entry))
}

@Composable
private fun WorkScaffold(model: PixivWorkModel, state: PixivWorkState, illust: PixivIllust) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val surface = remember(model) { WorkSurface(model, runtime) }
    PluginScaffold(
        title = illust.title.ifEmpty { pluginString(Strings.title) },
        onBack = runtime.host::back,
        actions = { WorkActions(illust, state.page, sheets) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val layout = pixivSettings()[PixivPrefs.detailLayout]
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
            if (PixivLayout.splits(maxWidth.value, layout)) {
                SplitDetail(model, state, illust, surface, maxWidth.value, bottom)
            } else {
                DetailGrid(model, state, illust, surface, bottom) {
                    item(key = "pages", span = StaggeredGridItemSpan.FullLine) {
                        val height = PixivLayout.viewerHeight(maxWidth.value, maxHeight.value, illust.width, illust.height)
                        PageViewer(illust, state.page, surface, Modifier.fillMaxWidth().height(height.dp))
                    }
                    if (illust.viewerUrls.size > 1) item(key = "page-bar", span = StaggeredGridItemSpan.FullLine) { PageBar(state.page, illust.viewerUrls.size, surface) }
                }
            }
        }
    }
    OverviewHost(surface, illust)
}

/** The bar's heart (on Pixiv; a long press opens the editor), Save this page, and the work's menu. */
@Composable
private fun WorkActions(illust: PixivIllust, page: Int, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val haptics = rememberHaptics()
    val marks by runtime.bookmarking.works.state.collectAsState()
    val bookmarked = marks.on(illust.id, illust.isBookmarked)
    IconButton(onClick = { sheets.toggleBookmark(illust, haptics) }, enabled = !marks.isBusy(illust.id)) {
        Icon(
            if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            pluginString(if (bookmarked) Strings.unbookmark else Strings.bookmark),
            tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    SavePageButton(illust, page) { sheets.savePages(illust, listOf(page)) }
    WorkMenu(illust, sheets)
}

/** Saves the page on screen; it shows once that page is saved here. */
@Composable
internal fun SavePageButton(illust: PixivIllust, page: Int, onClick: () -> Unit) {
    val saved = PixivSavedPages.key(illust.id, page) in PixivSavedPages.of(pixivSettings())
    IconButton(onClick = onClick) {
        Icon(
            if (saved) Icons.Filled.Done else SavedIcons.Download,
            pluginString(if (saved) Strings.download_page_saved else Strings.download_page),
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The work's ⋯ menu (pixiv_detail_menu.dart); More actions opens the shared post sheet, as XTA's does. */
@Composable
private fun WorkMenu(illust: PixivIllust, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    var open by remember { mutableStateOf(false) }
    val copy = rememberCopy()
    val outside = rememberOutside()
    val settings = pixivSettings()
    val language = currentLanguage()
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_actions)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        PixivWorkMenu.of(illust).forEach { entry ->
            val (label, icon) = when (entry) {
                PixivWorkMenu.DOWNLOAD_ALL -> Strings.download_all to SavedIcons.Download
                PixivWorkMenu.BOOKMARK -> Strings.bookmark_edit to Icons.Filled.Edit
                PixivWorkMenu.COPY_LINK -> Strings.copy_link to XIcons.Link
                PixivWorkMenu.COPY_INFO -> Strings.copy_info to PixivIcons.ContentCopy
                PixivWorkMenu.OPEN -> Strings.open_on_pixiv to PostIcons.OpenInNew
                PixivWorkMenu.MUTE -> Strings.mute_illust to PixivIcons.VolumeOff
                PixivWorkMenu.MORE -> Strings.more_actions to PostIcons.MoreHoriz
            }
            DropdownMenuItem(text = { Text(pluginString(label)) }, leadingIcon = { Icon(icon, null) }, onClick = {
                open = false
                when (entry) {
                    PixivWorkMenu.DOWNLOAD_ALL -> sheets.saveAll(illust)
                    PixivWorkMenu.BOOKMARK -> sheets.show(PixivSheet.EditWork(illust))
                    PixivWorkMenu.COPY_LINK -> copy(illust.url, Strings.link_copied)
                    PixivWorkMenu.COPY_INFO -> copy(PixivCopyInfo.filled(PixivCopyInfo.template(settings, defaultCopyTemplate(language)), illust), Strings.info_copied)
                    PixivWorkMenu.OPEN -> outside.open(illust.url)
                    PixivWorkMenu.MUTE -> sheets.show(PixivSheet.Mute(illust))
                    PixivWorkMenu.MORE -> runtime.host.openPostMenu(PixivShared.menu(illust))
                }
            })
        }
    }
}

/** The entries of a work's ⋯ menu, in XTA's order (pixiv_detail_menu.dart); saving every page only for a work of several. */
internal enum class PixivWorkMenu {
    DOWNLOAD_ALL, BOOKMARK, COPY_LINK, COPY_INFO, OPEN, MUTE, MORE;

    companion object {
        fun of(illust: PixivIllust): List<PixivWorkMenu> = entries.filter { it != DOWNLOAD_ALL || illust.viewerUrls.size > 1 }
    }
}

/** The details and works below a work's pages, in one staggered grid; [top] comes first (the pages, when they are above). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailGrid(
    model: PixivWorkModel,
    state: PixivWorkState,
    illust: PixivIllust,
    surface: WorkSurface,
    padding: PaddingValues,
    top: LazyStaggeredGridScope.() -> Unit = {},
) {
    val runtime = LocalPixiv.current
    val related by model.related.state.collectAsState()
    val mutes = pixivMutes()
    val works = remember(related.items, mutes) { mutes.filter(related.items) }
    val grid = rememberLazyStaggeredGridState()
    ReadsAhead(grid, related) { model.related.more() }
    PullToRefreshBox(isRefreshing = related.loading && related.items.isNotEmpty(), onRefresh = model::load, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(gridColumns(maxWidth.value, maxHeight.value)),
                state = grid,
                contentPadding = padding,
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                top()
                item(key = "meta", span = StaggeredGridItemSpan.FullLine) { WorkMeta(illust, state, surface) }
                if (state.authorWorks.isNotEmpty()) item(key = "author", span = StaggeredGridItemSpan.FullLine) { AuthorWorks(illust, state.authorWorks) }
                relatedWorks(works, related, state, runtime, model)
            }
        }
    }
}

private fun LazyStaggeredGridScope.relatedWorks(
    works: List<PixivIllust>,
    related: PixivListState<PixivIllust>,
    state: PixivWorkState,
    runtime: PixivRuntime,
    model: PixivWorkModel,
) {
    when {
        works.isNotEmpty() -> {
            item(key = "related-title", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    pluginString(Strings.related),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(works, key = { _, work -> "related-" + work.id }) { index, work ->
                WorkTile(work, onOpen = { runtime.openWork(works, index, model.related) })
            }
            pagedFooter(related, model.related::retryMore)
        }
        related.waiting -> item(key = "related-state", span = StaggeredGridItemSpan.FullLine) { LoadingRow() }
        state.error != null && !state.loaded -> item(key = "related-state", span = StaggeredGridItemSpan.FullLine) { PixivProblem(state.error, model::load) }
    }
}

/** The pictures on the start side, everything else scrolling beside them, parted by a divider the reader drags; where it is let go is kept. */
@Composable
private fun SplitDetail(model: PixivWorkModel, state: PixivWorkState, illust: PixivIllust, surface: WorkSurface, width: Float, padding: PaddingValues) {
    val runtime = LocalPixiv.current
    val density = LocalDensity.current
    var fraction by remember { mutableFloatStateOf(runtime.settings[PixivPrefs.detailSplit].toFloat()) }
    val images = PixivLayout.splitImageWidth(width, fraction)
    val drag = rememberDraggableState { delta -> fraction = PixivLayout.splitImageWidth(width, (images + with(density) { delta.toDp().value }) / width) / width }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(images.dp).fillMaxHeight()) {
            PageViewer(illust, state.page, surface, Modifier.fillMaxWidth().weight(1f))
            if (illust.viewerUrls.size > 1) PageBar(state.page, illust.viewerUrls.size, surface)
        }
        val handle = pluginString(Strings.split_divider)
        Box(
            Modifier.width(PixivLayout.SPLIT_GUTTER.dp).fillMaxHeight().semantics { contentDescription = handle }.draggable(
                drag,
                Orientation.Horizontal,
                onDragStopped = { runtime.settings.set(PixivPrefs.detailSplit, fraction.toDouble()) },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { DetailGrid(model, state, illust, surface, padding) }
    }
}

/** What a work's page sheets ask of the screen: the page on show, the reader, the overview. */
internal class WorkSurface(private val model: PixivWorkModel, private val runtime: PixivRuntime) : PixivOverviewSurface {
    override var overviewRequest by mutableStateOf<Boolean?>(null)
        private set

    val page: Int get() = model.state.value.page

    override val currentPage: Int get() = page

    override val offersVertical: Boolean get() = true

    override fun showPage(page: Int) = model.showPage(page)

    override fun changeDirection() = openReader(page, vertical = true)

    override fun openOverview(selecting: Boolean) {
        overviewRequest = selecting
    }

    override fun closeOverview() {
        overviewRequest = null
    }

    fun openReader(page: Int, vertical: Boolean) {
        val illust = model.state.value.illust ?: return
        runtime.open(PixivRoute.Reader(illust.id, page, vertical, runtime.shelf.put(illust)))
    }
}
