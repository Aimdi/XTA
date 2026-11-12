package com.aimdi.xtanative.ui.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_caught_up
import com.aimdi.xtanative.ui.generated.resources.posts_collapse
import com.aimdi.xtanative.ui.generated.resources.posts_link_more
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.post.BoostRunRow
import com.aimdi.xtanative.ui.post.BoostRuns
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.post.ThreadRows
import org.jetbrains.compose.resources.stringResource

/**
 * How a feed list lays out [threads] and the plugins' [posts] among them
 * ([FeedLayout]): the posts sharing one article stacked, and the runs of
 * reposts folded while Settings › Posts › "Group runs of reposts" is on.
 */
@Composable
fun rememberFeedLayout(threads: List<XThread>, posts: List<SourcedPost> = emptyList()): FeedLayout {
    val settings = LocalGraph.current.settings
    val collapse by remember(settings) { settings.observe(PostsPrefs.collapseReposts) }.collectAsStateWithLifecycle(false)
    return remember(threads, posts, collapse) { FeedLayout.of(threads, posts, collapse) }
}

/**
 * [threads] as a feed list draws them (paginated_tweet_list.dart): each in
 * its own row, keyed by [keyOf], drawn by [row], except where [layout]
 * stacks the posts sharing an article or folds a run of reposts; and
 * "You're caught up" above the thread at [divider].
 */
fun LazyListScope.feedThreads(
    threads: List<XThread>,
    layout: FeedLayout,
    actions: PostActions,
    divider: Int? = null,
    keyOf: (XThread) -> Any = { it.id },
    row: @Composable (XThread) -> Unit = { ThreadRows(it, actions) },
) {
    val byKey = threads.associateBy(FeedLayout::xKey)
    itemsIndexed(threads, key = { _, thread -> keyOf(thread) }, contentType = { _, _ -> "thread" }) { index, thread ->
        if (index == divider) CaughtUpDivider()
        FeedThreadRow(layout.ofThread(index, thread), thread, actions, draw = { key -> byKey[key]?.let { row(it) } }) { row(thread) }
    }
}

/** "You're caught up", between the new posts and the ones read before (caught_up_divider.dart). */
@Composable
fun CaughtUpDivider() {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = color)
        Text(stringResource(Res.string.groups_caught_up), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = color)
    }
}

/**
 * What a feed list draws in [thread]'s place ([part]): the thread by [plain];
 * nothing for one shown in a stack or a run above; the stack of the posts
 * sharing its article, each drawn by [draw] from its key; or its run of reposts.
 */
@Composable
fun FeedThreadRow(part: FeedPart, thread: XThread, actions: PostActions, draw: @Composable (String) -> Unit, plain: @Composable () -> Unit) {
    when (part) {
        FeedPart.Plain -> plain()
        FeedPart.Hidden -> Unit
        is FeedPart.Stack -> LinkStackRow(part.keys) { key -> if (key == FeedLayout.xKey(thread)) plain() else draw(key) }
        is FeedPart.Run -> BoostRunRow(BoostRuns.Item.Run(part.threads), actions)
    }
}

/**
 * The posts sharing one article (link_stack.dart): the newest, then "More
 * posts sharing this link: N", which lays the others out under it, and
 * "Collapse", which folds them again. [draw] draws a post from its key.
 */
@Composable
fun LinkStackRow(keys: List<String>, draw: @Composable (String) -> Unit) {
    var open by rememberSaveable(keys.first()) { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxWidth().then(if (reduced) Modifier else Modifier.animateContentSize())) {
        key(keys.first()) { draw(keys.first()) }
        TextButton(onClick = { open = !open }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(if (open) PostIcons.UnfoldLess else XIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (open) stringResource(Res.string.posts_collapse) else stringResource(Res.string.posts_link_more, keys.size - 1),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (open) keys.drop(1).forEach { other -> key(other) { draw(other) } }
    }
}

/**
 * A plugin's post where a feed list places it among the threads, as
 * [layout] has it: alone, the first of a stack (whose X posts [drawThread]
 * draws, by key), or nothing, when it is in a stack above.
 */
@Composable
internal fun PlacedPluginPost(post: SourcedPost, layout: FeedLayout, plugins: Map<String, XtaPlugin>, posts: Map<String, SourcedPost>, drawThread: @Composable (String) -> Unit) {
    when (val part = layout.ofPost(post)) {
        FeedPart.Hidden -> Unit
        is FeedPart.Stack -> LinkStackRow(part.keys) { key -> posts[key]?.let { PluginPostRow(it, plugins[it.source]) } ?: drawThread(key) }
        else -> PluginPostRow(post, plugins[post.source])
    }
}

/** Whether [list] is within its last few rows: the feed reads on then. */
@Composable
fun rememberNearEnd(list: LazyListState, rows: Int = 6): State<Boolean> = remember(list, rows) {
    derivedStateOf {
        val info = list.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - rows
    }
}
