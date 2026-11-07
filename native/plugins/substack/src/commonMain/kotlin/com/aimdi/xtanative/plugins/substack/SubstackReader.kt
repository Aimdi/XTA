package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackHighlights
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackImageBlock
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPostLink
import com.aimdi.xtanative.networks.substack.SubstackPosts
import com.aimdi.xtanative.networks.substack.SubstackReading
import com.aimdi.xtanative.networks.substack.SubstackSpeakFrom
import com.aimdi.xtanative.networks.substack.SubstackWebPage
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.offline.OfflineArticleAction
import com.aimdi.xtanative.ui.plugins.ArticleReaderControls
import com.aimdi.xtanative.ui.plugins.KeepsReaderPlace
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.speech.LocalSpeech
import com.aimdi.xtanative.ui.speech.ReadAloudButton
import com.aimdi.xtanative.ui.speech.VoiceSettingsSheet
import kotlinx.coroutines.launch

/**
 * A post in the reader, as XTA's SubstackReaderScreen: on Android the
 * article in a web view unless "Native reader (preview)" is on, as XTA's
 * default; on the desktop, and with that switch, the article as blocks.
 * Either way it is kept where the reader left it, with the type every reader
 * shares; its episode's player; contents and find; highlights (native);
 * Listen, and "Speak from here" on a paragraph held; kept offline; saved,
 * liked and marked read on this device; its discussion, its publication, and
 * its page in the browser. A post with no body but an address shows the
 * publication's page in the web view, or offers it in the browser. A paid
 * post without a body shows the paid pane, never the publisher's scripts.
 */
