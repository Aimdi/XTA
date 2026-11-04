package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.ui.post.PostIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ReaderBlock
import com.aimdi.xtanative.networks.ReaderHtml
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.store.reading.ArticleReading
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.plugin_rss_no_article
import com.aimdi.xtanative.ui.generated.resources.reader_link_open_in_browser
import com.aimdi.xtanative.ui.offline.OfflineArticleAction
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The RSS reader (rss_reader_screen.dart), where the RSS plugin and web
 * sources open an article: its copy kept offline when there is one, else
 * [item] as it came; its text drawn here with the type every reader shares,
 * kept where the reader left it and finished ([onRead]) when read to its
 * end; kept offline; its page in the browser. An item that only linked its
 * page says so and opens it. [read] is whether it was read before; what
 * outlives the screen (the last place written) runs in [scope]. [actions]
 * go in the bar between Keep offline and Open in browser, as XTA's bar
 * orders them: RSS's Add to group for the item's feed.
 */
@Composable
fun RssReaderScreen(
    host: PluginHost,
    item: RssItem,
    read: Boolean,
    scope: CoroutineScope,
    actions: @Composable RowScope.() -> Unit = {},
    onRead: suspend () -> Unit,
) {
    val model = rememberKept("rss-reader-${RssOffline.idOf(item)}") { screen -> RssReaderModel(host, item, read, onRead, scope, screen) }
    val state by model.state.collectAsState()
    val outside = rememberOpenOutside()
    val shown = state.item
    val reading = state.reading
    DisposableEffect(reading) {
        reading?.setActive(shown.hasReadableBody)
        onDispose { reading?.setActive(false) }
    }
    PluginScaffold(title = shown.feedTitle, onBack = host::back, actions = {
        if (shown.hasReadableBody) OfflineArticleAction(RssOffline.articleOf(shown))
        actions()
        shown.link?.let { link -> IconButton(onClick = { outside(link) }) { Icon(PostIcons.OpenInNew, stringResource(Res.string.reader_link_open_in_browser)) } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                reading == null -> LoadingRow()
                shown.hasReadableBody -> ReaderArticle(host, state, reading)
                else -> ReaderFallback(shown, reading) { link -> outside(link) }
            }
        }
    }
}

@Composable
private fun ReaderArticle(host: PluginHost, state: RssReaderModel.State, reading: ArticleReading) {
    val list = rememberLazyListState()
    val coroutines = rememberCoroutineScope()
    val type by reading.state.collectAsState()
    val blocks = state.blocks
    ArticleReaderControls(reading, supportsAppearance = true, canComplete = true) { coroutines.launch { list.scrollToItem(0) } }
    if (blocks == null) return LoadingRow()
    val images = blocks.filterIsInstance<ReaderBlock.Image>()
    KeepsReaderPlace(reading, list, before = 1, blocks = blocks.size)
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                state.item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        readerBlocks(
            blocks,
            ReaderLook(type.fontSize, type.lineHeight),
            onLink = host::openLink,
            onImage = { index -> host.openMedia(images.map { MediaItem(imageUrl = it.src, alt = it.alt) }, index) },
            kept = state.keptImages,
        )
    }
}

/** An item that only linked its page: its title, who and when, its excerpt, and the page. */
@Composable
private fun ReaderFallback(item: RssItem, reading: ArticleReading, open: (String) -> Unit) {
    ArticleReaderControls(reading, supportsAppearance = false, canComplete = true) {}
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val byline = listOfNotNull(item.author, item.publishedAt?.let { agoText(it) }).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Text(byline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            item.excerpt?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(Res.string.plugin_rss_no_article))
            item.link?.let { link ->
                Button(onClick = { open(link) }, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(PostIcons.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.reader_link_open_in_browser))
                }
            }
        }
    }
}

/**
 * One item open in the RSS reader, as XTA's _RssReaderScreenState loads it:
 * the copy kept offline replaces the one it came with, its pictures kept
 * there are drawn from the device, its body is read into blocks away from
 * the screen's thread, and its place, type and read mark go through the
 * app's [ArticleReading] under XTA's id for it (`rss:<feed>:<item>`).
 */
internal class RssReaderModel(
    private val host: PluginHost,
    opened: RssItem,
    private val read: Boolean,
    private val onRead: suspend () -> Unit,
    private val outlives: CoroutineScope,
    screen: CoroutineScope,
) {
    data class State(
        val item: RssItem,
        val blocks: List<ReaderBlock>? = null,
        val keptImages: Map<String, File> = emptyMap(),
        val reading: ArticleReading? = null,
    )

    private val mutable = MutableStateFlow(State(opened))
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        screen.launch {
            val id = RssOffline.idOf(opened)
            val item = runCatching { host.offline.article(id) }.getOrNull()?.let(RssOffline::itemOf) ?: opened
            val kept = runCatching { host.offline.articleImages(id) }.getOrDefault(emptyMap())
            val reading = open(item)
            mutable.value = State(item, reading = reading, keptImages = kept)
            val blocks = withContext(Dispatchers.Default) { ReaderHtml.blocks(item.bodyHtml.orEmpty(), item.link) }
            mutable.value = mutable.value.copy(blocks = blocks)
        }
        screen.coroutineContext[Job]?.invokeOnCompletion {
            state.value.reading?.let { reading -> outlives.launch(NonCancellable) { reading.close() } }
        }
    }

    private suspend fun open(item: RssItem): ArticleReading = ArticleReading.open(
        journal = host.readingJournal,
        appearance = host.readingAppearance,
        articleId = RssOffline.idOf(item),
        scope = outlives,
        onCompleted = onRead,
        remembers = { host.settings.state.value?.get(PostsPrefs.rememberReadingPosition) != false },
        alreadyCompleted = read,
        allowAutomaticCompletion = item.hasReadableBody,
    )
}

