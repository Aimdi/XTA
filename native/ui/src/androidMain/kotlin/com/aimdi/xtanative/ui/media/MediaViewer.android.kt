package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.core.x.model.XMedia

internal actual val viewerPageButtons: Boolean = false

/** Media3's player on the viewer's page, under XTA's controls in place, with full screen and picture-in-picture from there. */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?) {
    val players = rememberVideoPlayers() ?: return
    PlayerVideoPage(media, active, players, onPlaying, onDownload)
}