@Composable
internal fun SubstackReaderScreen(app: SubstackApp, link: SubstackPostLink, key: String?) {
    val model = rememberKept("substack-reader-${link.publicationBase}/${link.slug}") { scope -> ReaderModel(app, app.postFor(link, key), scope) }
    val state by model.state.collectAsState()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val article = state.content as? ReaderContent.Article
    var sheet by rememberSaveable { mutableStateOf<ReaderSheet?>(null) }
    val nativeReader by app.host.settings.observe(SubstackPrefs.nativeReader).collectAsState(SubstackPrefs.nativeReader.default)
    val web = articleWebView && !nativeReader
    val page = remember { WebPageControl() }
    val reading = state.reading
    DisposableEffect(reading, article != null) {
        reading?.setActive(article != null)
        onDispose { reading?.setActive(false) }
    }
    PluginScaffold(title = state.post.title, onBack = app.host::back, actions = { ReaderActions(app, model, state, web, onSheet = { sheet = it }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (article?.preview == true) {
                Text(pluginString(Strings.preview_badge), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            reading?.let {
                ArticleReaderControls(it, supportsAppearance = article != null, canComplete = state.content !is ReaderContent.Loading) {
                    if (web) page.run(SubstackWebPage.START_OVER) else scope.launch { list.scrollToItem(0) }
                }
            }
            Box(Modifier.weight(1f)) {
                ReaderBody(app, model, state, list, web, page) { sheet = it }
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.refreshError?.let {
                    FailureRow(it, { model.load(force = true) }, Modifier.align(Alignment.TopCenter), pluginString(Strings.refresh_failed))
                }
            }
        }
    }
    article?.let { content ->
        ReaderSheets(app, state.post, content, sheet, onDismiss = { sheet = null }) { passage ->
            if (web) {
                page.run(SubstackWebPage.jump(passage.anchor))
            } else {
                content.article.blockOf(passage)?.let { block -> scope.launch { list.animateScrollToItem(ROWS_BEFORE_BLOCKS + block) } }
            }
        }
    }
}

@Composable
private fun ReaderBody(app: SubstackApp, model: ReaderModel, state: ReaderModel.State, list: LazyListState, web: Boolean, page: WebPageControl, onSheet: (ReaderSheet) -> Unit) {
    val post = state.post
    when (val content = state.content) {
        ReaderContent.Loading -> LoadingRow()
        is ReaderContent.Failed -> ReaderFailure(post, content.error) { model.load(force = false) }
        ReaderContent.Paywall -> PostPane(post, Icons.Outlined.Lock, Strings.paywalled_title, Strings.paywalled_description)
        ReaderContent.Live -> {
            val url = post.canonicalUrl
            if (articleWebView && url != null) WebLive(app, model, post, url, page, Modifier.fillMaxSize()) else PostPane(post, SubstackIcons.OpenInNew, null, null)
        }
        ReaderContent.Empty -> EmptyState(pluginString(Strings.no_content))
        is ReaderContent.Article -> Column(Modifier.fillMaxSize()) {
            post.audioUrl?.takeIf { post.isPodcast }?.let { SubstackAudioPlayer(app, it, post.title) }
            if (web) WebArticle(app, model, state, content, page, Modifier.weight(1f)) else ReaderArticle(app, state, content, list, onSheet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderArticle(app: SubstackApp, state: ReaderModel.State, content: ReaderContent.Article, list: LazyListState, onSheet: (ReaderSheet) -> Unit, modifier: Modifier) {
    val post = state.post
    val reading = state.reading ?: return
    val type by reading.state.collectAsState()
    val font by app.host.settings.observe(SubstackPrefs.readerFont).collectAsState(SubstackPrefs.readerFont.default)
    val column by app.host.settings.observe(SubstackPrefs.columnWidth).collectAsState(SubstackPrefs.columnWidth.default)
    val highlights by app.highlights.state.collectAsState()
    val blocks = content.article.blocks
    val images = remember(blocks) { blocks.filterIsInstance<SubstackImageBlock>() }
    val speech = LocalSpeech.current
    val speakText by rememberUpdatedState(state.speakText)
    var pressed by remember(blocks) { mutableIntStateOf(-1) }
    val taps = remember(app, post, images) {
        ArticleTaps(
            onLink = { url -> if (SubstackReading.articleFragment(url, post.canonicalUrl) == null) app.openLink(url) },
            onFootnote = { number -> onSheet(ReaderSheet.Footnote(number)) },
            onImage = { image -> app.host.openMedia(images.map(::mediaOf), images.indexOf(image).coerceAtLeast(0)) },
            onPress = { pressed = it },
        )
    }
    KeepsReaderPlace(reading, list, before = ROWS_BEFORE_BLOCKS, blocks = blocks.size)
    HighlightingSelection(
        pluginString(Strings.highlight),
        onHighlight = { selected -> app.launch { app.highlights.add(post, SubstackHighlights.quotes(selected)) } },
        speakLabel = pluginString(Strings.speak_from_here),
        onSpeak = speech?.let { voice -> { voice.speak(post.title, SubstackSpeakFrom.fromBlock(speakText, blocks, pressed)) } },
    ) {
        ArticleList(
            post = post,
            blocks = blocks,
            readMinutes = SubstackReading.readMinutes(post, content.article.plainText),
            preview = content.preview,
            look = ArticleLook(font, column, type.fontSize, type.lineHeight),
            highlights = highlights[post.key].orEmpty(),
            keptImages = content.keptImages,
            taps = taps,
            list = list,
            modifier = modifier,
        )
    }
}

private fun mediaOf(image: SubstackImageBlock): MediaItem {
    val width = image.width
    val height = image.height
    val ratio = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
    return MediaItem(imageUrl = image.src, aspectRatio = ratio, alt = image.alt.ifEmpty { null })
}

/** A post that could not be read: why, again, and its page in the browser. */
@Composable
internal fun ReaderFailure(post: SubstackPost, error: Throwable, onRetry: () -> Unit) {
    val outside = rememberOpenOutside()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        NetworkProblemView(problemOf(error), NETWORK, onRetry)
        post.canonicalUrl?.let { url -> Button(onClick = { outside(url) }) { Text(pluginString(Strings.open_in_browser)) } }
    }
}

/**
 * A post with nothing to draw here: the paid pane (XTA's _PaywallPane) for a
 * subscriber-only post, or, for one Substack gave no body but an address, its
 * title and excerpt with its page in the browser.
 */
@Composable
private fun PostPane(post: SubstackPost, icon: ImageVector, title: PluginText?, description: PluginText?) {
    val outside = rememberOpenOutside()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title?.let { pluginString(it) } ?: post.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        description?.let { Text(pluginString(it), textAlign = TextAlign.Center) }
        post.excerpt?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
        post.canonicalUrl?.let { url ->
            Button(onClick = { outside(url) }, modifier = Modifier.padding(top = 12.dp)) {
                Icon(SubstackIcons.OpenInNew, null, Modifier.padding(end = 8.dp))
                Text(pluginString(Strings.open_in_browser))
            }
        }
    }
}

/** The reader's bar: keep offline, save, Listen, and its menu, in XTA's order. */
@Composable
private fun ReaderActions(app: SubstackApp, model: ReaderModel, state: ReaderModel.State, web: Boolean, onSheet: (ReaderSheet) -> Unit) {
    val post = state.post
    val saves by app.saves.state.collectAsState()
    val saved = saves.any { SubstackPosts.same(it, post) }
    // What is spoken always starts with the title: gathering it is left to the button.
    val canSpeak = state.content !is ReaderContent.Empty && post.title.isNotBlank()
    var voices by remember { mutableStateOf(false) }
    if (!post.bodyHtml.isNullOrBlank()) OfflineArticleAction(SubstackOffline.articleOf(post))
    IconButton(onClick = { app.launch { app.saves.toggle(post) } }) {
        Icon(if (saved) SubstackIcons.Bookmark else SubstackIcons.BookmarkBorder, pluginString(if (saved) Strings.unsave else Strings.save))
    }
    if (canSpeak) ReadAloudButton(post.title, { state.speakText })
    ReaderMenu(app, model, state, canSpeak, web, onSheet) { voices = true }
    if (voices) VoiceSettingsSheet { voices = false }
}

@Composable
private fun ReaderMenu(app: SubstackApp, model: ReaderModel, state: ReaderModel.State, canSpeak: Boolean, web: Boolean, onSheet: (ReaderSheet) -> Unit, onVoices: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val likes by app.likes.state.collectAsState()
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val post = state.post
    val liked = likes.any { SubstackPosts.same(it, post) }
    val article = state.content is ReaderContent.Article
    fun item(icon: ImageVector, label: String, action: () -> Unit): @Composable () -> Unit = {
        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { open = false; action() })
    }
    val items = buildList {
        if (article && !web) add(item(SubstackIcons.Quote, pluginString(Strings.highlights)) { onSheet(ReaderSheet.Highlights) })
        if (article) add(item(SubstackIcons.Contents, pluginString(Strings.contents)) { onSheet(ReaderSheet.Contents(find = false)) })
        if (article) add(item(SubstackIcons.Find, pluginString(Strings.find)) { onSheet(ReaderSheet.Contents(find = true)) })
        if (state.content !is ReaderContent.Loading && !state.refreshing) add(item(Icons.Default.Refresh, pluginString(Strings.refresh)) { model.load(force = true) })
        add(item(SubstackIcons.Comment, pluginString(Strings.comments)) { app.openComments(post) })
        add(item(SubstackIcons.Newspaper, pluginString(Strings.publication)) { app.openPublication(SubstackHomeRules.publicationOf(post)) })
        add(item(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, pluginString(if (liked) Strings.unlike else Strings.like)) { app.launch { app.likes.toggle(post) } })
        if (canSpeak) add(item(SubstackIcons.Tune, pluginString(Strings.tts_settings), onVoices))
        post.canonicalUrl?.let { url ->
            add(item(Icons.Default.Share, pluginString(Strings.share_link)) { share("${post.title}\n$url") })
            add(item(SubstackIcons.OpenInNew, pluginString(Strings.open_in_browser)) { outside(url) })
        }
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items.forEach { it() } }
    }
}
