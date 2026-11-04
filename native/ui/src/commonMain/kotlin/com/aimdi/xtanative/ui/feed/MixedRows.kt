package com.aimdi.xtanative.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.networkProblemText
import org.jetbrains.compose.resources.stringResource

/** The running plugins by id, as they come and go: who draws and opens each plugin's posts. */
@Composable
fun rememberRunningPlugins(): Map<String, XtaPlugin> {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    return remember(running) { running.associate { it.plugin.id to it.plugin } }
}

/**
 * Where a post of plugin [source] leads when the plugin says nothing
 * ([XtaPlugin.postActions]): its link, opened as the app opens links (in the
 * plugin that reads it, else on the web), and its media in the app's viewer.
 */
@Composable
internal fun rememberLinkActions(source: String): NetworkPostActions {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    val plugins = LocalPlugins.current
    return remember(source, links, uri, plugins) {
        val open: (NetworkPost) -> Unit = { post -> post.url?.let { url -> if (links != null) links.open(url) else runCatching { uri.openUri(url) } } }
        NetworkPostActions(
            open = open,
            openAuthor = open,
            openMention = {},
            openTag = {},
            openMedia = { post, index -> plugins.navigator.go(Destination.Media(plugins.media.put(source, post.media), index)) },
        )
    }
}

/**
 * What [plugin] adds to [post]'s shared row ([XtaPlugin.postExtras]: a
 * Mastodon poll's results); nothing when it adds nothing, or fails to say.
 */
@Composable
internal fun rememberPostExtras(plugin: XtaPlugin?, post: NetworkPost): NetworkPostExtras =
    remember(plugin, post) { plugin?.let { runCatching { it.postExtras(post) }.getOrNull() } ?: NetworkPostExtras.None }

/** [plugin]'s own card for [post] ([XtaPlugin.postCard]: RSS's article row); none when it draws none, or fails to say. */
@Composable
internal fun rememberPostCard(plugin: XtaPlugin?, post: NetworkPost): (@Composable () -> Unit)? =
    remember(plugin, post) { plugin?.let { runCatching { it.postCard(post) }.getOrNull() } }

/**
 * A plugin's post among X's: its plugin's own card, with the plugin's colour
 * down its side as XTA's provenance strip, or the shared row with its
 * plugin's mark and what it adds there, leading where its plugin says.
 */
@Composable
fun PluginPostRow(post: SourcedPost, plugin: XtaPlugin?) {
    val markColor = plugin?.brandColor ?: MaterialTheme.colorScheme.primary
    val card = rememberPostCard(plugin, post.post)
    if (card != null) return SourceStrip(provenanceColor(markColor), card)
    val fallback = rememberLinkActions(post.source)
    NetworkPostCard(
        post.post,
        plugin?.postActions ?: fallback,
        rememberPostExtras(plugin, post.post),
        mark = plugin?.icon,
        markColor = markColor,
    )
    Hairline()
}

/** [brand] as a card's strip shows it on the list's ground ([ProvenanceColors]): Threads' near-black is lightened on a dark theme. */
@Composable
private fun provenanceColor(brand: Color): Color {
    val ground = MaterialTheme.colorScheme.background
    return remember(brand, ground) { Color(ProvenanceColors.accent(brand.toArgb(), ground.toArgb())) }
}

/** [content] with [color] down its left edge, two points wide (provenance_accent.dart), where a card without an avatar to mark says where it came from. */
@Composable
private fun SourceStrip(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().drawWithContent {
        drawContent()
        drawRect(color, size = Size(2.dp.toPx(), size.height))
    }) { content() }
}

/**
 * A timeline with nothing from X but posts from plugins (XTA's
 * interleaved-only list): they alone, under why X's did not come ([problem],
 * retried with [onRetry]), and still pulled to refresh.
 */
@Composable
fun PluginPostsOnly(
    posts: List<SourcedPost>,
    plugins: Map<String, XtaPlugin>,
    list: LazyListState,
    contentPadding: PaddingValues,
    problem: Problem?,
    onRetry: () -> Unit,
) {
    val layout = rememberFeedLayout(emptyList(), posts)
    val byKey = remember(posts) { posts.associateBy { it.key } }
    WithReadingColumn(contentPadding) { padding ->
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (problem != null) item(key = "x-problem", contentType = "notice") { ProblemRow(problem, onRetry) }
            items(posts, key = { it.key }, contentType = { "plugin-post" }) { post -> PlacedPluginPost(post, layout, plugins, byKey) {} }
        }
    }
}

/** The plugins whose posts did not come, each with why and a retry, floating over the timeline as XTA's source chips do. */
@Composable
fun SourceProblems(state: PluginPostsState, plugins: Map<String, XtaPlugin>, onRetry: (String) -> Unit, modifier: Modifier = Modifier) {
    val failed = state.failed
    if (failed.isEmpty()) return
    val language = currentLanguage()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        failed.forEach { (id, problem) ->
            val name = remember(id, language, plugins[id]) { runCatching { plugins[id]?.title(language) }.getOrNull() ?: id }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp).widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        networkProblemText(problem, name),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = { onRetry(id) }) { Text(stringResource(Res.string.retry)) }
                }
            }
        }
    }
}

/**
 * The thread at [index] of a timeline, drawn by [thread], with the plugin
 * posts [buckets] place above it, and for the last thread those below it. One
 * column whether there are any or not, so the thread keeps its state as they
 * come. [layout] stacks the posts sharing one article: a plugin's post shown
 * in a stack above is left out, and one that is a stack's first draws the
 * stack, its plugin posts from [posts] and its X threads by [drawThread].
 */
@Composable
fun Placed(
    buckets: List<List<SourcedPost>>,
    index: Int,
    plugins: Map<String, XtaPlugin>,
    layout: FeedLayout,
    posts: Map<String, SourcedPost>,
    drawThread: @Composable (String) -> Unit,
    thread: @Composable () -> Unit,
) {
    val (above, below) = FeedMix.around(buckets, index)
    Column(Modifier.fillMaxWidth()) {
        above.forEach { post -> key(post.key) { PlacedPluginPost(post, layout, plugins, posts, drawThread) } }
        thread()
        below.forEach { post -> key(post.key) { PlacedPluginPost(post, layout, plugins, posts, drawThread) } }
    }
}
