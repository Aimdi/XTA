package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.substack.SubstackBlock
import com.aimdi.xtanative.networks.substack.SubstackDividerBlock
import com.aimdi.xtanative.networks.substack.SubstackEmbedBlock
import com.aimdi.xtanative.networks.substack.SubstackHighlights
import com.aimdi.xtanative.networks.substack.SubstackImageBlock
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackRun
import com.aimdi.xtanative.networks.substack.SubstackShownRun
import com.aimdi.xtanative.networks.substack.SubstackTableBlock
import com.aimdi.xtanative.networks.substack.SubstackTextBlock
import com.aimdi.xtanative.networks.substack.SubstackWebOnlyBlock
import com.aimdi.xtanative.store.settings.SubstackColumnWidth
import com.aimdi.xtanative.store.settings.SubstackReaderFont
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import java.io.File
import androidx.compose.ui.unit.TextUnit

/** How the reader sets an article: its font and column (the plugin's settings) and the type every reader shares. */
@Immutable
internal data class ArticleLook(val font: SubstackReaderFont, val column: SubstackColumnWidth, val fontSize: Double, val lineHeight: Double) {
    val family: FontFamily get() = if (font == SubstackReaderFont.SERIF) FontFamily.Serif else FontFamily.Default
    val columnWidth: Dp get() = (column.rem * 16).dp
}

/** Where an article's taps lead; [onPress] is told which block a press (a long one, a right click) landed on, -1 for the header. */
internal class ArticleTaps(
    val onLink: (String) -> Unit,
    val onFootnote: (Int) -> Unit,
    val onImage: (SubstackImageBlock) -> Unit,
    val onPress: (Int) -> Unit = {},
)

/**
 * A post's body as XTA's SubstackNativeArticle draws it, without a web view:
 * its title, where it is from and how long it reads, each block the way its
 * kind reads (headings, quotes, lists, code, pictures with captions and the
 * viewer, held embeds, tables, footnotes), the reader's highlights marked,
 * and, for the free part of a paid post, where it ends. Its rows are the
 * header ([ROWS_BEFORE_BLOCKS]), one per block, then the footer.
 */
@Composable
internal fun ArticleList(
    post: SubstackPost,
    blocks: List<SubstackBlock>,
    readMinutes: Int?,
    preview: Boolean,
    look: ArticleLook,
    highlights: List<String>,
    keptImages: Map<String, File>,
    taps: ArticleTaps,
    list: LazyListState,
    modifier: Modifier = Modifier,
) {
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = look.fontSize.sp, lineHeight = (look.fontSize * look.lineHeight).sp, fontFamily = look.family)
    val column = Modifier.fillMaxWidth().wrapContentWidth().padding(horizontal = 20.dp).widthIn(max = look.columnWidth).fillMaxWidth()
    LazyColumn(state = list, modifier = modifier.fillMaxSize()) {
        item(key = "header") { ArticleHeader(post, readMinutes, column.pressed(-1, taps).padding(top = 16.dp, bottom = 24.dp)) }
        itemsIndexed(blocks, key = { index, _ -> "block-$index" }) { index, block ->
            Box(column.pressed(index, taps).padding(bottom = 16.dp)) { BlockView(block, body, highlights, keptImages, post.canonicalUrl, taps) }
        }
        item(key = "footer") { ArticleFooter(post, preview, body, taps, column.padding(bottom = 48.dp)) }
    }
}

/** Tells [taps] of each press on block [index], seen before the selection takes it. */
private fun Modifier.pressed(index: Int, taps: ArticleTaps): Modifier = pointerInput(index, taps) {
    awaitPointerEventScope {
        while (true) {
            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) taps.onPress(index)
        }
    }
}

/** The rows above an article's first block: its header. */
internal const val ROWS_BEFORE_BLOCKS = 1

@Composable
private fun ArticleHeader(post: SubstackPost, readMinutes: Int?, modifier: Modifier) {
    val minutes = readMinutes?.let { pluginPlural(Strings.read_minutes, it, it) }
    val meta = listOfNotNull(post.publicationName, post.authorName, minutes).filter { it.isNotBlank() }.joinToString(" · ")
    Column(modifier) {
        Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        post.excerpt?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
    }
}

/** Where the free part of a paid post stops, and the way to the rest on the publication's site. */
@Composable
private fun ArticleFooter(post: SubstackPost, preview: Boolean, body: TextStyle, taps: ArticleTaps, modifier: Modifier) {
    if (!preview) return Box(modifier)
    Column(modifier) {
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(pluginString(Strings.preview_ends), style = body.copy(fontStyle = FontStyle.Italic))
        post.canonicalUrl?.let { url -> TextButton(onClick = { taps.onLink(url) }) { Text(pluginString(Strings.continue_on_site)) } }
    }
}

