package com.aimdi.xtanative.ui.offline

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/** Media3's player with its controls, over the kept file; it waits for a tap on play, and pauses once not shown. */
@Composable
internal actual fun OfflineVideo(file: File, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(active) { if (!active) player.pause() }
    AndroidView(
        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
        modifier = modifier,
    )
}
