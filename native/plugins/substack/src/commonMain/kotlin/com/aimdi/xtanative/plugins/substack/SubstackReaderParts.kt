package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackHighlights
import com.aimdi.xtanative.networks.substack.SubstackPassage
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberShareText

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
 * selected, and [speakLabel] ("Speak from here") when [onSpeak] is given:
 * the desktop's right-click menu, Android's selection toolbar. [onHighlight]
 * gets the selected text; [onSpeak] reads aloud from the paragraph held.
 */
@Composable
internal expect fun HighlightingSelection(
    label: String,
    onHighlight: (String) -> Unit,
    speakLabel: String,
    onSpeak: (() -> Unit)?,
    content: @Composable () -> Unit,
)

/** The sheets over an article: contents and find (a passage chosen goes to [onJump]), its highlights, a footnote. */
@Composable
internal fun ReaderSheets(app: SubstackApp, post: SubstackPost, article: ReaderContent.Article, sheet: ReaderSheet?, onDismiss: () -> Unit, onJump: (SubstackPassage) -> Unit) {
    when (sheet) {
        is ReaderSheet.Contents -> ContentsSheet(article, sheet.find, onDismiss) { passage ->
            onDismiss()
            onJump(passage)
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
