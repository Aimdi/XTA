package com.aimdi.xtanative.ui.offline

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.media.ExoVideoPlayer
import com.aimdi.xtanative.ui.media.VideoChrome
import com.aimdi.xtanative.ui.media.VideoControls
import com.aimdi.xtanative.ui.media.VideoOptions
import com.aimdi.xtanative.ui.media.VideoSource
import java.io.File

/**
 * The kept file in the app's own player, under the controls every other
 * video has, with its sound and no loop: it waits for a tap on play, and
 * pauses once not shown or once the app is left. It was Media3's own player
 * view, which alone kept that view, its controls and the list library they
 * are built on in the app (native/PERF.md).
 */
@Composable
internal actual fun OfflineVideo(file: File, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val settings = LocalSettingsStore.current
    val player = remember(file) {
        ExoVideoPlayer(context, VideoOptions.of(settings.state.value)).apply {
            open(VideoSource(Uri.fromFile(file).toString(), loop = false, muted = false))
        }
    }
    DisposableEffect(player) { onDispose(player::release) }
    LaunchedEffect(active) { if (!active) player.setActive(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.setActive(false) }
    Box(modifier) {
        player.Picture(fill = false, Modifier.fillMaxSize())
        VideoControls(player, VideoChrome(), Modifier.fillMaxSize())
    }
}
