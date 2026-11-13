package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.threads.ThreadsCommunityBadge
import com.aimdi.xtanative.networks.threads.ThreadsFediverse
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPodcast
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/** The words a community badge is shown in. */
internal fun badgeLabel(badge: ThreadsCommunityBadge): PluginText = when (badge) {
    ThreadsCommunityBadge.MEMBER -> Strings.badge_member
    ThreadsCommunityBadge.FOUNDER -> Strings.badge_founder
    ThreadsCommunityBadge.MODERATOR -> Strings.badge_moderator
    ThreadsCommunityBadge.CHAMPION -> Strings.badge_champion
    ThreadsCommunityBadge.TOP_CONTRIBUTOR -> Strings.badge_top_contributor
}

/** The topics and communities a profile lists under its bio, as threads.com shows them: each opens its page here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileTags(app: ThreadsApp, tags: List<ThreadsTopic>) {
    if (tags.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { topic ->
            AssistChip(
                onClick = { app.openTopic(topic) },
                label = { Text(topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { TopicMark(topic, 20.dp) },
            )
        }
    }
}

/** "Podcast · Its name", opening where it is heard; without an address, only named. */
@Composable
internal fun ProfilePodcast(podcast: ThreadsPodcast?) {
    val shown = podcast ?: return
    val label = shown.label ?: return
    val outside = rememberOpenOutside()
    val said = pluginString(Strings.podcast) + " · " + label
    val open = shown.url?.let { url -> Modifier.clickable(role = Role.Button) { outside(url) } } ?: Modifier
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).then(open).semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PostIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Text(
            said,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = if (shown.url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** "Shares to the fediverse", with the address the fediverse follows the profile at; a tap copies the address. */
@Composable
internal fun ProfileFediverse(app: ThreadsApp, fediverse: ThreadsFediverse?) {
    val shown = fediverse ?: return
    val copy = rememberCopyText()
    val copied = pluginString(Strings.fediverse_copied)
    val sharing = pluginString(Strings.fediverse_sharing)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClickLabel = copied) { copy(shown.address); app.host.tell(copied) }
            .semantics(mergeDescendants = true) { contentDescription = "$sharing, ${shown.address}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ThreadsIcons.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Text(
            "$sharing · ${shown.address}",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A profile's Media tab as a grid: one square per post, its first picture
 * or video's poster, marked when the post holds more or a video. Nothing
 * more is read for it: the posts the tab already has are its tiles.
 */
internal object ThreadsMediaGrid {
    const val COLUMNS = 3

    /** One square: [post]'s first [media]; [more] when the post holds several. */
    data class Tile(val post: ThreadsPost, val media: ThreadsMedia, val more: Boolean) {
        val key: String get() = post.id
    }

    /** A square for each post with media, in the tab's order. */
    fun tiles(posts: List<ThreadsPost>): List<Tile> =
        posts.mapNotNull { post -> post.media.firstOrNull()?.let { Tile(post, it, post.media.size > 1) } }

    /** [tiles] in rows of [columns], the last one short where they run out. */
    fun rows(tiles: List<Tile>, columns: Int = COLUMNS): List<List<Tile>> = tiles.chunked(columns.coerceAtLeast(1))
}

/** One row of the grid: its squares side by side, the row's end left empty where they run out. */
@Composable
internal fun MediaGridRow(app: ThreadsApp, row: List<ThreadsMediaGrid.Tile>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        val pixels = with(LocalDensity.current) { (maxWidth / ThreadsMediaGrid.COLUMNS).roundToPx() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.forEach { tile -> MediaGridTile(app, tile, pixels, Modifier.weight(1f)) }
            repeat(ThreadsMediaGrid.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * A square of the grid: a tap opens the post's pictures and videos in the
 * viewer (with Open post), a long press opens the post. Media Meta covered
 * waits behind its mark where the reader hides sensitive media, and opens
 * the post instead, where it is shown on asking.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridTile(app: ThreadsApp, tile: ThreadsMediaGrid.Tile, pixels: Int, modifier: Modifier) {
    val settings by app.prefs.collectAsState()
    val covered = ThreadsMediaCover.covers(tile.post, settings)
    val media = tile.media
    val name = media.alt ?: pluginString(if (media.isVideo) Strings.media_video else Strings.media_picture)
    val openPost = pluginString(Strings.open_post)
    val url = remember(media, pixels) { media.tileUrl(pixels, pixels) }
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClickLabel = if (covered) openPost else name,
                onClick = { if (covered) app.openPost(tile.post) else app.openMedia(tile.post, 0) },
                onLongClickLabel = openPost,
                onLongClick = { app.openPost(tile.post) },
            )
            .semantics { contentDescription = name; role = Role.Image },
    ) {
        if (covered) {
            Icon(PostIcons.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        val mark = when {
            tile.more -> PostIcons.Layers
            media.isVideo -> PostIcons.Videocam
            else -> null
        }
        mark?.let {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
