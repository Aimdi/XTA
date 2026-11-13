package com.aimdi.xtanative.plugins.instagram

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.longPressOver
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * A photo, carousel, video or reel as a feed card (instagram_post_card.dart):
 * the author (opening the profile, Follow beside an account not followed
 * here when [showFollow]), the caption, the pictures, then the like kept on
 * this device with Instagram's count, the post on Instagram and its link to
 * share. A long press anywhere on it, its picture and buttons included, opens
 * the app's post sheet, which keeps it in Saved as XTA keeps it, a link.
 */
@Composable
internal fun InstagramPostCard(app: InstagramApp, post: InstagramPost, openAuthor: Boolean = true, showFollow: Boolean = false) {
    val more = pluginString(Strings.more_options)
    Column(
        Modifier
            .fillMaxWidth()
            .longPressOver { app.openMenu(post) }
            .semantics { onLongClick(more) { app.openMenu(post); true } },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.author.avatarUrl, 40.dp, onClick = if (openAuthor) { { app.openProfile(post.author.username) } } else null)
            Spacer(Modifier.width(16.dp))
            AuthorText(post, Modifier.weight(1f))
            if (showFollow) FollowButton(app, post.author)
        }
        if (post.caption.isNotBlank()) {
            Text(post.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
        if (post.displayUrls.isNotEmpty()) PostMedia(app, post, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        Footer(app, post)
        Hairline()
    }
}

@Composable
private fun AuthorText(post: InstagramPost, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(post.author.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (post.author.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        Text(
            "@${post.author.username} · ${agoText(post.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Follow on this device, gone once the author is followed. */
@Composable
internal fun FollowButton(app: InstagramApp, author: InstagramAuthor) {
    val rows by app.follows.state.collectAsState()
    if (rows == null || InstagramFollows.follows(rows, author.username)) return
    TextButton(onClick = { app.follow(author) }) { Text(pluginString(Strings.follow)) }
}

/**
 * The pictures, square: one, or a carousel swiped one at a time, a dot for
 * each under it as XTA's PluginPostMedia pages them. A picture opens the
 * app's viewer; a video, which Instagram's servers serve only to its own
 * players, shows its cover (where it is among several in the corner) and
 * opens on Instagram.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostMedia(app: InstagramApp, post: InstagramPost, modifier: Modifier) {
    val urls = post.displayUrls
    val outside = rememberOpenOutside()
    val pager = rememberPagerState { urls.size }
    val open = { index: Int -> if (post.isVideo) outside(post.url) else app.openPhotos(post, index) }
    val mark = InstagramMedia.pageMark(post)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
                AsyncImage(urls[page], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable { open(page) })
            }
            if (post.isVideo) PlayCircle(Modifier.align(Alignment.Center))
            if (mark == PageMark.COUNTER) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.54f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (mark == PageMark.DOTS) PageDots(pager.currentPage, urls.size)
    }
}

/** A dot for each picture of a carousel, the one shown darker (plugin_post_media.dart's pager). */
@Composable
private fun PageDots(current: Int, count: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            Box(Modifier.padding(horizontal = 2.dp).size(5.dp).clip(CircleShape).background(color.copy(alpha = if (index == current) 0.85f else 0.28f)))
        }
    }
}

@Composable
private fun PlayCircle(modifier: Modifier) {
    Box(modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(40.dp))
    }
}

/** The like kept here with Instagram's count, the post on Instagram, and its link to share. */
@Composable
private fun Footer(app: InstagramApp, post: InstagramPost) {
    val settings by app.prefs.collectAsState()
    val liked = post.id in InstagramLikes.ids(settings)
    val outside = rememberOpenOutside()
    val share = rememberShareText()
    val copied = Strings.link_copied.get(currentLanguage())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { app.toggleLike(post.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
            val tint = if (liked) MaterialTheme.colorScheme.primary else muted
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = pluginString(if (liked) Strings.unlike_on_device else Strings.like_on_device),
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(countText(post.likeCount), style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { outside(post.url) }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_on_site), tint = muted, modifier = Modifier.size(20.dp)) }
        IconButton(onClick = { if (!share(post.url)) app.host.tell(copied) }) {
            Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link), tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}
