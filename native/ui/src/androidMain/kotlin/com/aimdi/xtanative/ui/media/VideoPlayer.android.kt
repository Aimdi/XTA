package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberVideoPlayers(): VideoPlayers? {
    val context = LocalContext.current
    return remember(context) { VideoPlayers { options -> ExoVideoPlayer(context, options) } }
}
