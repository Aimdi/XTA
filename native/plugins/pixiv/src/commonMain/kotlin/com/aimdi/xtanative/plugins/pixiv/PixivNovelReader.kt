package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivNovelContent
import com.aimdi.xtanative.networks.pixiv.PixivNovelNeighbour
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.store.reading.ReadingAppearance
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.launch

/**
 * A novel: its cover, title, creator, series, numbers, tags and caption over
 * its text in the reader's own text size and spacing (the app's reading
 * appearance), its pictures and page jumps; comments and the chapters
 * beside it at its foot. Where the reader stopped is where it opens next time.
 */
@Composable
internal fun PixivNovelScreen(id: Long, seed: PixivNovel?) {
    val runtime = LocalPixiv.current
    val model = rememberKept("pixiv-novel-$id") { scope -> PixivNovelModel(runtime, scope, id, seed) }
    val reading by model.reading.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val novel = reading.novel
    PluginScaffold(
        title = novel?.title?.ifEmpty { null } ?: pluginString(Strings.novel),
        onBack = runtime.host::back,
        actions = { if (novel != null) IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                novel == null && reading.error != null -> PixivProblem(reading.error, model::load)
                novel == null -> LoadingRow()
                else -> MuteGateNovel(novel) { NovelText(model, reading, novel) }
            }
        }
    }
    if (novel == null) return
    val export = rememberNovelExport(novel, reading)
    if (menu) NovelMenu(novel, reading.content, onAppearance = { appearance = true }, onExport = { exporting = true }) { menu = false }
    if (appearance) AppearanceDialog { appearance = false }
    if (exporting) {
        NovelExportSheet(onPick = { format ->
            exporting = false
            export(format)
        }) { exporting = false }
    }
}

/** A muted novel waits behind a notice saying why, as a muted work does. */
@Composable
private fun MuteGateNovel(novel: PixivNovel, content: @Composable () -> Unit) {
    val runtime = LocalPixiv.current
    var shown by rememberSaveable(novel.id) { mutableStateOf(!runtime.mutes.now.hidesNovel(novel)) }
    val reason = pixivMutes().reasonFor(novel)
    if (shown || reason == null) content() else MutedNotice(reason) { shown = true }
}

@Composable
private fun NovelText(model: PixivNovelModel, reading: PixivNovelReading, novel: PixivNovel) {
    val look by produceAppearance(LocalPixiv.current.host.readingAppearance)
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val content = reading.content
    ResumesAt(scroll, reading.resumeAt, offset = HEADER_ITEMS)
    KeepsPlace(model, scroll, reading.blocks.size)
    val jump: (Int) -> Unit = { page -> reading.pageStarts[page]?.let { at -> scope.launch { scroll.animateScrollToItem(at + HEADER_ITEMS) } } }
    LazyColumn(state = scroll, contentPadding = PaddingValues(horizontal = 20.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") { NovelHeader(novel) }
        when {
            content == null && reading.error != null -> item(key = "error") { PixivProblem(reading.error, model::load) }
            content == null -> item(key = "loading") { LoadingRow() }
            else -> itemsIndexed(reading.blocks, key = { index, _ -> "block-$index" }) { _, block -> NovelBlockView(block, content, look, jump) }
        }
        if (content != null) item(key = "footer") { NovelFooter(novel, content) }
    }
}

/** How many items come before the first block. */
private const val HEADER_ITEMS = 1

@Composable
private fun produceAppearance(store: ReadingAppearanceStore): State<ReadingAppearance> =
    androidx.compose.runtime.produceState(ReadingAppearance(), store) { value = store.get() }

/** Opens where the reader stopped, once the text is there. */
@Composable
private fun ResumesAt(scroll: LazyListState, block: Int?, offset: Int) {
    LaunchedEffect(block) { if (block != null && block > 0) scroll.scrollToItem(block + offset) }
}

/** Keeps the block on top whenever the reader stops scrolling, and as the screen goes. */
@Composable
private fun KeepsPlace(model: PixivNovelModel, scroll: LazyListState, total: Int) {
    val current = { (scroll.firstVisibleItemIndex - HEADER_ITEMS).coerceAtLeast(0) }
    LaunchedEffect(scroll.isScrollInProgress, total) { if (!scroll.isScrollInProgress && total > 0 && scroll.firstVisibleItemIndex > 0) model.keepPlace(current(), total) }
    DisposableEffect(total) { onDispose { if (total > 0 && scroll.firstVisibleItemIndex > 0) model.keepPlace(current(), total) } }
}

@Composable
private fun NovelHeader(novel: PixivNovel) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NovelCover(novel.coverUrl, 88.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (novel.title.isNotEmpty()) Text(novel.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { runtime.openUser(novel.user.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixivAvatar(novel.user.avatarUrl, novel.user.name, size = 24.dp)
                    Text(novel.user.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        novel.series?.let { SeriesLink(it, novel = true) }
        NovelStats(novel)
        if (novel.tags.isNotEmpty()) TagChips(novel.tags, PixivSearchKind.NOVELS, leaveOnMute = false)
        if (novel.captionHtml.isNotEmpty() || novel.caption.isNotEmpty()) PixivHtmlText(novel.captionHtml, novel.caption)
        CommentsLink(PixivKind.NOVEL, novel.id, novel.totalComments)
        HorizontalDivider()
    }
}

/** Whether it is bookmarked on Pixiv and its count, views, date, length and the R-18 and AI marks. */
@Composable
private fun NovelStats(novel: PixivNovel) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarking.novels.state.collectAsState()
    val bookmarked = marks.on(novel.id, novel.isBookmarked)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stat(if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, countText(marks.count(novel.id, novel.isBookmarked, novel.totalBookmarks)))
        Stat(SavedIcons.Visibility, countText(novel.totalViews))
        novel.createdAt?.let { Text(dateText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Stat(PixivIcons.Notes, pluginPlural(Strings.novel_characters, novel.textLength.toInt(), novel.textLength.toInt(), countText(novel.textLength)))
        if (novel.isR18) Text(pluginString(if (novel.isR18G) Strings.r18g else Strings.r18), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        if (novel.isAi && pixivSettings()[PixivPrefs.aiBadge]) Text(pluginString(Strings.ai), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
    }
}

/** Comments, then the chapters before and after this one. */
@Composable
private fun NovelFooter(novel: PixivNovel, content: PixivNovelContent) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        CommentsLink(PixivKind.NOVEL, novel.id, novel.totalComments)
        ChapterButtons(content)
    }
}

@Composable
private fun ChapterButtons(content: PixivNovelContent) {
    if (content.previous == null && content.next == null) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content.previous?.let { ChapterButton(it, Strings.novel_previous, Modifier.weight(1f).widthIn(min = 160.dp)) }
        content.next?.let { ChapterButton(it, Strings.novel_next, Modifier.weight(1f).widthIn(min = 160.dp)) }
    }
}

@Composable
private fun ChapterButton(chapter: PixivNovelNeighbour, label: PluginText, modifier: Modifier) {
    val runtime = LocalPixiv.current
    OutlinedButton(onClick = { runtime.replace(PixivRoute.Novel(chapter.id)) }, enabled = chapter.viewable, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pluginString(label))
            val name = chapterName(chapter)
            if (name.isNotEmpty()) Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

/** "#3 Title", or whichever of the two Pixiv sent. */
@Composable
internal fun chapterName(chapter: PixivNovelNeighbour): String = listOfNotNull(
    chapter.order.takeIf { it > 0 }?.let { pluginString(Strings.novel_chapter_number, it) },
    chapter.title.ifEmpty { null },
).joinToString(" ")
