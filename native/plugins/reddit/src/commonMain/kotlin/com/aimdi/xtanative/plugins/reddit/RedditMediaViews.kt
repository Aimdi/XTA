package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.reddit.RedditMedia
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.ui.common.Graphemes
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MediaTiles
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val MediaShape = RoundedCornerShape(16.dp)

/** How tall a picture may get before it is cropped, and how short: a phone-height meme must not push the feed off the screen. */
private val MaxHeight = 420.dp
private val MinHeight = 120.dp

/**
 * The picture, gallery, video or link that goes with a post (XTA's
 * `RedditPostMedia`), the width of the card, behind its cover when the post
 * is over 18 or a spoiler. [thread] draws a link post's card as the thread
 * does.
 */
@Composable
internal fun PostMediaView(app: RedditApp, post: RedditPost, choices: RedditChoices, modifier: Modifier = Modifier, thread: Boolean = false) {
    val media = RedditMediaRules.of(post) ?: return
    val gate = choices.gate(post)
    Box(modifier) {
        when (media) {
            is PostMedia.Video -> Covered(post, gate, ratioOf(post)) { VideoTile(app, post, media.stream) }
            is PostMedia.Gallery -> Covered(post, gate, 1f) { Gallery(media.images, onSave = { app.savePicture(media.images[it], post) }) { app.openPictures(media.images, it) } }
            is PostMedia.Image -> Covered(post, gate) { Picture(media.url, onSave = { app.savePicture(media.url, post) }) { app.openPictures(listOf(media.url), 0) } }
            is PostMedia.GalleryLink -> GalleryLink(app, post, gate, media.url, thread)
            is PostMedia.Link -> LinkCard(app, post, gate, media.url, thread)
        }
    }
}

private fun ratioOf(post: RedditPost): Float = RedditMediaRules.aspectRatio(post.videoAspectRatio).toFloat()

/**
 * A video in its card, as XTA's TweetVideo plays Reddit's (reddit_post_media.dart):
 * in place under the app's controls, by itself where Autoplay videos says,
 * else on a tap; full screen from its controls. Where nothing plays in
 * place the tile opens the app's viewer.
 */
@Composable
private fun VideoTile(app: RedditApp, post: RedditPost, stream: String) {
    val item = MediaItem(
        imageUrl = post.cardPreviewImage.orEmpty(),
        kind = MediaItem.Kind.VIDEO,
        videoUrl = stream,
        aspectRatio = ratioOf(post),
        webUrl = RedditThreadRules.postUrl(post),
    )
    MediaTiles(listOf(item), onOpen = { app.openVideo(post, stream) }, playing = true)
}

/** One picture at whatever height it turns out to be, within the frame's bounds, cropped beyond them; a long press saves it, as XTA's does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Picture(url: String, onSave: (() -> Unit)? = null, onOpen: () -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = pluginString(Strings.media),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinHeight, max = MaxHeight).framed().combinedClickable(onClick = onOpen, onLongClick = onSave),
    )
}

/** A gallery one picture at a time in a square, with where the reader is in it: the album in the height of one picture; a long press saves the picture shown, as XTA's RedditGallery. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Gallery(images: List<String>, onSave: (Int) -> Unit, onOpen: (Int) -> Unit) {
    val pager = rememberPagerState { images.size }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).framed()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = images[page],
                contentDescription = pluginString(Strings.photos),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().combinedClickable(onClick = { onOpen(page) }, onLongClick = { onSave(page) }),
            )
        }
        Text(
            "${pager.currentPage + 1}/${images.size}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.54f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A gallery an old.reddit listing carried as a link: its pictures from the post's own JSON, the link until they come, or for good when Reddit will not say. */
@Composable
private fun GalleryLink(app: RedditApp, post: RedditPost, gate: MediaGate?, url: String, thread: Boolean) {
    val images by produceState(emptyList<String>(), post.permalink) { value = RedditMedia.collapseImageUrls(attempt { app.galleryImages(post.permalink) }.getOrDefault(emptyList())) }
    when {
        images.size > 1 -> Covered(post, gate, 1f) { Gallery(images, onSave = { app.savePicture(images[it], post) }) { app.openPictures(images, it) } }
        images.size == 1 -> Covered(post, gate) { Picture(images[0], onSave = { app.savePicture(images[0], post) }) { app.openPictures(images, 0) } }
        else -> LinkCard(app, post, gate, url, thread)
    }
}

/** Where a link post leads: the feed's card, or the thread's. */
@Composable
private fun LinkCard(app: RedditApp, post: RedditPost, gate: MediaGate?, url: String, thread: Boolean) {
    val open = { app.host.openLink(url) }
    if (thread) ThreadLinkCard(post, gate, url, open) else FeedLinkCard(post, gate, url, open)
}

/**
 * A link post in a list: Reddit's preview full width, a video's with its
 * play badge, over the site and the way out; with no preview, the
 * thumbnail, or a square saying what it is, beside the site.
 */
