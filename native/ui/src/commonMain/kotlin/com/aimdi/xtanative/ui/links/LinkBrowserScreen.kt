package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.services.links.LinkBrowserAction
import com.aimdi.xtanative.services.links.LinkBrowserSession
import com.aimdi.xtanative.services.links.LinkBrowserState
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_close
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_copy
import com.aimdi.xtanative.ui.generated.resources.reader_link_not_secure
import com.aimdi.xtanative.ui.generated.resources.reader_link_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.reader_link_reload
import com.aimdi.xtanative.ui.generated.resources.reader_link_secure
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.reader_more_options
import com.aimdi.xtanative.ui.media.ReaderIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Reload and back for the page under XTA's browser screen; the platform's web view fills them in. */
class LinkPageControl {
    var reload: () -> Unit = {}
}

/**
 * The page at [url] in a web view, its events going to [session]. Before the
 * page navigates to an http(s) address, [guard] is asked whether an XTA
 * screen opened it instead, and then the page stays; schemes no web view
 * should follow are refused (link_browser_page.dart). The desktop has no web
 * view: there the page opens in the system browser.
 */
@Composable
internal expect fun LinkWebPage(url: String, session: LinkBrowserSession, guard: (String) -> Boolean, control: LinkPageControl, modifier: Modifier)

/**
 * A web page read inside XTA, with the post it came from kept in reach
 * (link_browser_screen.dart): the bar at the top says where the page is, the
 * one at the bottom where it came from, and goes back there. Leaving for a
 * real browser stays one tap away in the menu. [from] names what it was
 * opened with ([LinkOpener.opening]).
 */
@Composable
fun LinkBrowserScreen(url: String, from: String?, onClose: () -> Unit) {
    val links = LocalLinks.current
    val opening = remember(from) { links?.opening(from) }
    val prepared = remember(url) { links?.prepare(url) ?: url }
    val session = remember(prepared) { LinkBrowserSession(prepared) }
    DisposableEffect(session) { onDispose { session.close() } }
    val state by session.state.collectAsStateWithLifecycle()
    val control = remember { LinkPageControl() }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(Res.string.reader_link_copied)

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar(copied) }
    }

    fun act(action: LinkBrowserAction) {
        val current = links?.prepare(state.url) ?: state.url
        when (action) {
            LinkBrowserAction.OPEN_EXTERNALLY -> links?.leaveBrowserScreen(current)
            LinkBrowserAction.COPY_LINK -> copy(current)
            LinkBrowserAction.SHARE -> if (links?.share(current) != true) copy(current)
            LinkBrowserAction.RELOAD -> control.reload()
        }
    }

    val subtitle = opening?.from?.post?.network?.let { networkName(it) } ?: opening?.title?.trim()?.ifEmpty { null }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                BrowserTopBar(state, subtitle, onClose, ::act)
                BrowserProgress(state)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LinkWebPage(prepared, session, { target -> links?.openOnScreen(target) == true }, control, Modifier.fillMaxSize())
            opening?.from?.let { from ->
                val postUrl = from.post.postUrl
                LinkPostContextBar(
                    post = from.post,
                    onBackToPost = {
                        onClose()
                        from.openPost?.invoke()
                    },
                    onSharePost = postUrl?.let { shared -> { if (links?.share(shared) != true) copy(shared) } },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
        }
    }
}

/** ✕, the padlock and the domain over where the link came from, and ⋯. */
@Composable
private fun BrowserTopBar(state: LinkBrowserState, subtitle: String?, onClose: () -> Unit, onAction: (LinkBrowserAction) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.reader_close)) }
        Column(Modifier.weight(1f).semantics { heading() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.secure) ReaderIcons.Lock else ReaderIcons.LockOpen,
                    contentDescription = stringResource(if (state.secure) Res.string.reader_link_secure else Res.string.reader_link_not_secure),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(state.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BrowserMenu(onAction)
    }
}

@Composable
private fun BrowserMenu(onAction: (LinkBrowserAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                ReaderIcons.MoreHoriz,
                contentDescription = stringResource(Res.string.reader_more_options),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(3.dp).size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            browserActions.forEach { (action, icon, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

/** The menu, in XTA's order. */
private val browserActions: List<Triple<LinkBrowserAction, ImageVector, StringResource>>
    get() = LinkBrowserAction.entries.map { action ->
        when (action) {
            LinkBrowserAction.OPEN_EXTERNALLY -> Triple(action, ReaderIcons.OpenInNew, Res.string.reader_link_open_in_browser)
            LinkBrowserAction.COPY_LINK -> Triple(action, XIcons.Link, Res.string.reader_link_copy)
            LinkBrowserAction.SHARE -> Triple(action, Icons.Filled.Share, Res.string.reader_link_share)
            LinkBrowserAction.RELOAD -> Triple(action, Icons.Filled.Refresh, Res.string.reader_link_reload)
        }
    }

/** A thin line that fills as the page loads, and a hairline once it has. */
@Composable
private fun BrowserProgress(state: LinkBrowserState) {
    Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.BottomCenter) {
        when {
            !state.loading -> Hairline()
            state.progress > 0 -> LinearProgressIndicator(progress = { state.progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(2.dp))
            else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
