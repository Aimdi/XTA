package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackHighlights
import com.aimdi.xtanative.networks.substack.SubstackPassage
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.reading.ArticleReading
import com.aimdi.xtanative.store.reading.BlockExtent
import com.aimdi.xtanative.store.reading.firstVisibleBlock
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberShareText
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What opens over the article: its contents (or find), its highlights, a footnote. */
internal sealed interface ReaderSheet : java.io.Serializable {
    data class Contents(val find: Boolean) : ReaderSheet

    data object Highlights : ReaderSheet {
        private fun readResolve(): Any = Highlights
    }

    data class Footnote(val number: Int) : ReaderSheet
}

/**
 * A selection over [content] whose menu also offers [label] for what is
 * selected: the desktop's right-click menu, Android's selection toolbar.
 * [onHighlight] gets the selected text.
 */
@Composable
internal expect fun HighlightingSelection(label: String, onHighlight: (String) -> Unit, content: @Composable () -> Unit)

/**
 * The reader's line under its bar, as XTA's ArticleReaderControls: how far
 * in, whether it opened where it was left or is finished, back to the top,
 * the type, and marking it finished (and so read).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderControls(reading: ArticleReading, supportsAppearance: Boolean, canComplete: Boolean, onStartOver: () -> Unit) {
    val state by reading.state.collectAsState()
    val scope = rememberCoroutineScope()
    var appearance by rememberSaveable { mutableStateOf(false) }
    val point = state.point
    val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag(currentLanguage())).format(point.fraction)
    LinearProgressIndicator(progress = { point.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val status = when {
            point.completed -> pluginString(Strings.reader_finished)
            state.resumed -> pluginString(Strings.reader_resumed) + " · " + percent
            point.fraction > 0.01 -> percent
            else -> pluginString(Strings.reader_opened)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.weight(1f))
        IconButton(onClick = { reading.startOver(); onStartOver() }, enabled = supportsAppearance) { Icon(SubstackIcons.Replay, pluginString(Strings.reader_start_over)) }
        IconButton(onClick = { appearance = true }, enabled = supportsAppearance) { Icon(SubstackIcons.TextFields, pluginString(Strings.reader_appearance)) }
        IconButton(onClick = { scope.launch { reading.complete() } }, enabled = !point.completed && canComplete) {
            Icon(
                if (point.completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                pluginString(if (point.completed) Strings.reader_finished else Strings.reader_finish),
                tint = if (point.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (appearance) {
        ModalBottomSheet(onDismissRequest = { appearance = false }) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                SheetHeading(pluginString(Strings.reader_appearance)) { appearance = false }
                Text(pluginString(Strings.reader_text_size), modifier = Modifier.padding(top = 16.dp))
                Slider(value = state.fontSize.toFloat(), onValueChange = { reading.appearance(fontSize = it.roundToInt().toDouble()) }, valueRange = 16f..28f, steps = 11)
                Text(pluginString(Strings.reader_line_spacing))
                Slider(value = state.lineHeight.toFloat(), onValueChange = { reading.appearance(lineHeight = (it * 10).roundToInt() / 10.0) }, valueRange = 1.4f..2.2f, steps = 7)
            }
        }
    }
}

/**
 * Keeps the reader's place in [list]: puts it back once the article is laid
 * out, then reports where the reader stands as they scroll, by the first
 * block still showing ([firstVisibleBlock]) and how far down, so the place
 * survives another text size. Leading is kept in density-independent pixels,
 * as XTA keeps its logical ones.
 */
@Composable
internal fun KeepsPlace(reading: ArticleReading, list: LazyListState, blocks: Int) {
    val density = LocalDensity.current.density
    var restored by remember(reading) { mutableStateOf(false) }
    LaunchedEffect(reading, blocks) {
        val point = reading.state.value.point
        snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
        if (blocks > 0 && (point.paragraph > 0 || point.leading < 0)) {
            list.scrollToItem(ROWS_BEFORE_BLOCKS + point.paragraph.coerceIn(0, blocks - 1), (-point.leading * density).roundToInt().coerceAtLeast(0))
        }
        restored = true
    }
    if (!restored) return
    LaunchedEffect(reading, list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.drop(1).collect {
            reading.receivePoint(pointOf(list, density, blocks), interacted = true, userScrolled = list.isScrollInProgress, atEnd = !list.canScrollForward)
        }
    }
}

