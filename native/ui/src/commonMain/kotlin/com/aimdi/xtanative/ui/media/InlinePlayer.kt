package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * One player for a whole feed, which its tiles take turns at: an X video
 * plays in its tile under XTA's controls ([TileVideo]), a GIF loops there
 * ([TileGif]), and a plugin's tile loops a silent preview in it ([Video]).
 */
interface InlinePlayer {
    /** [url] looping silently in its tile while Autoplay videos is on, over the tile's poster until the first frame. */
    @Composable
    fun Video(url: String, modifier: Modifier)

    /** The feed is no longer seen: nothing plays until it is again, but a video full screen, or one heard while Background play allows. */
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
