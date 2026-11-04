package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.LocalGraph

internal actual val viewerPageButtons: Boolean = true

/**
 * libmpv under XTA's controls for a video, a GIF looping without them,
 * silent, as Android plays them; the variant, sound and loop as the
 * settings and the app-wide mute say. Where the desktop has no libmpv: the
 * poster, and a click hands the video to the system's player. Saving it is
 * the same file a phone saves.
 */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?) {
    val graph = LocalGraph.current
    val poster = photoUrl(media.url, PhotoSize.LARGE)
    if (!MpvEngine.ready) {
        val url = remember(media) { VideoRules.streamUrl(media) }
        LaunchedEffect(url) { url?.let(onPlaying) }
        return OutsideVideo(poster, media.alt, url)
    }
    val video = remember(media) { ViewerVideo.of(media, graph.settings.state.value) } ?: return
    PlayingVideo(video, poster, media.alt, active, onPlaying, onDownload)
}
