package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruComment
import com.aimdi.xtanative.networks.booru.BooruPool
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruText
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

private val StripHeight = 132.dp

/**
 * A row of posts for [query] under [title], leaving out the post on screen
 * (booru_related.dart): "More from" its artist, its parent and child posts.
 * Nothing until there is something to show; See all opens the search.
 */
@Composable
internal fun RelatedStrip(app: BooruApp, viewer: ViewerState, title: String, query: String, post: BooruPost) {
    val site = app.client.siteOf(post)
    val posts = produceState(emptyList<BooruPost>(), query, post.key) {
        value = attempt {
            viewer.lookups.remember("related:${site.host}:$query:1") { app.client.search(query, "1", RELATED_PAGE, site) }
        }?.posts.orEmpty()
    }.value.filter { it.id != post.id }
    if (posts.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { app.openSearch(query) }) { Text(pluginString(Strings.see_all)) }
        }
        LazyRow(Modifier.height(StripHeight), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(posts, key = { _, it -> it.key }) { index, related ->
                BooruImage(
                    related.thumbnailUrl,
                    Modifier.height(StripHeight).width(StripHeight * related.aspectRatio.toFloat().coerceIn(0.6f, 1.5f))
                        .clip(RoundedCornerShape(8.dp)).clickable { app.openPost(posts, index) },
                    description = tileLabel(related),
                )
            }
        }
    }
}

private const val RELATED_PAGE = 20

/** The pools [post] is in, each opening to be read in its own order (booru_pools.dart); nothing when it is in none. */
@Composable
internal fun PoolsSection(app: BooruApp, viewer: ViewerState, post: BooruPost) {
    val pools = produceState(emptyList<BooruPool>(), post.key) {
        value = attempt { viewer.lookups.remember("pools:${post.key}") { app.client.pools(post) } }.orEmpty()
    }.value
    if (pools.isEmpty()) return
    val site = app.siteOf(post)
    Column(Modifier.fillMaxWidth()) {
        Text(pluginString(Strings.pools), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        pools.forEach { pool ->
            Row(Modifier.fillMaxWidth().clickable { app.host.open(BooruRoutes.pool(pool.id, BooruFormats.tagName(pool.name), site)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(BooruIcons.Pools, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(BooruFormats.tagName(pool.name), style = MaterialTheme.typography.bodyLarge)
                    pool.postCount?.let { Text(pluginString(Strings.pool_size, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** The post's comments, read only, fetched when the section is opened (booru_comments.dart). */
@Composable
internal fun CommentsSection(app: BooruApp, post: BooruPost) {
    var open by rememberSaveable(post.key) { mutableStateOf(false) }
    var attempt by rememberSaveable(post.key) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PostIcons.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(24.dp))
            Text(pluginString(Strings.comments), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.ExpandMore, contentDescription = null)
        }
        if (open) Comments(app, post, attempt) { attempt++ }
    }
}

@Composable
private fun Comments(app: BooruApp, post: BooruPost, attempt: Int, onRetry: () -> Unit) {
    val comments = produceState<Result<List<BooruComment>>?>(null, post.key, attempt) {
        value = try {
            Result.success(app.client.comments(post))
        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }.value
    val loaded = comments?.getOrNull()
    when {
        comments == null -> LoadingRow()
        comments.isFailure -> RetryRow(errorText(comments.exceptionOrNull()), onRetry)
        loaded.isNullOrEmpty() -> Text(pluginString(Strings.no_comments), modifier = Modifier.padding(16.dp))
        else -> loaded.forEach { CommentRow(it) }
    }
}

@Composable
private fun CommentRow(comment: BooruComment) {
    val locale = readerLocale()
    val meta = listOfNotNull(comment.author, comment.createdAt?.let { BooruFormats.date(it, locale) }).joinToString(" · ")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(Modifier.padding(top = 2.dp)) { Text(BooruText.plain(comment.body), style = MaterialTheme.typography.bodyMedium) }
    }
}
