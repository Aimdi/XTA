package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.LocalGraph

/**
 * A feed's one player (XTA's video pool, kept to one): its tiles take turns
 * at it ([turns]), an X video under XTA's controls ([TileVideo]), a GIF
 * looping ([TileGif]); a plugin's tile loops a silent preview in it ([Video])
 * while Autoplay videos is on, as before.
 */
internal class FeedPlayer(val turns: PlayerTurns<VideoPlayer>) : InlinePlayer {
    @Composable
    override fun Video(url: String, modifier: Modifier) {
        val settings by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
        if (settings?.get(MediaPrefs.autoplay) != true) return
        LoopingVideo(turns, VideoSource.looping(url), poster = null, modifier)
    }

    override fun pause() = turns.pause()

    override fun resume() = turns.resume()
}

/** A feed's one player over [players], let go with the feed: what `rememberInlinePlayer` gives where the platform plays videos. */
@Composable
internal fun rememberFeedPlayer(players: VideoPlayers): InlinePlayer {
    val turns = rememberPlayerTurns(players, shared = true)
    return remember(turns) { FeedPlayer(turns) }
}

/** The player a tile plays in: its feed's, else one of its own; null where nothing plays in place (the desktop, for now). */
@Composable
internal fun rememberTilePlayer(): PlayerTurns<VideoPlayer>? {
    (LocalInlinePlayer.current as? FeedPlayer)?.let { return it.turns }
    val players = rememberVideoPlayers() ?: return null
    return rememberPlayerTurns(players, shared = false)
}

/** Turns at a player made as the settings say when first wanted, let go with the caller. */
@Composable
private fun rememberPlayerTurns(players: VideoPlayers, shared: Boolean): PlayerTurns<VideoPlayer> {
    val graph = LocalGraph.current
    val turns = remember(players) {
        val options = VideoOptions.of(graph.settings.state.value)
        PlayerTurns({ players.make(options) }, options.backgroundPlay, shared)
    }
    DisposableEffect(turns) { onDispose(turns::release) }
    return turns
}
