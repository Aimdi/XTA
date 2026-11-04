package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.ui.common.Graphemes
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import kotlin.math.abs

/** An account's picture, or its first letter on a colour of its own when it has none (XTA's FallbackAvatar). */
@Composable
internal fun TikTokAvatar(url: String?, name: String, seed: String, size: Dp, onClick: (() -> Unit)? = null) {
    val frame = Modifier.size(size).clip(CircleShape).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    if (url.isNullOrEmpty()) {
        Box(frame.background(Color.hsl((abs(seed.hashCode()) % 360).toFloat(), 0.45f, 0.42f)), contentAlignment = Alignment.Center) {
            Text(Graphemes.first(name.ifBlank { seed }).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = frame.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** The verified mark beside a name. */
@Composable
internal fun VerifiedMark(size: Dp) {
    Icon(XIcons.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(size))
}

/**
 * A video as XTA's TikTok card draws it (tiktok_post_card.dart): who posted
 * it and when, its caption, its cover with the play mark and its length,
 * how often it was played, and the heart kept on this device and Share. The
 * cover plays it: in the app's player where there is one, else on the site.
 * A long press opens the app's post menu, as XTA's card does.
 */
@Composable
internal fun TikTokPostCard(app: TikTokApp, post: TikTokPost, liked: Boolean, openAuthor: Boolean = true) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().pointerInput(post.id) { detectTapGestures(onLongPress = { app.openPostMenu(post) }) }) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val author = post.author
            TikTokAvatar(author.avatarUrl, author.displayName, author.uniqueId, 48.dp, onClick = if (openAuthor) ({ app.openProfile(author.uniqueId) }) else null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(author.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (author.verified) VerifiedMark(16.dp)
                }
                Text("@${author.uniqueId} · ${agoText(post.createdAt)}", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (post.desc.isNotBlank()) Text(post.desc, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        Cover(app, post, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        if (post.playCount > 0) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                Text(countText(post.playCount), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(start = 2.dp))
            }
        }
        CardActions(app, post, liked)
        Hairline()
    }
}

@Composable
private fun CardActions(app: TikTokApp, post: TikTokPost, liked: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val share = rememberShareText()
    val copied = pluginString(Strings.link_copied)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { app.toggleLike(post.id) }) {
            val tint = if (liked) MaterialTheme.colorScheme.primary else muted
            Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(countText(post.diggCount), color = tint, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { if (!share(post.url)) app.notices.tell(copied) }) {
            Icon(Icons.Filled.Share, pluginString(Strings.share_link), tint = muted)
        }
    }
}

/** The cover at the video's shape (kept between 9:16 and 16:9), the play mark on a video and its length. */
@Composable
private fun Cover(app: TikTokApp, post: TikTokPost, modifier: Modifier) {
    val openOutside = rememberOpenOutside()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(post.aspectRatio.coerceIn(9f / 16f, 16f / 9f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { if (inAppPlayer) app.openVideo(post) else openOutside(post.url) },
    ) {
        post.coverUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (!post.isPhoto) {
            Box(Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(12.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            post.durationText.takeIf(String::isNotEmpty)?.let { length ->
                Text(
                    length,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.54f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** A heading of the search's landing: recent searches, trending, suggested accounts. */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
}

/** Words to tap, under [title]: TikTok's suggestions, the trending searches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordChips(title: String, words: List<String>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.take(WORDS_SHOWN).forEach { word -> AssistChip(onClick = { onTap(word) }, label = { Text(word) }) }
        }
    }
}

private const val WORDS_SHOWN = 12
