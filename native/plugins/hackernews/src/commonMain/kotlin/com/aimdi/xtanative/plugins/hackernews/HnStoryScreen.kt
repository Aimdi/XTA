package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnComment
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.networks.hackernews.HnThreadReader
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * A story and its comments (hn_story_screen.dart): the story's title, site,
 * points, author and age, its text, then each comment in a bubble tinted by
 * its depth; a tap on one folds its replies away, or back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HnStoryScreen(app: HnApp, id: Long) {
    val reader = rememberKept("hn-story:$id") { scope -> HnThreadReader(app.client, id, app.seen(id), scope).also { it.refresh() } }
    val state by reader.state.collectAsState()
    var collapsed by remember { mutableStateOf(emptySet<Long>()) }
    val thread = state.value
    val url = thread?.story?.url
    PluginScaffold(
        title = pluginString(Strings.comments),
        onBack = app.host::back,
        actions = { if (url != null) IconButton(onClick = { app.host.openLink(url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_article)) } },
    ) { padding ->
        val rows = remember(thread, collapsed) { HnComment.visible(thread?.comments.orEmpty(), collapsed) }
        PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = reader::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp), modifier = Modifier.fillMaxSize()) {
                thread?.let { item(key = "lead") { Lead(app, it.story) } }
                val error = state.error
                when {
                    state.loading && rows.isEmpty() -> item(key = "loading") { FeedSkeleton() }
                    error != null -> item(key = "problem") { NetworkProblemView(problemOf(error), app.network, reader::refresh) }
                }
                items(rows, key = { (comment, _) -> comment.id }) { (comment, depth) ->
                    val folded = comment.id in collapsed
                    CommentBubble(comment, depth, folded) { collapsed = if (folded) collapsed - comment.id else collapsed + comment.id }
                }
            }
        }
    }
}

/** The story over its comments: title, site, points, author (to their page) and age, then its text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lead(app: HnApp, story: HnStory) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Text(story.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            story.host?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluginPlural(Strings.points, story.score, story.score))
                story.author?.let { author -> Text(pluginString(Strings.by, author), color = HnBrand, modifier = Modifier.clickable { app.openUser(author) }) }
                agoText(story.createdAt).takeIf { it.isNotEmpty() }?.let { Text(it) }
            }
        }
        Hairline()
        if (story.text.isNotEmpty()) Text(story.text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
    }
}

/**
 * One comment in its own rounded bubble, as XTA's CommentBubble draws it: a
 * wash of its depth's hue, indented a step a level up to eight, the
 * author set apart from when it was written and, folded, how many replies
 * it holds; deleted ones keep only the outline.
 */
@Composable
private fun CommentBubble(comment: HnComment, depth: Int, folded: Boolean, onToggle: () -> Unit) {
    val ground = MaterialTheme.colorScheme.background
    val dark = ground.luminance() < 0.5f
    val hue = Hues[depth % Hues.size]
    val fill = if (comment.deleted) ground else hue.copy(alpha = if (dark) 0.17f else 0.09f).compositeOver(ground)
    val border = hue.copy(alpha = if (dark) 0.42f else 0.28f).compositeOver(ground)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(start = 12.dp + 12.dp * depth.coerceIn(0, 8), end = 12.dp, top = 3.dp, bottom = 3.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(header(comment, folded), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!folded && !comment.deleted && comment.text.isNotEmpty()) {
            Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** "author · when · N comments": the name set apart, so a thread can be followed by who is speaking. */
@Composable
private fun header(comment: HnComment, folded: Boolean) = buildAnnotatedString {
    val author = comment.author.takeUnless { comment.deleted }
    if (author == null) append(pluginString(Strings.deleted)) else withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(author) }
    agoText(comment.createdAt).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
    if (folded && comment.children.isNotEmpty()) append(" · " + pluginPlural(Strings.comment_count, comment.children.size, comment.children.size))
}

/** One hue per reply level, cycling: X's own accent family, as XTA's comment bubbles take it. */
private val Hues = listOf(Color(0xFF1D9BF0), Color(0xFF7856FF), Color(0xFF00BA7C), Color(0xFFFFAD1F), Color(0xFFF91880), Color(0xFFFF7A00))
