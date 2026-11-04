package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * libmpv's players, where the desktop has it ([MpvEngine]), as XTA's
 * media_kit plays on Linux: the feed's tiles, the viewer's page and full
 * screen take them as Android takes Media3's. None without it, so a video's
 * poster hands it to the system's player, as before.
 */
@Composable
internal actual fun rememberVideoPlayers(): VideoPlayers? = remember { if (MpvEngine.ready) VideoPlayers { options -> MpvVideoPlayer(options) } else null }

/** libmpv under XTA's controls ([MpvVideoCore]), its picture drawn by [MpvFrames] where it was put on screen last. */
internal class MpvVideoPlayer private constructor(private val core: MpvVideoCore<MpvFrames>) : VideoPlayer, PlayerCore by core {
    constructor(options: VideoOptions) : this(MpvVideoCore(options, ::MpvFrames))

    /** Its frames, for the video check to count; none when mpv made no player. */
    val frames: MpvFrames? get() = core.pictures

    suspend fun property(name: String): String? = core.property(name)

    @Composable
    override fun Picture(fill: Boolean, modifier: Modifier) {
        val frames = core.pictures ?: return
        VideoSurface(frames, crop = fill, modifier = modifier, shows = { it == core.playing })
    }
}
