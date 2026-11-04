package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.services.links.NetworkLink
import com.aimdi.xtanative.services.links.SharedLinks
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_link_unable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The app's one way to open a link, for every screen: [LinkOpener.open] for
 * a link in a post's text or anywhere else, [LinkOpener.openPostLink] for a
 * card's, with the post it came from, [LinkOpener.openOutside] to leave the
 * app. Null only outside [LinksFrame].
 */
val LocalLinks = staticCompositionLocalOf<LinkOpener?> { null }

/** [LocalLinks]'s [LinkOpener.open], for a screen that only opens links. */
@Composable
fun rememberOpenLink(): (String) -> Unit {
    val links = LocalLinks.current
    return remember(links) { { url -> links?.open(url) } }
}

/** What the platform does with links that leave the app: Custom Tabs and browsers on Android, the system browser on the desktop. */
@Composable
internal expect fun rememberLinkPlatform(): LinkPlatform

/** Hands [onText] whatever another app shares to this one. Only Android is shared to. */
@Composable
internal expect fun SharedTextEffect(onText: (String) -> Unit)

/**
 * The frame the app's screens open links in: one [LinkOpener] for all of
 * them, and the links Compose draws in text (which open through the
 * [LocalUriHandler]) going through it, so a link in a post opens what XTA
 * would open. What it has to tell the reader shows at the bottom.
 */
@Composable
fun LinksFrame(content: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val platform = rememberLinkPlatform()
    val opener = remember(graph, platform) { LinkOpener(platform, { graph.settings.state.value }) }
    val handler = remember(opener) {
        object : UriHandler {
            override fun openUri(uri: String) = opener.open(uri)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val unable = stringResource(Res.string.reader_link_unable)
    LaunchedEffect(graph) { runCatching { graph.settings.load() } }
    LaunchedEffect(opener) {
        opener.notices.collect { notice ->
            when (notice) {
                LinkNotice.UNABLE_TO_OPEN -> snackbar.showSnackbar(unable)
            }
        }
    }
    CompositionLocalProvider(LocalLinks provides opener, LocalUriHandler provides handler) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
        }
    }
}

/**
 * Makes [screens] the ones links open on, while this is composed, and opens
 * what another app shares here (main.dart's shared text): a network's
 * screen, an X link once its short link is followed, a page as a post's link,
 * else a search on X.
 */
@Composable
fun BindLinkScreens(screens: LinkScreens) {
    val opener = LocalLinks.current ?: return
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    DisposableEffect(opener, screens) {
        opener.bind(screens)
        onDispose { opener.unbind(screens) }
    }
    SharedTextEffect { text ->
        scope.launch {
            val settings = runCatching { graph.settings.load() }.getOrNull()
            val pixiv = screens.pixivWork != null && LinkSteps.networkEnabled(settings, NetworkLink.PIXIV)
            val step = SharedText.step(text, pixiv, opener::openOnNetwork) { shared -> SharedLinks.resolve(shared, SharedHttp.http, pixiv) }
            when (step) {
                SharedStep.Opened -> Unit
                is SharedStep.Link -> if (step.inPost) opener.openPostLink(step.url) else opener.open(step.url)
                is SharedStep.Outside -> opener.openOutside(step.url)
                is SharedStep.Search -> screens.search(step.query)
                is SharedStep.PixivWork -> screens.pixivWork?.invoke(step.id)
                SharedStep.Unable -> opener.notify(LinkNotice.UNABLE_TO_OPEN)
            }
        }
    }
}

/** What follows a shared short link: one request at a time, never to another host. */
private object SharedHttp {
    val http by lazy { OkHttpXHttp() }
}
