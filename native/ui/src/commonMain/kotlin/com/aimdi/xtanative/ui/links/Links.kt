package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.aimdi.xtanative.ui.generated.resources.reader_link_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.reader_link_report
import com.aimdi.xtanative.ui.generated.resources.reader_link_unable
import com.aimdi.xtanative.ui.generated.resources.reader_link_unable_details
import com.aimdi.xtanative.ui.media.ReaderIcons
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

/** Hands [onText] whatever another app shares to this one, and the Pixiv pages it opens here. Only Android is shared to. */
@Composable
internal expect fun SharedTextEffect(onText: (String) -> Unit)

/** Hands [onLink] each link another app opens here: X's, its short links and mirrors, Pixiv's pages. Only Android is handed links. */
@Composable
internal expect fun IncomingLinkEffect(onLink: (String) -> Unit)

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
 * else a search on X. A link another app opens here goes as
 * [IncomingLinks] says, and one nothing reads asks the reader what to do.
 */
@Composable
fun BindLinkScreens(screens: LinkScreens) {
    val opener = LocalLinks.current ?: return
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var unreadable by remember { mutableStateOf<String?>(null) }
    DisposableEffect(opener, screens) {
        opener.bind(screens)
        onDispose { opener.unbind(screens) }
    }
    IncomingLinkEffect { url ->
        scope.launch {
            val step = IncomingLinks.step(url, opener::openOnNetwork) { short -> SharedLinks.resolve(short, SharedHttp.http) }
            IncomingLinks.open(step, screens, outside = opener::openOutside, postLink = { link -> opener.openPostLink(link) }, unable = { unreadable = it })
        }
    }
    unreadable?.let { url ->
        UnableToOpenLink(
            onReport = { opener.openOutside(IncomingLinks.ISSUES_URL) },
            onOpenInBrowser = {
                unreadable = null
                opener.openOutside(url)
            },
            onDismiss = { unreadable = null },
        )
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

/** XTA's dialog for a link handed here that it cannot read: report it, or open it in the browser. */
@Composable
private fun UnableToOpenLink(onReport: () -> Unit, onOpenInBrowser: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(ReaderIcons.ErrorOutline, contentDescription = null) },
        title = { Text(stringResource(Res.string.reader_link_unable)) },
        text = { Text(stringResource(Res.string.reader_link_unable_details)) },
        dismissButton = { TextButton(onClick = onReport) { Text(stringResource(Res.string.reader_link_report)) } },
        confirmButton = { TextButton(onClick = onOpenInBrowser) { Text(stringResource(Res.string.reader_link_open_in_browser)) } },
    )
}

/** What follows a shared short link: one request at a time, never to another host. */
private object SharedHttp {
    val http by lazy { OkHttpXHttp() }
}
