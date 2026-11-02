package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.core.x.model.playableUrl
import com.aimdi.xtanative.ui.post.PlayBadge

/**
 * The desktop has no player of its own: the poster, and a click hands the
 * video to the system, which opens it in the browser or the video player
 * set for it.
 */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean) {
    val uri = LocalUriHandler.current
    val url = remember(media) { media.playableUrl() }
    Box(
        Modifier.fillMaxSize().clickable(enabled = url != null) { url?.let { runCatching { uri.openUri(it) } } },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photoUrl(media.url, PhotoSize.LARGE),
            contentDescription = media.alt,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (url != null) PlayBadge(Modifier)
    }
}
