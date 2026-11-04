package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.LocalGraph

/**
 * A platform's player for one video at a time, which XTA's controls drive
 * ([VideoPlayback]) and [Picture] shows: a feed's tiles take turns at one
 * ([PlayerTurns]); the viewer's page and a tile of a screen without a feed's
 * player have one each.
 */
internal interface VideoPlayer : PlayerCore {
    /**
     * Its picture, fitted to [modifier]'s box or, with [fill], filling it as
     * a tile's poster does. It is drawn in one place at a time: the place
     * composed last has it.
     */
    @Composable
    fun Picture(fill: Boolean, modifier: Modifier)
}

/** Makes the platform's players, as Settings › Media says ([VideoOptions]). */
internal fun interface VideoPlayers {
    fun make(options: VideoOptions): VideoPlayer
}

/**
 * The players of this platform: Media3's on Android; none on the desktop,
 * which has no engine of its own yet, so a video's poster leads to the
 * system's player there, as before.
 */
@Composable
internal expect fun rememberVideoPlayers(): VideoPlayers?

/** Pauses [player] when the app is left, unless Background play keeps a video that is heard going (pauseUponEnteringBackgroundMode). */
@Composable
internal fun PausesWhenLeft(player: VideoPlayer) {
    val graph = LocalGraph.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val background = graph.settings.state.value?.get(MediaPrefs.backgroundPlayback) ?: true
        if (VideoRules.pausesWhenLeft(background, heard = !player.state.value.muted)) player.setActive(false)
    }
}
