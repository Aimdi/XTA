package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * One player for a whole feed: the video or GIF most in view plays in its
 * tile, muted and looping, as X's feed plays them.
 */
interface InlinePlayer {
    /** [url] playing in its tile, over the tile's poster until the first frame. */
    @Composable
    fun Video(url: String, modifier: Modifier)

    /** The feed is no longer seen: nothing plays until it is again. */
    fun pause()

    fun resume()
}

/**
 * A feed page's player, released with the page. Null where the platform plays
 * nothing in place: on the desktop the poster shows, and a tap opens the viewer.
 */
@Composable
expect fun rememberInlinePlayer(): InlinePlayer?

/** The feed's player, for the tiles in it; null where nothing plays inline. */
val LocalInlinePlayer = staticCompositionLocalOf<InlinePlayer?> { null }
