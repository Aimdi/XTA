package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import coil3.compose.AsyncImage
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PlayBadge

/**
 * The desktop has no player of its own: the poster, and a click hands the
 * video to the system, outside the app: where it plays on the web when the
 * network says, else the file itself.
 */
@Composable
internal actual fun ItemVideoPage(item: MediaItem, active: Boolean) {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    val url = item.webUrl ?: item.videoUrl
    Box(
        Modifier.fillMaxSize().clickable(enabled = url != null) {
            url?.let { target -> links?.openOutside(target) ?: runCatching { uri.openUri(target) } }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = item.imageUrl, contentDescription = item.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (url != null) PlayBadge(Modifier)
    }
}
