package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.media.MpvEngine
import com.aimdi.xtanative.ui.media.OutsideVideo
import com.aimdi.xtanative.ui.media.PlayingVideo
import com.aimdi.xtanative.ui.media.ViewerVideo

/**
 * A network's video in the app through libmpv, under the viewer's controls
 * and with its rules, as XTA plays a plugin's video (a GIF loops without
 * them). Where the desktop has no libmpv, or the network gives no file to
 * play: the poster, and a click hands the video to the system, outside the
 * app: where it plays on the web when the network says, else the file itself.
 */
@Composable
internal actual fun ItemVideoPage(item: MediaItem, active: Boolean) {
    val graph = LocalGraph.current
    val url = item.videoUrl
    if (url == null || !MpvEngine.ready) return OutsideVideo(item.imageUrl, item.alt, item.webUrl ?: item.videoUrl)
    val video = remember(url) { ViewerVideo.of(url, emptyList(), item.kind == MediaItem.Kind.GIF, graph.settings.state.value) }
    PlayingVideo(video, item.imageUrl, item.alt, active)
}
