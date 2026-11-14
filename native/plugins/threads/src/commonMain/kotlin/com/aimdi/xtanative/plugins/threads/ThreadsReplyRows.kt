package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsReplyRow
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/** How far each level of a reply steps in. */
private val STEP: Dp = 20.dp

/**
 * Each reply thread: a reply that is the only answer joined by a rail, one
 * of several answers set in under the post it answers with a guide line per
 * level, a long thread cut to its first few until "Show N more replies" and
 * cut again by "Show fewer replies", and one Meta holds more of ending in
 * "Continue this thread": [onContinue], which opens its last post's own
 * conversation here unless the screen reads its replies in place.
 */
internal fun LazyListScope.replyRows(
    app: ThreadsApp,
    rows: List<ThreadsReplyRow>,
    onExpand: (List<ThreadsPost>) -> Unit,
    onCollapse: (List<ThreadsPost>) -> Unit,
    byAuthor: (ThreadsPost) -> Boolean = { false },
    onContinue: (ThreadsReplyRow.OnWeb) -> Unit = { row -> row.continuesAt?.let(app::openPost) },
) {
    itemsIndexed(rows, key = { index, row -> "reply:$index:" + keyOf(row) }, contentType = { _, row -> if (row is ThreadsReplyRow.Post) "post" else "marker" }) { _, row ->
        when (row) {
            is ThreadsReplyRow.Post -> Box(Modifier.fillMaxWidth().levelGuides(row.depth).padding(start = STEP * row.depth)) {
                ThreadsPostCard(app, row.post, connectTop = row.connectTop, connectBottom = row.connectBottom, byAuthor = byAuthor(row.post))
            }
            is ThreadsReplyRow.More -> MarkerRow(PostIcons.SubdirectoryArrow, pluginPlural(Strings.more_replies, row.hidden, row.hidden)) { onExpand(row.chain) }
            is ThreadsReplyRow.Less -> MarkerRow(PostIcons.SubdirectoryArrow, pluginString(Strings.fewer_replies)) { onCollapse(row.chain) }
            is ThreadsReplyRow.OnWeb -> if (row.continuesAt != null) MarkerRow(PostIcons.Forum, pluginString(Strings.continue_thread)) { onContinue(row) }
        }
    }
}

/** Under the replies when Meta holds more than the page carried: a guest reads the rest on threads.com. */
internal fun LazyListScope.moreRepliesOnWeb(url: String?) {
    if (url == null) return
    item(key = "replies-on-web") { OnWebRow(pluginString(Strings.more_replies_web), url) }
}

/** The posts Threads suggests beside this one, under their own heading, after the conversation. */
internal fun LazyListScope.relatedRows(app: ThreadsApp, related: List<ThreadsPost>) {
    if (related.isEmpty()) return
    item(key = "related-header") {
        Text(
            pluginString(Strings.related_threads),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
    items(related, key = { "related:" + it.id }, contentType = { "post" }) { post -> ThreadsPostCard(app, post) }
}

private fun keyOf(row: ThreadsReplyRow): String = when (row) {
    is ThreadsReplyRow.Post -> row.post.id
    is ThreadsReplyRow.More -> "more:" + row.chain.first().id
    is ThreadsReplyRow.Less -> "less:" + row.chain.first().id
    is ThreadsReplyRow.OnWeb -> "web:" + row.chain.first().id
}

/** A thin line for each level a reply is set in, down the room the step leaves. */
@Composable
private fun Modifier.levelGuides(depth: Int): Modifier {
    if (depth <= 0) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind { guides(depth, color) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.guides(depth: Int, color: Color) {
    val width = 1.dp.toPx()
    repeat(depth) { level ->
        // The replies step in from the row's start: from the right where the language is written right to left.
        val x = ThreadsCardRules.mirrored((STEP * level + STEP / 2 + 16.dp).toPx(), size.width, layoutDirection == LayoutDirection.Rtl)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = width)
    }
}

@Composable
private fun MarkerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 76.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun OnWebRow(label: String, url: String) {
    val outside = rememberOpenOutside()
    MarkerRow(PostIcons.OpenInNew, label) { outside(url) }
}