@Composable
internal fun BlockView(block: SubstackBlock, body: TextStyle, highlights: List<String>, keptImages: Map<String, File>, postUrl: String?, taps: ArticleTaps) {
    when (block) {
        is SubstackTextBlock -> TextBlockView(block, body, highlights, taps)
        is SubstackImageBlock -> ImageBlockView(block, body, highlights, keptImages, taps)
        is SubstackEmbedBlock -> OutlinedButton(onClick = { taps.onLink(block.src) }) {
            Icon(SubstackIcons.OpenInNew, null, Modifier.padding(end = 8.dp))
            Text(listOf(pluginString(Strings.load_embed), block.host).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        SubstackWebOnlyBlock -> Column {
            Text(pluginString(Strings.web_only_block), style = body.copy(fontStyle = FontStyle.Italic))
            postUrl?.let { url -> TextButton(onClick = { taps.onLink(url) }) { Text(pluginString(Strings.open_in_browser)) } }
        }
        SubstackDividerBlock -> HorizontalDivider()
        is SubstackTableBlock -> TableView(block.rows)
    }
}

@Composable
private fun TextBlockView(block: SubstackTextBlock, body: TextStyle, highlights: List<String>, taps: ArticleTaps) {
    when (block.kind) {
        SubstackTextBlock.Kind.PARAGRAPH -> Runs(block.runs, body, highlights, taps)
        SubstackTextBlock.Kind.HEADING -> {
            val scale = if (block.level <= 2) 1.35 else 1.15
            val size = (body.fontSize.value * scale).sp
            Runs(block.runs, body.copy(fontSize = size, fontWeight = FontWeight.Bold, lineHeight = (size.value * 1.35).sp), highlights, taps, Modifier.semantics { heading() })
        }
        SubstackTextBlock.Kind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Runs(block.runs, body.copy(fontStyle = FontStyle.Italic), highlights, taps, Modifier.padding(start = 16.dp))
        }
        SubstackTextBlock.Kind.LIST_ITEM, SubstackTextBlock.Kind.FOOTNOTE -> Row(Modifier.padding(start = (20 * block.level).dp)) {
            Text(block.marker.orEmpty(), style = body, modifier = Modifier.width(28.dp))
            Runs(block.runs, body, highlights, taps, Modifier.weight(1f))
        }
        SubstackTextBlock.Kind.CODE -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp).horizontalScroll(rememberScrollState()),
        ) {
            Text(block.text, style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = (14 * 1.4).sp))
        }
    }
}

@Composable
private fun ImageBlockView(image: SubstackImageBlock, body: TextStyle, highlights: List<String>, keptImages: Map<String, File>, taps: ArticleTaps) {
    val width = image.width
    val height = image.height
    val ratio = if (width != null && height != null && height > 0 && width > 0) width.toFloat() / height else 16f / 9f
    Column {
        AsyncImage(
            model = keptImages[image.src] ?: image.src,
            contentDescription = image.alt.ifEmpty { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { taps.onImage(image) },
        )
        if (image.caption.isNotEmpty()) {
            val caption = body.copy(fontSize = (body.fontSize.value * 0.85).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Runs(image.caption, caption, highlights, taps, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TableView(rows: List<List<String>>) {
    val columns = rows.maxOf { it.size }
    val widths = (0 until columns).map { c -> (rows.maxOf { it.getOrNull(c)?.length ?: 0 } * 7.5).coerceIn(64.0, 280.0).dp }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, line)) {
        rows.forEach { row ->
            Row {
                widths.forEachIndexed { c, w ->
                    Text(row.getOrNull(c).orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(w).border(0.5.dp, line).padding(8.dp))
                }
            }
        }
    }
}

/** Styled runs as one paragraph: links and footnote markers tappable, highlights marked. */
@Composable
private fun Runs(runs: List<SubstackRun>, style: TextStyle, highlights: List<String>, taps: ArticleTaps, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = RunColors(scheme.primary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    val text = remember(runs, highlights, colors, taps) { annotated(SubstackHighlights.shown(runs, highlights), colors, taps) }
    Text(text, style = style, color = scheme.onSurface, modifier = modifier)
}

private data class RunColors(val link: Color, val highlight: Color, val onHighlight: Color)

private fun annotated(runs: List<SubstackShownRun>, colors: RunColors, taps: ArticleTaps): AnnotatedString = buildAnnotatedString {
    for ((run, highlighted) in runs) {
        val footnote = run.footnote
        val href = run.href
        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            color = when {
                href != null || footnote != null -> colors.link
                highlighted -> colors.onHighlight
                else -> Color.Unspecified
            },
            background = if (highlighted) colors.highlight else Color.Unspecified,
            textDecoration = if (href != null && footnote == null) TextDecoration.Underline else null,
            baselineShift = if (footnote != null) BaselineShift.Superscript else null,
            fontSize = if (footnote != null) 0.75.em else TextUnit.Unspecified,
        )
        when {
            footnote != null -> withLink(LinkAnnotation.Clickable("footnote-$footnote") { taps.onFootnote(footnote) }) { withStyle(style) { append(run.text) } }
            href != null -> withLink(LinkAnnotation.Clickable(href) { taps.onLink(href) }) { withStyle(style) { append(run.text) } }
            else -> withStyle(style) { append(run.text) }
        }
    }
}

/** The footnotes numbered [number], as the sheet over the article shows them. */
internal fun footnotesOf(blocks: List<SubstackBlock>, number: Int): List<SubstackTextBlock> =
    blocks.filterIsInstance<SubstackTextBlock>().filter { it.kind == SubstackTextBlock.Kind.FOOTNOTE && it.marker == "$number" }
