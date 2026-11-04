package com.aimdi.xtanative.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aimdi.xtanative.store.downloads.mimeTypeFor
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.downloads_open_file
import com.aimdi.xtanative.ui.media.EngineVideo
import com.aimdi.xtanative.ui.media.MpvEngine
import com.aimdi.xtanative.ui.media.MpvVideoPlayback
import com.aimdi.xtanative.ui.media.ViewerVideo
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The kept file under the viewer's controls through libmpv, with its sound
 * and no loop, as XTA's offline page opens it (media_kit's defaults): it
 * waits for a tap on play, and pauses once not shown or once the window is
 * no longer the one in front, as XTA's pauses when the app is not resumed.
 * Where the desktop has no libmpv, a click hands it to the system's player.
 */
@Composable
internal actual fun OfflineVideo(file: File, active: Boolean, modifier: Modifier) {
    if (!MpvEngine.ready) return SystemPlayer(file, modifier)
    val playback = remember(file) { MpvVideoPlayback(ViewerVideo(file.path, emptyList(), gif = false, loop = false, muted = false, prefetchSeconds = 0)) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    LaunchedEffect(active) { if (!active) playback.setActive(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playback.setActive(false) }
    EngineVideo(playback, poster = null, alt = null, controls = true, onDownload = null, modifier = modifier)
}

/** A click hands the kept video to the player the system has. */
@Composable
private fun SystemPlayer(file: File, modifier: Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val open = stringResource(Res.string.downloads_open_file)
    Box(
        modifier.clickable(role = Role.Button, onClickLabel = open) {
            scope.launch { if (!graph.platformFiles.openFile(file, mimeTypeFor(file.name))) KeepNotices.show(KeepNotice.OpenFailed) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text(open, color = Color.White, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
