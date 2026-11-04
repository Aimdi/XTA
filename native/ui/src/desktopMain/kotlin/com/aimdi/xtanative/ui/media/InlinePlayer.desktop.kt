package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.LocalGraph

/**
 * The feed's player, libmpv's as XTA's feed plays on Linux. It looks for
 * the engine when the first tile plays, so a feed that plays nothing never
 * opens libmpv; without it a tile keeps its poster, and a tap opens the
 * media viewer.
 */
@Composable
actual fun rememberInlinePlayer(): InlinePlayer? {
    val graph = LocalGraph.current
    val player = remember { MpvInlinePlayer { graph.settings.state.value?.get(MediaPrefs.prefetchSeconds) ?: 0 } }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * One libmpv player for a whole feed, as Media3's is on Android: the tile
 * most in view plays in it, muted and looping, cropped to cover the tile as
 * its poster does. It is made when the first tile plays, with the reader's
 * read-ahead; a tile shows its own frames only, never the last tile's.
 */
private class MpvInlinePlayer(private val prefetchSeconds: () -> Int) : InlinePlayer {
    private var player: MpvPlayer? = null
    private var frames: MpvFrames? = null
    private var url: String? = null
    private var load = 0L
    private var resumed = true

    @Composable
    override fun Video(url: String, modifier: Modifier) {
        var shown by remember(url) { mutableLongStateOf(0L) }
        DisposableEffect(url) {
            shown = play(url)
            onDispose { stop(url) }
        }
        val frames = remember(shown) { this.frames }
        if (shown != 0L && frames != null) VideoSurface(frames, crop = true, modifier = modifier, shows = { it == shown })
    }

    /** [url] playing (opened unless it is what plays already); the number of its frames, 0 without a player. */
    private fun play(url: String): Long {
        val player = player() ?: return 0L
        if (this.url != url) {
            this.url = url
            load = player.open(url, 0, play = resumed)
        } else if (resumed) {
            player.play()
        }
        return load
    }

    /** Stops [url], unless another tile has started meanwhile. */
    private fun stop(url: String) {
        if (this.url != url) return
        this.url = null
        player?.stop()
    }

    private fun player(): MpvPlayer? {
        player?.let { return it }
        if (!MpvEngine.ready) return null
        val made = MpvPlayer.create(MpvOptions.video(prefetchSeconds(), loop = true, muted = true), video = true, name = "mpv-feed") ?: return null
        player = made
        frames = MpvFrames(made)
        return made
    }

    override fun pause() {
        resumed = false
        player?.pause()
    }

    override fun resume() {
        resumed = true
        if (url != null) player?.play()
    }

    fun release() {
        frames?.close()
        frames = null
        player = null
    }
}
