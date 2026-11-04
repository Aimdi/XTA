package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.ReaderBlock
import com.aimdi.xtanative.networks.ReaderRun
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.reading.ArticleReading
import com.aimdi.xtanative.store.reading.BlockExtent
import com.aimdi.xtanative.store.reading.firstVisibleBlock
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_appearance
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_finish
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_finished
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_line_spacing
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_opened
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_resumed
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_start_over
import com.aimdi.xtanative.ui.generated.resources.plugin_reader_text_size
import com.aimdi.xtanative.ui.generated.resources.reader_close
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's line under its bar, as XTA's ArticleReaderControls: how far
 * in, whether it opened where it was left or is finished, back to the
 * beginning ([onStartOver] scrolls there), the type, and marking it finished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderControls(reading: ArticleReading, supportsAppearance: Boolean, canComplete: Boolean, onStartOver: () -> Unit) {
    val state by reading.state.collectAsState()
    val scope = rememberCoroutineScope()
    var appearance by rememberSaveable { mutableStateOf(false) }
    val point = state.point
    val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag(currentLanguage())).format(point.fraction)
    LinearProgressIndicator(progress = { point.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val status = when {
            point.completed -> stringResource(Res.string.plugin_reader_finished)
            state.resumed -> stringResource(Res.string.plugin_reader_resumed) + " · " + percent
            point.fraction > 0.01 -> percent
            else -> stringResource(Res.string.plugin_reader_opened)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.weight(1f))
        IconButton(onClick = { reading.startOver(); onStartOver() }, enabled = supportsAppearance) {
            Icon(ReaderGlyphs.Replay, stringResource(Res.string.plugin_reader_start_over))
        }
        IconButton(onClick = { appearance = true }, enabled = supportsAppearance) {
            Icon(ReaderGlyphs.TextFields, stringResource(Res.string.plugin_reader_appearance))
        }
        IconButton(onClick = { scope.launch { reading.complete() } }, enabled = !point.completed && canComplete) {
            Icon(
                if (point.completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                stringResource(if (point.completed) Res.string.plugin_reader_finished else Res.string.plugin_reader_finish),
                tint = if (point.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (appearance) {
        ModalBottomSheet(onDismissRequest = { appearance = false }) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.plugin_reader_appearance), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { appearance = false }) { Icon(Icons.Filled.Close, stringResource(Res.string.reader_close)) }
                }
                Text(stringResource(Res.string.plugin_reader_text_size), modifier = Modifier.padding(top = 16.dp))
                Slider(value = state.fontSize.toFloat(), onValueChange = { reading.appearance(fontSize = it.roundToInt().toDouble()) }, valueRange = 16f..28f, steps = 11)
                Text(stringResource(Res.string.plugin_reader_line_spacing))
                Slider(value = state.lineHeight.toFloat(), onValueChange = { reading.appearance(lineHeight = (it * 10).roundToInt() / 10.0) }, valueRange = 1.4f..2.2f, steps = 7)
            }
        }
    }
}

/**
 * Keeps the reader's place in [list], whose first [before] rows come before
 * the article's [blocks]: puts it back once the article is laid out, then
 * reports where the reader stands as they scroll, by the first block still
 * showing ([firstVisibleBlock]) and how far down, so the place survives
 * another text size. Leading is kept in density-independent pixels, as XTA
 * keeps its logical ones.
 */
@Composable
fun KeepsReaderPlace(reading: ArticleReading, list: LazyListState, before: Int, blocks: Int) {
    val density = LocalDensity.current.density
    var restored by remember(reading) { mutableStateOf(false) }
    LaunchedEffect(reading, blocks) {
        val point = reading.state.value.point
        snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
        if (blocks > 0 && (point.paragraph > 0 || point.leading < 0)) {
            list.scrollToItem(before + point.paragraph.coerceIn(0, blocks - 1), (-point.leading * density).roundToInt().coerceAtLeast(0))
        }
        restored = true
    }
    if (!restored) return
    LaunchedEffect(reading, list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.drop(1).collect {
            reading.receivePoint(placeIn(list, density, before, blocks), interacted = true, userScrolled = list.isScrollInProgress, atEnd = !list.canScrollForward)
        }
    }
}

