package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PlayBadge

/** [player]'s picture, its [poster] over it until the first frame, and the viewer's [controls] (none for a GIF). */
@Composable
internal fun EngineVideo(
    player: VideoPlayer,
    poster: Any?,
    alt: String?,
    controls: Boolean,
    onDownload: (() -> Unit)?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val state by player.state.collectAsStateWithLifecycle()
    var fill by remember { mutableStateOf(false) }
    Box(modifier) {
        player.Picture(fill, Modifier.fillMaxSize())
        if (!state.started && poster != null) {
            AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (controls) VideoControls(player, fill, onToggleFill = { fill = !fill }, onDownload = onDownload)
    }
}

/**
 * Where the desktop has no player (no libmpv, [MpvEngine]): the poster, and
 * a click hands [url] to the system, which opens it in the browser or the
 * video player set for it.
 */
@Composable
internal fun OutsideVideo(poster: Any?, alt: String?, url: String?) {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    Box(
        Modifier.fillMaxSize().clickable(enabled = url != null) {
            url?.let { target -> links?.openOutside(target) ?: runCatching { uri.openUri(target) } }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (url != null) PlayBadge(Modifier)
    }
}