private fun pointOf(list: LazyListState, density: Float, blocks: Int): ArticleReadPoint {
    val info = list.layoutInfo
    val extents = info.visibleItemsInfo.filter { it.index in ROWS_BEFORE_BLOCKS until ROWS_BEFORE_BLOCKS + blocks }
        .map { BlockExtent(it.index - ROWS_BEFORE_BLOCKS, it.offset / density.toDouble(), it.size / density.toDouble()) }
    val visible = firstVisibleBlock(extents)
    val first = info.visibleItemsInfo.firstOrNull()
    val position = list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset.toDouble() / (first?.size?.coerceAtLeast(1) ?: 1)
    val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    val fraction = if (!list.canScrollForward) 1.0 else (position / scrollable).coerceIn(0.0, 1.0)
    return ArticleReadPoint(fraction = fraction, paragraph = visible?.index ?: 0, leading = visible?.leading ?: 0.0)
}

/** The sheets over an article: contents and find, its highlights, a footnote. */
@Composable
internal fun ReaderSheets(app: SubstackApp, post: SubstackPost, article: ReaderContent.Article, list: LazyListState, sheet: ReaderSheet?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    when (sheet) {
        is ReaderSheet.Contents -> ContentsSheet(article, sheet.find, onDismiss) { passage ->
            onDismiss()
            article.article.blockOf(passage)?.let { block -> scope.launch { list.animateScrollToItem(ROWS_BEFORE_BLOCKS + block) } }
        }
        ReaderSheet.Highlights -> HighlightsSheet(app, post, onDismiss)
        is ReaderSheet.Footnote -> FootnoteSheet(app, article, sheet.number, onDismiss)
        null -> Unit
    }
}

/** The article's headings, or the passages holding every word asked, each leading to its place (substack_article_navigation_sheet.dart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(content: ReaderContent.Article, find: Boolean, onDismiss: () -> Unit, onJump: (SubstackPassage) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val article = content.article
    val results = remember(article, query) { if (query.isBlank()) article.headings else article.search(query) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.88f)) {
            SheetHeading(pluginString(Strings.contents), onDismiss)
            Text(pluginString(Strings.find), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                placeholder = { Text(pluginString(Strings.search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, pluginString(Strings.clear_search)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            )
            if (results.isEmpty()) {
                EmptyState(pluginString(if (query.isBlank()) Strings.no_headings else Strings.no_matches))
            } else {
                LazyColumn {
                    items(results, key = { it.anchor }) { passage ->
                        val indent = if (query.isBlank()) (passage.headingLevel - 1).coerceIn(0, 3) * 12 else 0
                        Row(Modifier.fillMaxWidth().clickable { onJump(passage) }.padding(start = (20 + indent).dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (query.isBlank()) passage.text else passage.excerpt(query), modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }
                }
            }
        }
    }
    if (find) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The post's highlights, each to remove, and all shared as Markdown (substack_highlights_sheet.dart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightsSheet(app: SubstackApp, post: SubstackPost, onDismiss: () -> Unit) {
    val all by app.highlights.state.collectAsState()
    val quotes = all[post.key].orEmpty()
    val share = rememberShareText()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetHeading(pluginString(Strings.highlights), onDismiss)
            if (quotes.isEmpty()) {
                Text(pluginString(Strings.highlights_empty), modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(quotes, key = { it }) { quote ->
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(quote, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                            IconButton(onClick = { app.launch { app.highlights.remove(post, quote) } }) { Icon(Icons.Default.Delete, pluginString(Strings.highlight_remove)) }
                        }
                    }
                }
                FilledTonalButton(onClick = { share(SubstackHighlights.markdown(post, quotes)) }, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                    Icon(Icons.Default.Share, null, Modifier.padding(end = 8.dp))
                    Text(pluginString(Strings.highlights_share))
                }
            }
        }
    }
}

/** A footnote's own text over the article, where its number was tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FootnoteSheet(app: SubstackApp, content: ReaderContent.Article, number: Int, onDismiss: () -> Unit) {
    val notes = remember(content, number) { footnotesOf(content.article.blocks, number) }
    if (notes.isEmpty()) return LaunchedEffect(Unit) { onDismiss() }
    val body = MaterialTheme.typography.bodyLarge
    val taps = remember(app) { ArticleTaps(onLink = { url -> onDismiss(); app.openLink(url) }, onFootnote = {}, onImage = {}) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SelectionContainer {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                notes.forEach { BlockView(it, body, emptyList(), emptyMap(), null, taps) }
            }
        }
    }
}
