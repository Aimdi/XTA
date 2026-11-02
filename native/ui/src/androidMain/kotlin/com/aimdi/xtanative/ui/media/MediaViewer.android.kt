package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.playableUrl

/** Media3's player with its controls for a video; a GIF loops without them. */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean) {
    val context = LocalContext.current
    val url = remember(media) { media.playableUrl() } ?: return
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (media.kind == XMedia.Kind.GIF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(active) { player.playWhenReady = active }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = media.kind == XMedia.Kind.VIDEO
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
