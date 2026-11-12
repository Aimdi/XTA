package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.LocalGraph

/**
 * A video or GIF on the viewer's page in a player of [players], playing
 * while it is the page shown (_media.dart's TweetVideo in TweetMediaView): a
 * video under XTA's controls in place, its mute corner and full-screen
 * button, a GIF looping silently. The variant is the one the video-quality
 * setting picks, and the poster covers the player until its first frame.
 * [onPlaying] hears the variant it plays; [onDownload] saves that one.
 */
@Composable
internal fun PlayerVideoPage(media: XMedia, active: Boolean, players: VideoPlayers, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?) {
    val graph = LocalGraph.current
    val source = remember(media) { VideoSource.of(media, graph.settings.state.value) }
    SourceVideoPage(source, media.aspectRatio, active, players, onPlaying, onDownload) {
        AsyncImage(model = photoUrl(media.url, PhotoSize.LARGE), contentDescription = media.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/**
 * [PlayerVideoPage] for any video: [source] in a player of [players], null
 * for nothing to play; [aspectRatio] its shape full screen, and [poster]
 * what covers it until its first frame and while full screen has it.
 */
@Composable
internal fun SourceVideoPage(
    source: VideoSource?,
    aspectRatio: Float?,
    active: Boolean,
    players: VideoPlayers,
    onPlaying: (String) -> Unit,
    onDownload: ((playingUrl: String) -> Unit)?,
    poster: @Composable () -> Unit,
) {
    val graph = LocalGraph.current
    val opened = remember(source, players) {
        source?.let {
            val player = players.make(VideoOptions.of(graph.settings.state.value)).apply { open(it) }
            player to KeptPlayer(player::release)
        }
    } ?: return
    val (player, kept) = opened
    DisposableEffect(kept) { onDispose(kept::left) }
    LaunchedEffect(player, active) { player.setActive(active) }
    PausesWhenLeft(player)
    val state by player.state.collectAsState()
    LaunchedEffect(state.url) { onPlaying(state.url) }
    val fullscreen = LocalVideoFullscreen.current
    val gif = source?.gif == true
    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        // Full screen has it: the page keeps its poster meanwhile.
        if (fullscreen?.shows(player) == true) return@Box poster()
        player.Picture(fill = false, Modifier.fillMaxSize())
        if (!gif) {
            val download: (() -> Unit)? = onDownload?.let { save -> { save(player.state.value.url) } }
            val open: (() -> Unit)? = fullscreen?.let { screen ->
                {
                    kept.enteredFullscreen()
                    screen.open(FullscreenVideo(player, aspectRatio ?: (16f / 9f), download, onClosed = kept::leftFullscreen))
                }
            }
            VideoControls(player, VideoChrome(onFullscreen = open, onDownload = download), Modifier.fillMaxSize())
        }
        // Over the player: a surface of its own lies behind the window, and would clear a poster drawn under it.
        PosterUntilStarted(state, Modifier.fillMaxSize(), poster = poster)
        if (!gif) MuteCorner(state.muted, onToggle = { toggleMute(player, state.muted) }, Modifier.align(Alignment.BottomEnd))
    }
}
