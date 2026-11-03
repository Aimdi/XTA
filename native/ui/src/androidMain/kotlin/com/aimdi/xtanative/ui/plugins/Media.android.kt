package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Media3's player with its controls for a video, an MP4 or an HLS playlist; a GIF loops without them. */
@Composable
internal actual fun ItemVideoPage(item: MediaItem, active: Boolean) {
    val context = LocalContext.current
    val url = item.videoUrl ?: return
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = if (item.kind == MediaItem.Kind.GIF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(active) { player.playWhenReady = active }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = item.kind == MediaItem.Kind.VIDEO
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
