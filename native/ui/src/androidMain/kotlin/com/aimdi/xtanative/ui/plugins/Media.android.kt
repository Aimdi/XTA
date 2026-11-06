package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.aimdi.xtanative.ui.common.rememberPicture

/** Android always has Media3's player, so this is only the poster. */
@Composable
internal actual fun ItemVideoOutside(item: MediaItem) {
    AsyncImage(model = rememberPicture(item.imageUrl), contentDescription = item.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
}
