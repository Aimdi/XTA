package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.aimdi.xtanative.ui.common.pictureRequest

/**
 * [url] as Coil's AsyncImage loads it, asked for with [headers]: what an
 * image server refuses a picture without, such as Pixiv's Referer. A plugin
 * is built without Coil's network module, so it asks for such a picture here.
 */
fun imageRequest(context: PlatformContext, url: String, headers: Map<String, String>): ImageRequest = pictureRequest(context, url, headers)

/** [imageRequest] in a composable, made again only when [url] or [headers] change. */
@Composable
fun rememberImageRequest(url: String, headers: Map<String, String>): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url, headers) { imageRequest(context, url, headers) }
}
