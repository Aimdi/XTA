package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/**
 * [url] as Coil's AsyncImage loads it, asked for with [headers]: what an
 * image server refuses a picture without, such as Pixiv's Referer. A plugin
 * is built without Coil's network module, so it asks for such a picture here.
 */
fun imageRequest(context: PlatformContext, url: String, headers: Map<String, String>): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (name, value) -> set(name, value) } }.build())
        .build()

/** [imageRequest] in a composable, made again only when [url] or [headers] change. */
@Composable
fun rememberImageRequest(url: String, headers: Map<String, String>): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url, headers) { imageRequest(context, url, headers) }
}
