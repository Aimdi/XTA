package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostRef
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.delay

/** The viewer over a list (booru_post_pager.dart); when the list is gone (the app was closed), the post it was on, read alone. */
@Composable
internal fun BooruViewerScreen(app: BooruApp, route: BooruRoute.Viewer) = Bound(app) {
    val source = remember(route.key) { app.viewers.get(route.key) }
    if (source == null) BooruLinkedPostScreen(app, route.ref) else ViewerPages(app, "booru-viewer:" + route.key, source, route.index)
}

/** A post a link named, read from its site (booru_link_open.dart); one the site does not have says so, with its page on the host. */
@Composable
internal fun BooruLinkedPostScreen(app: BooruApp, ref: BooruPostRef) = Bound(app) {
    val linked = rememberKept("booru-linked:${ref.host}:${ref.id}") { scope -> LinkedPost(app, ref, scope) }
    when (val state = linked.state.collectAsState().value) {
        LinkedPost.State.Loading -> PluginScaffold("#" + ref.id, onBack = app.host::back) { padding -> LoadingRow(Modifier.padding(padding)) }
        is LinkedPost.State.Found -> ViewerPages(app, "booru-linked-view:${ref.host}:${ref.id}", state.source, 0)
        is LinkedPost.State.Missing -> MissingPost(app, ref, state.error, linked::load)
    }
}

@Composable
private fun MissingPost(app: BooruApp, ref: BooruPostRef, error: Throwable?, onRetry: () -> Unit) {
    val outside = rememberOutside()
    PluginScaffold("#" + ref.id, onBack = app.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (error != null) {
                RetryMessage(errorText(error), onRetry)
            } else {
                EmptyPane(BooruIcons.BrokenImage, pluginString(Strings.error_not_found)) {
                    BooruRoutes.pageUrl(ref)?.let { page -> Button(onClick = { outside.open(page) }) { Text(pluginString(Strings.open_on_host)) } }
                }
            }
        }
    }
}

/**
 * Swipes sideways through the posts of [source] from [start], keeps going as
 * its feed loads more, and loads the neighbours' pictures ahead once the
 * reader stops on a post. The bar names the post shown and carries its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPages(app: BooruApp, key: String, source: ViewerSource, start: Int) {
    val viewer = rememberKept(key) { scope -> ViewerState(app, source, scope) }
    val posts by viewer.posts.collectAsState()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (posts.size - 1).coerceAtLeast(0))) { posts.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(viewer::show) }
    Preloads(posts, pager.settledPage)
    val current = posts.getOrNull(pager.currentPage) ?: return
    PluginScaffold(
        title = "#" + current.id,
        onBack = app.host::back,
        actions = { PostActions(app, viewer, current) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pager, key = { posts[it].key }, modifier = Modifier.fillMaxSize()) { page ->
                PostDetails(app, viewer, posts[page], padding)
            }
            BooruMessages(app, Modifier.align(Alignment.BottomCenter).padding(padding))
        }
    }
}

/** The neighbours' pictures, asked for once the reader has stopped on a post for a moment, so a fast run of swipes loads nothing ahead. */
@Composable
private fun Preloads(posts: List<BooruPost>, settled: Int) {
    val context = LocalPlatformContext.current
    LaunchedEffect(settled, posts.size) {
        delay(PRELOAD_SETTLE_MILLIS)
        val loader = SingletonImageLoader.get(context)
        BooruPager.preloadUrls(posts, settled).forEach { url -> loader.enqueue(ImageRequest.Builder(context).data(url).build()) }
    }
}

private const val PRELOAD_SETTLE_MILLIS = 300L
