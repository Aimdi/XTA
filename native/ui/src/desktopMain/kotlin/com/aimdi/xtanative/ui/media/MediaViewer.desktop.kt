package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl

internal actual val viewerPageButtons: Boolean = true

/**
 * libmpv's player on the viewer's page, under XTA's controls in place, with
 * full screen from there, as Android's Media3 (no picture-in-picture on the
 * desktop). Where the desktop has no libmpv: the poster, and a click hands
 * the video to the system's player. Saving it is the same file a phone saves.
 */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?) {
    val players = rememberVideoPlayers()
    if (players != null) return PlayerVideoPage(media, active, players, onPlaying, onDownload)
    val url = remember(media) { VideoRules.streamUrl(media) }
    LaunchedEffect(url) { url?.let(onPlaying) }
    OutsideVideo(photoUrl(media.url, PhotoSize.LARGE), media.alt, url)
}
