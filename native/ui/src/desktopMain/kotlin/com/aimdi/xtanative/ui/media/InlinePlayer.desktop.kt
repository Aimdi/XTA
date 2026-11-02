package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/** No video plays in place on the desktop: the tile keeps its poster, and a tap opens the media viewer. */
@Composable
actual fun rememberInlinePlayer(): InlinePlayer? = null
