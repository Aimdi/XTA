package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/** No engine of the desktop's own yet: a video's poster hands it to the system's player, and nothing plays in place. */
@Composable
internal actual fun rememberVideoPlayers(): VideoPlayers? = null
