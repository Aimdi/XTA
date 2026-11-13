package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.media.OutsideVideo

/**
 * Where the desktop has no libmpv: the poster, and a click hands the video
 * to the system, outside the app: where it plays on the web when the
 * network says, else the file itself.
 */
@Composable
internal actual fun ItemVideoOutside(item: MediaItem) = OutsideVideo(item.imageUrl, item.alt, item.webUrl ?: item.videoUrl)
