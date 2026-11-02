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
import androidx.compose.runtime.remember
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
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.core.x.model.playableUrl
import com.aimdi.xtanative.ui.media.LocalInlinePlayer

private val Gap = 2.dp

/**
 * A post's photos and videos as X lays them out: one at its shape, two side by
 * side, three, or four in a square. With [playing], its first video or GIF
 * plays in its tile.
 */
@Composable
fun MediaGrid(media: List<XMedia>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, playing: Boolean = false) {
    if (media.isEmpty()) return
    val playIndex = if (playing) media.indexOfFirst { it.kind != XMedia.Kind.PHOTO } else -1
    val shape = RoundedCornerShape(16.dp)
    val frame = modifier.fillMaxWidth().clip(shape).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    when (media.size) {
        1 -> MediaTile(media[0], playIndex == 0, frame.aspectRatio((media[0].aspectRatio ?: (16f / 9f)).coerceIn(0.8f, 2f))) { onOpen(0) }
        2 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            MediaTile(media[0], playIndex == 0, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
            MediaTile(media[1], playIndex == 1, Modifier.weight(1f).fillMaxHeight()) { onOpen(1) }
        }
        3 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            MediaTile(media[0], playIndex == 0, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[1], playIndex == 1, Modifier.weight(1f).fillMaxWidth()) { onOpen(1) }
                MediaTile(media[2], playIndex == 2, Modifier.weight(1f).fillMaxWidth()) { onOpen(2) }
            }
        }
        else -> Column(frame.aspectRatio(16f / 9f), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[0], playIndex == 0, Modifier.weight(1f).fillMaxHeight()) { onOpen(0) }
                MediaTile(media[1], playIndex == 1, Modifier.weight(1f).fillMaxHeight()) { onOpen(1) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                MediaTile(media[2], playIndex == 2, Modifier.weight(1f).fillMaxHeight()) { onOpen(2) }
                MediaTile(media[3], playIndex == 3, Modifier.weight(1f).fillMaxHeight()) { onOpen(3) }
            }
        }
    }
}

@Composable
private fun MediaTile(media: XMedia, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        AsyncImage(
            model = photoUrl(media.url, PhotoSize.MEDIUM),
            contentDescription = media.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (playing) InlineVideo(media, Modifier.fillMaxSize())
        when (media.kind) {
            XMedia.Kind.PHOTO -> Unit
            XMedia.Kind.VIDEO -> {
                PlayBadge(Modifier.align(Alignment.Center))
                media.durationMs?.let { Label(duration(it), Modifier.align(Alignment.BottomStart)) }
            }
            XMedia.Kind.GIF -> Label("GIF", Modifier.align(Alignment.BottomStart))
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

/** The tile's video playing in place, over its poster until the first frame, where the platform plays one. */
@Composable
private fun InlineVideo(media: XMedia, modifier: Modifier) {
    val player = LocalInlinePlayer.current ?: return
    // Lighter than the full player's pick: a tile is small and plays unasked.
    val url = remember(media) { media.playableUrl(maxBitrate = 1_000_000) } ?: return
    player.Video(url, modifier)
}

private fun duration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
