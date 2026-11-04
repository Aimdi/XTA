package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.media.TileGif
import com.aimdi.xtanative.ui.media.TileVideo

private val Gap = 2.dp

/**
 * A post's photos and videos as X lays them out: one at its shape, two side by
 * side, three, or four in a square. A video plays in its tile with its
 * controls, and a GIF loops there (`ui/media`'s TileVideo and TileGif): with
 * [playing], the feed plays the first of them by itself. [post] names a saved
 * video's file.
 */
@Composable
fun MediaGrid(media: List<XMedia>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, playing: Boolean = false, post: XPost? = null) {
    if (media.isEmpty()) return
    val playIndex = if (playing) media.indexOfFirst { it.kind != XMedia.Kind.PHOTO } else -1
    val shape = RoundedCornerShape(16.dp)
    val frame = modifier.fillMaxWidth().clip(shape).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    when (media.size) {
        1 -> MediaTile(media[0], playIndex == 0, post, frame.aspectRatio((media[0].aspectRatio ?: (16f / 9f)).coerceIn(0.8f, 2f))) { onOpen(0) }
        2 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            MediaTile(media[0], playIndex == 0, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
            MediaTile(media[1], playIndex == 1, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(1) }
        }
        3 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            MediaTile(media[0], playIndex == 0, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[1], playIndex == 1, post, Modifier.weight(1f).fillMaxWidth()) { onOpen(1) }
                MediaTile(media[2], playIndex == 2, post, Modifier.weight(1f).fillMaxWidth()) { onOpen(2) }
            }
        }
        else -> Column(frame.aspectRatio(16f / 9f), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[0], playIndex == 0, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
                MediaTile(media[1], playIndex == 1, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(1) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[2], playIndex == 2, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(2) }
                MediaTile(media[3], playIndex == 3, post, Modifier.weight(1f).fillMaxHeight()) { onOpen(3) }
            }
        }
    }
}

/** A tile, whose tap opens the viewer; a video's tile plays it in place instead, where the platform plays videos. */
@Composable
private fun MediaTile(media: XMedia, playing: Boolean, post: XPost?, modifier: Modifier, onClick: () -> Unit) {
    val poster = photoUrl(media.url, PhotoSize.MEDIUM)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        AsyncImage(
            model = poster,
            contentDescription = media.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when (media.kind) {
            XMedia.Kind.PHOTO -> Unit
            XMedia.Kind.VIDEO -> {
                PlayBadge(Modifier.align(Alignment.Center))
                media.durationMs?.let { Label(duration(it), Modifier.align(Alignment.BottomStart)) }
                TileVideo(media, poster, post, playing, Modifier.matchParentSize())
            }
            XMedia.Kind.GIF -> {
                TileGif(media, poster, playing, Modifier.matchParentSize())
                Label("GIF", Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** The round play sign over a video's poster. */
@Composable
internal fun PlayBadge(modifier: Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun duration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
