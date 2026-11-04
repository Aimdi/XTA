package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.aimdi.xtanative.store.downloads.downloadRequestHeaders

/** [url] as Coil loads it, asked for with [headers]. */
internal fun pictureRequest(context: PlatformContext, url: String, headers: Map<String, String>): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (name, value) -> set(name, value) } }.build())
        .build()

/**
 * [url] as the app's pictures ask for it: with the headers its server
 * refuses a picture without (Pixiv's Referer), as the downloads ask for the
 * file; any other picture as it is.
 */
internal fun pictureModel(context: PlatformContext, url: String): Any {
    val headers = downloadRequestHeaders(url)
    return if (headers.isEmpty()) url else pictureRequest(context, url, headers)
}

/** [pictureModel] in a composable, made again only when [url] changes. */
@Composable
internal fun rememberPicture(url: String): Any {
    val context = LocalPlatformContext.current
    return remember(context, url) { pictureModel(context, url) }
}
