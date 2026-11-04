package com.aimdi.xtanative.ui.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_fold_hide_again
import com.aimdi.xtanative.ui.generated.resources.groups_fold_matched
import com.aimdi.xtanative.ui.generated.resources.groups_media_empty
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.search.MediaGridShape
import com.aimdi.xtanative.ui.search.MediaTile
import com.aimdi.xtanative.ui.search.mediaRows
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.stringResource

/** A thread folded behind why (folded_chain.dart): a tap shows it, and hides it again. */
@Composable
internal fun FoldedThread(thread: XThread, reason: String, actions: PostActions, playing: Boolean) {
    var open by rememberSaveable(thread.id) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (open) stringResource(Res.string.groups_fold_hide_again) else stringResource(Res.string.groups_fold_matched, reason),
                style = if (open) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            if (!open) Icon(GroupIcons.ExpandMore, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        if (open) ThreadRows(thread, actions, playing = playing) else Hairline()
    }
}

/** A thread as a feed shows it: folded behind [fold] when its rules fold it, else whole. */
@Composable
internal fun FeedThread(thread: XThread, fold: String?, actions: PostActions, playing: Boolean) {
    if (fold != null) FoldedThread(thread, fold, actions, playing) else ThreadRows(thread, actions, playing = playing)
}

/** The grid shape the reader's Media settings ask for. */
@Composable
internal fun rememberMediaShape(): MediaGridShape {
    val settings = LocalGraph.current.settings
    val flow = remember(settings) {
        combine(settings.observe(MediaPrefs.gridLayout), settings.observe(MediaPrefs.gridColumns)) { layout, columns -> MediaGridShape.of(layout, columns) }
    }
    val shape by flow.collectAsStateWithLifecycle(MediaGridShape(3, ownShape = false))
    return shape
}

/** What a feed's media grid has to say at its foot: reading more, why reading failed, or nothing. */
data class MediaFoot(val loading: Boolean, val problem: Problem?, val onRetry: () -> Unit)

/**
 * A feed's media alone (XTA's group media page): the photos and videos of the
 * posts it has read, in its order, read on as the grid nears its end; a page
 * that brings no media reads the next. A tap opens the viewer.
 */
@Composable
internal fun FeedMediaGrid(
    threads: List<XThread>,
    canLoadMore: Boolean,
    foot: MediaFoot,
    actions: PostActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    onMore: () -> Unit,
) {
    val tiles = remember(threads) { MediaTile.of(threads) }
    val shape = rememberMediaShape()
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, threads.size, canLoadMore) { if (nearEnd && canLoadMore) onMore() }
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        mediaRows(tiles, shape) { tile -> actions.openMedia(tile.postId, tile.index) }
        item(key = "media-foot", contentType = "footer") {
            val problem = foot.problem
            when {
                foot.loading -> LoadingRow()
                problem != null -> ProblemRow(problem, foot.onRetry)
                tiles.isEmpty() && !canLoadMore -> MediaEmpty()
                else -> Box(Modifier.padding(bottom = 48.dp))
            }
        }
    }
}

@Composable
private fun MediaEmpty() {
    Text(
        stringResource(Res.string.groups_media_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}
