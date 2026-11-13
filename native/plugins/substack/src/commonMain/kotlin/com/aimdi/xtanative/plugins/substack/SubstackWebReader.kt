package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.aimdi.xtanative.networks.substack.SubstackPageColors
import com.aimdi.xtanative.networks.substack.SubstackPageWords
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackReading
import com.aimdi.xtanative.networks.substack.SubstackRoutes
import com.aimdi.xtanative.networks.substack.SubstackWebNavigation
import com.aimdi.xtanative.networks.substack.SubstackWebPage
import com.aimdi.xtanative.networks.substack.SubstackWebStep
import com.aimdi.xtanative.services.speech.textFromHere
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.speech.LocalSpeech

/**
 * Whether articles can show in a web view here, as XTA's reader shows them
 * unless its "Native reader (preview)" is on: Android. The desktop always
 * reads natively.
 */
internal expect val articleWebView: Boolean

/** What the web view shows: the article's page as the reader writes it, or the publication's own page. */
internal sealed interface WebPage {
    data class Article(val html: String, val baseUrl: String?) : WebPage

    data class Live(val url: String) : WebPage
}

/** How the reader reaches the page while it is shown: [run] runs a script in it. */
internal class WebPageControl {
    var run: (String) -> Unit = {}
}

/** What the page tells the reader. Only the article's own page posts [onReading] and [onSpeakFrom]; the live page has no channels. */
internal class WebPageEvents(
    /** Before the page leaves for [url] (its main frame): true keeps it where it is. */
    val onNavigate: (url: String) -> Boolean,
    val onLoaded: () -> Unit,
    val onFailed: (String) -> Unit,
    val onReading: (String) -> Unit = {},
    val onSpeakFrom: (String) -> Unit = {},
    /** The live page's prose, once it has loaded. */
    val onLiveText: (String) -> Unit = {},
)

/**
 * [page] in the platform's web view. On the article's page, the selection's
 * menu also offers [speakLabel], which reads aloud from the selected
 * paragraph through `XtaTts`.
 */
@Composable
internal expect fun SubstackWebView(page: WebPage, speakLabel: String, control: WebPageControl, events: WebPageEvents, modifier: Modifier)

/**
 * The article in the web view, as XTA's reader shows it by default: its
 * page as [SubstackWebPage] writes it (made to read offline when it is
 * kept), where embedded players load and play in place, the place and type
 * every reader shares, contents and find through [control], and "Speak from
 * here" on a selection.
 */
@Composable
internal fun WebArticle(app: SubstackApp, model: ReaderModel, state: ReaderModel.State, content: ReaderContent.Article, control: WebPageControl, modifier: Modifier) {
    val post = state.post
    val reading = state.reading ?: return
    val speech = LocalSpeech.current
    val speakText by rememberUpdatedState(state.speakText)
    val fontScale = LocalDensity.current.fontScale
    val colors = pageColors()
    val words = pageWords(post, content)
    val font by app.host.settings.observe(SubstackPrefs.readerFont).collectAsState(SubstackPrefs.readerFont.default)
    val column by app.host.settings.observe(SubstackPrefs.columnWidth).collectAsState(SubstackPrefs.columnWidth.default)
    val page by produceState<WebPage?>(null, content, colors, words, font, column) {
        val type = reading.state.value
        val html = SubstackWebPage.wrap(post, content.article.body, colors, words, font, column, type.fontSize * fontScale, type.lineHeight)
        value = WebPage.Article(app.host.offline.renderArticle(SubstackOffline.idOf(post), html), post.canonicalUrl)
    }
    val shown = page ?: return Box(modifier.fillMaxSize()) { Spinner() }
    var loaded by remember(shown) { mutableStateOf(false) }
    var failed by remember(shown) { mutableStateOf<String?>(null) }
    val type by reading.state.collectAsState()
    LaunchedEffect(shown, loaded, type.fontSize, type.lineHeight) {
        if (loaded) control.run(SubstackWebPage.readingBridge(reading.state.value.point, type.fontSize * fontScale, type.lineHeight))
    }
    val events = remember(shown, post, speech) {
        WebPageEvents(
            onNavigate = { url -> navigate(app, post, live = false, url, control) },
            onLoaded = { loaded = true },
            onFailed = { failed = it },
            onReading = { message -> if (failed == null) reading.receiveProgress(message) },
            onSpeakFrom = { needle -> speech?.speak(post.title, textFromHere(speakText, needle)) },
        )
    }
    failed?.let { return ReaderFailure(post, IllegalStateException(it)) { model.load(force = false) } }
    Box(modifier.fillMaxSize()) {
        SubstackWebView(shown, pluginString(Strings.speak_from_here), control, events, Modifier.fillMaxSize())
        if (!loaded) Spinner()
    }
}

/** The publication's own page, for a post Substack gave no body but an address, as XTA's reader opens it; Listen reads its prose. */
@Composable
internal fun WebLive(app: SubstackApp, model: ReaderModel, post: SubstackPost, url: String, control: WebPageControl, modifier: Modifier) {
    val page = remember(url) { WebPage.Live(url) }
    var loaded by remember(page) { mutableStateOf(false) }
    var failed by remember(page) { mutableStateOf<String?>(null) }
    val events = remember(page, post) {
        WebPageEvents(
            onNavigate = { target -> navigate(app, post, live = true, target, control) },
            onLoaded = { loaded = true },
            onFailed = { failed = it },
            onLiveText = model::liveText,
        )
    }
    failed?.let { return ReaderFailure(post, IllegalStateException(it)) { model.load(force = false) } }
    Box(modifier.fillMaxSize()) {
        SubstackWebView(page, pluginString(Strings.speak_from_here), control, events, Modifier.fillMaxSize())
        if (!loaded) Spinner()
    }
}

@Composable
private fun Spinner() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

/** Follows [SubstackWebNavigation]'s step for [url]: true when the page stays where it is. */
private fun navigate(app: SubstackApp, post: SubstackPost, live: Boolean, url: String, control: WebPageControl): Boolean =
    when (val step = SubstackWebNavigation.step(url, post, live, app.publications.value.orEmpty().map { it.baseUrl })) {
        SubstackWebStep.Follow -> false
        is SubstackWebStep.Jump -> true.also { control.run(SubstackWebPage.jump(step.anchor, publisherAnchor = true)) }
        is SubstackWebStep.OpenPost -> true.also { app.host.open(SubstackRoutes.post(step.link)) }
        SubstackWebStep.OpenLink -> true.also { app.host.openLink(url) }
    }

@Composable
private fun pageColors(): SubstackPageColors {
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(scheme.surface, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, rtl) {
        SubstackPageColors(css(scheme.surface), css(scheme.onSurface), css(scheme.onSurfaceVariant), css(scheme.primary), scheme.surface.luminance() < 0.5f, rtl)
    }
}

@Composable
private fun pageWords(post: SubstackPost, content: ReaderContent.Article): SubstackPageWords {
    val minutes = SubstackReading.readMinutes(post, content.article.plainText)
    val preview = content.preview
    return SubstackPageWords(
        readTime = minutes?.let { pluginPlural(Strings.read_minutes, it, it) },
        footer = if (preview) pluginString(Strings.preview_ends) else null,
        footerLink = if (preview) post.canonicalUrl else null,
        footerLinkLabel = if (preview) pluginString(Strings.continue_on_site) else null,
        embedLabel = pluginString(Strings.load_embed),
        webOnlyLabel = pluginString(Strings.web_only_block),
        openLabel = pluginString(Strings.open_in_browser),
    )
}

private fun css(color: Color): String = "#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')