private fun placeIn(list: LazyListState, density: Float, before: Int, blocks: Int): ArticleReadPoint {
    val info = list.layoutInfo
    val extents = info.visibleItemsInfo.filter { it.index in before until before + blocks }
        .map { BlockExtent(it.index - before, it.offset / density.toDouble(), it.size / density.toDouble()) }
    val visible = firstVisibleBlock(extents)
    val first = info.visibleItemsInfo.firstOrNull()
    val position = list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset.toDouble() / (first?.size?.coerceAtLeast(1) ?: 1)
    val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    val fraction = if (!list.canScrollForward) 1.0 else (position / scrollable).coerceIn(0.0, 1.0)
    return ArticleReadPoint(fraction = fraction, paragraph = visible?.index ?: 0, leading = visible?.leading ?: 0.0)
}

/** How an article's text is set: the size and line height every reader shares. */
class ReaderLook(val fontSize: Double, val lineHeight: Double)

/**
 * [blocks] as rows of a list, one each, set in [look]: a link calls [onLink],
 * a picture [onImage] with its index among the pictures, drawn from the
 * copy kept on the device when [kept] has one.
 */
fun LazyListScope.readerBlocks(
    blocks: List<ReaderBlock>,
    look: ReaderLook,
    onLink: (String) -> Unit,
    onImage: (Int) -> Unit,
    kept: Map<String, Any> = emptyMap(),
) {
    val images = blocks.filterIsInstance<ReaderBlock.Image>()
    itemsIndexed(blocks, contentType = { _, block -> block::class.simpleName }) { _, block ->
        Box(Modifier.padding(horizontal = 20.dp)) {
            when (block) {
                is ReaderBlock.Text -> ReaderText(block, look, onLink)
                is ReaderBlock.Image -> AsyncImage(
                    model = kept[block.src] ?: block.src,
                    contentDescription = block.alt,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImage(images.indexOf(block).coerceAtLeast(0)) },
                )
                ReaderBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ReaderText(block: ReaderBlock.Text, look: ReaderLook, onLink: (String) -> Unit) {
    val body = TextStyle(fontSize = look.fontSize.sp, lineHeight = look.lineHeight.em, color = MaterialTheme.colorScheme.onSurface)
    val link = MaterialTheme.colorScheme.primary
    val code = MaterialTheme.colorScheme.surfaceContainerHigh
    val text = remember(block, link, code, onLink) { annotated(block.runs, link, code, onLink) }
    when (block.kind) {
        ReaderBlock.Text.Kind.PARAGRAPH -> Text(text, style = body, modifier = Modifier.padding(vertical = 8.dp))
        ReaderBlock.Text.Kind.HEADING -> Text(
            text,
            style = body.copy(fontSize = (look.fontSize * headingScale(block.level)).sp, fontWeight = FontWeight.Bold, lineHeight = 1.3.em),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        ReaderBlock.Text.Kind.QUOTE -> Row(Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Text(text, style = body.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 12.dp))
        }
        ReaderBlock.Text.Kind.LIST_ITEM -> Row(Modifier.padding(start = (16 * block.level).dp, top = 4.dp, bottom = 4.dp)) {
            Text((block.marker ?: "•") + " ", style = body)
            Text(text, style = body, modifier = Modifier.weight(1f))
        }
        ReaderBlock.Text.Kind.CODE -> Box(
            Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(code)
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(block.text, style = body.copy(fontFamily = FontFamily.Monospace, fontSize = (look.fontSize * 0.85).sp, lineHeight = 1.4.em))
        }
    }
}

private fun headingScale(level: Int): Double = when (level) {
    1 -> 1.5
    2 -> 1.33
    3 -> 1.17
    else -> 1.05
}

private fun annotated(runs: List<ReaderRun>, link: Color, code: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        val look = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            background = if (run.code) code else Color.Unspecified,
        )
        val href = run.href
        if (href == null) {
            withStyle(look) { append(run.text) }
        } else {
            val styles = TextLinkStyles(look.merge(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))
            withLink(LinkAnnotation.Clickable(href, styles) { onLink(href) }) { append(run.text) }
        }
    }
}

/** The reader's icons the core set lacks: Material's (Apache 2.0). */
private object ReaderGlyphs {
    val Replay by lazy { pathIcon("Replay", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z") }
    val TextFields by lazy { pathIcon("TextFields", "M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z") }
}
