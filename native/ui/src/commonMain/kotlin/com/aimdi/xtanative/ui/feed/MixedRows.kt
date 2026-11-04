package com.aimdi.xtanative.ui.feed

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.ProblemRow
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

/** A plugin's post among X's: the shared row with its plugin's mark, leading where its plugin says. */
@Composable
fun PluginPostRow(post: SourcedPost, plugin: XtaPlugin?) {
    val fallback = rememberLinkActions(post.source)
    NetworkPostCard(
        post.post,
        plugin?.postActions ?: fallback,
        NetworkPostExtras.None,
        mark = plugin?.icon,
        markColor = plugin?.brandColor ?: MaterialTheme.colorScheme.primary,
    )
    Hairline()
}

/** [posts] one under another, as a thread's row carries the posts placed above or below it ([FeedMix.around]). */
@Composable
fun PluginPostRows(posts: List<SourcedPost>, plugins: Map<String, XtaPlugin>) {
    posts.forEach { post -> key(post.key) { PluginPostRow(post, plugins[post.source]) } }
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
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        if (problem != null) item(key = "x-problem", contentType = "notice") { ProblemRow(problem, onRetry) }
        items(posts, key = { it.key }, contentType = { "plugin-post" }) { post -> PluginPostRow(post, plugins[post.source]) }
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
 * column whether there are any or not, so the thread keeps its state as they come.
 */
@Composable
fun Placed(buckets: List<List<SourcedPost>>, index: Int, plugins: Map<String, XtaPlugin>, thread: @Composable () -> Unit) {
    val (above, below) = FeedMix.around(buckets, index)
    Column(Modifier.fillMaxWidth()) {
        PluginPostRows(above, plugins)
        thread()
        PluginPostRows(below, plugins)
    }
}
