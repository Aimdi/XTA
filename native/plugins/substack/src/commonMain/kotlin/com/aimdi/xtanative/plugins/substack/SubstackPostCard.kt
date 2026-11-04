package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPosts
import com.aimdi.xtanative.networks.substack.SubstackReading
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText

/** What the cards of one list need to know of what the reader keeps: read with each change, once for the list. */
internal class PostMarks(val readIds: Set<String>, val liked: List<SubstackPost>, val saved: List<SubstackPost>) {
    fun unread(post: SubstackPost): Boolean = !SubstackPosts.isRead(readIds, post)

    fun liked(post: SubstackPost): Boolean = liked.any { SubstackPosts.same(it, post) }

    fun saved(post: SubstackPost): Boolean = saved.any { SubstackPosts.same(it, post) }
}

@Composable
internal fun rememberMarks(app: SubstackApp): PostMarks {
    val read by app.read.state.collectAsState()
    val liked by app.likes.state.collectAsState()
    val saved by app.saves.state.collectAsState()
    return remember(read, liked, saved) { PostMarks(read, liked, saved) }
}

/**
 * A post as XTA's SubstackPostCard draws it: where it is from (the
 * publication's logo and name, opening its page), when, how long it reads,
 * whether it is paid or an episode, its headline and excerpt opening the
 * reader, its cover, and the reader's own heart, bookmark, its discussion and
 * the read mark. A long press offers its link. Nothing is told to Substack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubstackPostCard(app: SubstackApp, post: SubstackPost, marks: PostMarks, logoUrl: String? = null, modifier: Modifier = Modifier) {
    val unread = marks.unread(post)
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().combinedClickable(onClick = { app.openPost(post) }, onLongClick = { menu = true })) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            CardHeader(app, post, unread, logoUrl)
            post.authorName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            val cover = !post.coverImage.isNullOrEmpty()
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = if (cover) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            post.excerpt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (cover) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        post.coverImage?.takeIf { it.isNotEmpty() }?.let { Cover(it, post.isVideo) }
        CardActions(app, post, marks, unread)
        Hairline()
        LinkMenu(post, menu) { menu = false }
    }
}

@Composable
private fun CardHeader(app: SubstackApp, post: SubstackPost, unread: Boolean, logoUrl: String?) {
    val minutes = SubstackReading.readMinutes(post)
    val meta = listOfNotNull(post.publishedAt?.let { agoText(it) }, minutes?.let { pluginPlural(Strings.read_minutes, it, it) }).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (unread) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { app.openPublication(SubstackHomeRules.publicationOf(post, logoUrl)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PublicationLogo(logoUrl, 48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.publicationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    if (post.isPaywalled || post.isPodcast) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (post.isPaywalled) OutlineBadge(pluginString(Strings.paywalled))
            if (post.isPodcast) Icon(SubstackIcons.Podcast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OutlineBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Cover(url: String, video: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        if (video) {
            Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.54f)).padding(12.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CardActions(app: SubstackApp, post: SubstackPost, marks: PostMarks, unread: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val liked = marks.liked(post)
    val saved = marks.saved(post)
    val hearts = (post.reactionCount ?: 0) + if (liked) 1 else 0
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CountButton(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (hearts > 0) "$hearts" else "", if (liked) accent else muted,
            pluginString(if (liked) Strings.unlike else Strings.like)) { app.launch { app.likes.toggle(post) } }
        CountButton(if (saved) SubstackIcons.Bookmark else SubstackIcons.BookmarkBorder, "", if (saved) accent else muted,
            pluginString(if (saved) Strings.unsave else Strings.save)) { app.launch { app.saves.toggle(post) } }
        CountButton(SubstackIcons.Comment, "${post.commentCount ?: 0}", muted, pluginString(Strings.comments)) { app.openComments(post) }
        IconButton(onClick = { app.launch { if (unread) app.read.markRead(listOf(post)) else app.read.markUnread(post) } }) {
            Icon(if (unread) SubstackIcons.MarkRead else SubstackIcons.MarkUnread, pluginString(if (unread) Strings.mark_read else Strings.mark_unread), Modifier.size(20.dp), tint = muted)
        }
    }
}

@Composable
private fun CountButton(icon: ImageVector, label: String, tint: Color, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, description, Modifier.size(20.dp), tint = tint)
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.padding(start = 4.dp))
    }
}

/** A long press's menu: the post's link, shared or opened in the browser. */
@Composable
private fun LinkMenu(post: SubstackPost, open: Boolean, onDismiss: () -> Unit) {
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val url = post.canonicalUrl ?: "${post.publicationBaseUrl}/p/${post.slug}"
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(pluginString(Strings.share_link)) }, onClick = { onDismiss(); share(url) })
        DropdownMenuItem(text = { Text(pluginString(Strings.open_in_browser)) }, onClick = { onDismiss(); outside(url) })
    }
}
