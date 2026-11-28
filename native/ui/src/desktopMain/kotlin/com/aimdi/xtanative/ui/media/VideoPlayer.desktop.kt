package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.net.DesktopNetworkMonitor

/**
 * libmpv's players, where the desktop has it ([MpvEngine]), as XTA's
 * media_kit plays on Linux: the feed's tiles, the viewer's page and full
 * screen take them as Android takes Media3's. None without it, so a video's
 * poster hands it to the system's player, as before. Whether it has it is
 * asked without loading it ([MpvEngine.expected]); a tile on screen loads it.
 */
@Composable
internal actual fun rememberVideoPlayers(): VideoPlayers? = remember { if (MpvEngine.expected) MpvVideoPlayers else null }

private object MpvVideoPlayers : VideoPlayers {
    override fun make(options: VideoOptions): VideoPlayer = MpvVideoPlayer(options)

    override fun warm() = MpvEngine.warmUp()
}

/**
 * The desktop's data saver: on while the connection is metered, as the
 * network portal says ([DesktopNetworkMonitor.metered]), so videos stop
 * playing by themselves on a phone's tether as they do under Android's Data Saver.
 */
@Composable
internal actual fun rememberDataSaver(): Boolean {
    val monitor = DesktopNetworkMonitor.desktop
    LaunchedEffect(monitor) { monitor.start() }
    return monitor.metered.collectAsState().value
}

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
