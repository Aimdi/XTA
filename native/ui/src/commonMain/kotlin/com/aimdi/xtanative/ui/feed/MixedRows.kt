package com.aimdi.xtanative.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ReadFailureSheet
import com.aimdi.xtanative.ui.common.ReadRetries
import com.aimdi.xtanative.ui.common.ReadSource
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.common.rememberReadRecovery
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.problem_cached_content
import com.aimdi.xtanative.ui.generated.resources.problem_source_loading
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.LocalPostNetwork
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
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
    val language = currentLanguage()
    // Whose post it is, for the bar under a page its link card opens.
    CompositionLocalProvider(LocalPostNetwork provides post.source) {
        NetworkPostCard(
            post.post,
            plugin?.postActions ?: fallback,
            rememberPostExtras(plugin, post.post),
            mark = plugin?.icon,
            markColor = markColor,
            markLabel = remember(plugin, language) { plugin?.let { runCatching { it.title(language) }.getOrNull() } },
        )
    }
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

/**
 * The plugins worth a word over the timeline (XTA's source chips), in one
 * pill: a chip for each whose read failed, retried on its own with its clock
 * counting down, opening why in a sheet with Retry and the plugin's settings;
 * and for each showing the copy kept from before, opening what that means.
 */
@Composable
fun SourceChips(state: PluginPostsState, plugins: Map<String, XtaPlugin>, onRetry: (String) -> Unit, modifier: Modifier = Modifier) {
    val chips = state.chips
    if (chips.isEmpty()) return
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            chips.forEach { (id, read) -> key(id) { SourceChip(id, read, plugins[id], onRetry) } }
        }
    }
}

@Composable
private fun SourceChip(id: String, read: SourceRead, plugin: XtaPlugin?, onRetry: (String) -> Unit) {
    val language = currentLanguage()
    val name = remember(id, language, plugin) { runCatching { plugin?.title(language) }.getOrNull() ?: id }
    val navigator = LocalPlugins.current.navigator
    val problem = read.problem
    val seconds = rememberReadRecovery(ReadRetries.recoverable(problem), read.loading) { onRetry(id) }
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(name) },
        leadingIcon = {
            val icon = when {
                problem == null -> DiscoverIcons.History
                seconds > 0 -> GroupIcons.ScheduleOutlined
                else -> GroupIcons.ErrorOutline
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    if (!open) return
    if (problem != null) {
        val settings = plugin?.takeIf { runCatching { it.settings != null }.getOrDefault(false) }?.let { { navigator.go(Destination.PluginSettings(id)) } }
        ReadFailureSheet(ReadSource(name, isX = false, openSettings = settings), problem, { onRetry(id) }) { open = false }
    } else {
        CachedCopySheet { open = false }
    }
}

/** What a chip over a kept copy says (XTA's reader_cached_content). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CachedCopySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(Res.string.problem_cached_content), modifier = Modifier.padding(16.dp))
    }
}

/**
 * A line over the timeline's top edge while some plugin is still reading
 * (XTA's LoadingHairline): it takes no room, so nothing moves as it comes
 * and goes, and says that the posts shown are ready to read.
 */
@Composable
fun SourceLoadingHairline(loading: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.problem_source_loading)
    AnimatedVisibility(loading, modifier, enter = fadeIn(), exit = fadeOut()) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp).semantics { contentDescription = label },
            trackColor = Color.Transparent,
        )
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