@Composable
private fun FeedLinkCard(post: RedditPost, gate: MediaGate?, url: String, onOpen: () -> Unit) {
    val preview = post.cardPreviewImage
    Column(Modifier.fillMaxWidth().framed().clickable(onClick = onOpen)) {
        if (preview != null) {
            Covered(post, gate, RedditMediaRules.MAX_RATIO.toFloat(), shape = RoundedCornerShape(0.dp)) {
                Banner(preview, post.isVideo, Modifier.fillMaxWidth().aspectRatio(RedditMediaRules.MAX_RATIO.toFloat()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preview == null) Thumb(post, gate)
            Text(
                RedditMediaRules.domain(post, url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            OutIcon(Modifier.padding(end = 12.dp))
        }
    }
}

/** The thumbnail Reddit gave, or a square saying what the link is; a video's with its badge. */
@Composable
private fun Thumb(post: RedditPost, gate: MediaGate?) {
    val thumbnail = post.thumbnailUrl
    Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (thumbnail != null && gate == null) {
            AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (gate != null) RedditGlyphs.VisibilityOff else RedditGlyphs.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (post.isVideo) PlayBadge(32.dp)
    }
}

/**
 * A link post's card in its thread: Reddit's preview 168 dp tall over the
 * site and the way out, or, with no preview worth stretching, one row with
 * the site's initial, the site and "Open in browser".
 */
@Composable
private fun ThreadLinkCard(post: RedditPost, gate: MediaGate?, url: String, onOpen: () -> Unit) {
    val preview = post.previewImage
    val domain = RedditMediaRules.domain(post, url)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().framed(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onOpen)) {
        if (preview != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ratio = (maxWidth / 168.dp).coerceAtLeast(0.1f)
                Covered(post, gate, ratio, shape = RoundedCornerShape(0.dp)) { Banner(preview, post.isVideo, Modifier.fillMaxWidth().height(168.dp)) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(domain, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                OutIcon()
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Initial(domain)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluginString(Strings.open_in_browser), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                OutIcon()
            }
        }
    }
}

@Composable
private fun Initial(domain: String) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text(if (domain.isEmpty()) "" else Graphemes.first(domain).uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** Reddit's preview of what a link leads to, a video's with its play badge. */
@Composable
private fun Banner(url: String, video: Boolean, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = pluginString(Strings.media), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (video) PlayBadge(48.dp)
    }
}

@Composable
private fun PlayBadge(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.54f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
private fun OutIcon(modifier: Modifier = Modifier) {
    Icon(RedditGlyphs.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.size(18.dp))
}

/**
 * Holds media back until the reader asks for it (XTA's `RedditSensitiveGate`):
 * not a blur over the picture, which is not even fetched until the cover is
 * tapped. The reveal belongs to the post, so a recycled row does not carry it.
 */
@Composable
private fun Covered(post: RedditPost, gate: MediaGate?, ratio: Float = 4f / 3f, shape: RoundedCornerShape = MediaShape, content: @Composable () -> Unit) {
    var revealed by rememberSaveable(post.id, gate) { mutableStateOf(gate == null) }
    if (revealed || gate == null) {
        content()
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { revealed = true }) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = maxWidth / ratio).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            val spoiler = gate == MediaGate.SPOILER
            Icon(if (spoiler) RedditGlyphs.WarningAmber else RedditGlyphs.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pluginString(if (spoiler) Strings.spoiler else Strings.possibly_sensitive), style = MaterialTheme.typography.titleSmall)
            Text(
                pluginString(if (spoiler) Strings.tap_to_show_spoiler else Strings.tap_to_show_media),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pictures and GIFs a comment carries, smaller than a post's: a reply is a reply. A long press saves one, as XTA's. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentImages(app: RedditApp, urls: List<String>) {
    urls.forEachIndexed { index, url ->
        var broken by rememberSaveable(url) { mutableStateOf(false) }
        if (broken) {
            BrokenImage(url)
        } else {
            AsyncImage(
                model = url,
                contentDescription = pluginString(Strings.media),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { broken = true },
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = { app.openPictures(urls, index) }, onLongClick = { app.savePicture(url) }),
            )
        }
    }
}

/** A picture Reddit would not serve: its link survives, to open where it lives. */
@Composable
private fun BrokenImage(url: String) {
    val outside = rememberOpenOutside()
    Row(Modifier.clickable { outside(url) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(RedditGlyphs.BrokenImage, contentDescription = pluginString(Strings.open_in_browser), tint = muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(url.toHttpUrlOrNull()?.host ?: url, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Rounded as a post's media, the theme's hairline drawn over it, and tinted under it so a picture still on its way is a frame, not a hole. */
@Composable
private fun Modifier.framed(shape: RoundedCornerShape = MediaShape): Modifier =
    clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
