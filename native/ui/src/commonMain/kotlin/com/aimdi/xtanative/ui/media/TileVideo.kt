package com.aimdi.xtanative.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_media_play
import com.aimdi.xtanative.ui.generated.resources.reader_video
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * A post's video in its tile, playing there under XTA's controls (_video.dart's
 * TweetVideo): the mute corner, the ⋮ menu and full screen, looping as Loop
 * videos says, with the sound as the app's mute stands. With Autoplay videos
 * on it plays by itself once it has stayed in view a moment (in a feed, when
 * its row is the one the feed plays); otherwise a tap plays it in place. It
 * pauses while less than half of it is seen. [poster] is the tile's picture,
 * kept over the video until its first frame; [post] names a saved file. Where
 * nothing plays in place, it draws nothing and the tile's tap opens the viewer.
 */
@Composable
internal fun TileVideo(media: XMedia, poster: Any?, post: XPost?, playing: Boolean, modifier: Modifier = Modifier) =
    TileVideo({ VideoSource.of(media, it) }, media.alt, media.aspectRatio, poster, playing, modifier) { player -> rememberTileDownload(media, post, player) }

/**
 * [TileVideo] for any video: [source] what it plays as the settings given
 * say, [alt] its description, [aspectRatio] its shape full screen, and
 * [download] the ⋮ menu's Download of what [VideoPlayer] plays, null for none.
 */
@Composable
internal fun TileVideo(
    source: (Settings?) -> VideoSource?,
    alt: String?,
    aspectRatio: Float?,
    poster: Any?,
    playing: Boolean,
    modifier: Modifier = Modifier,
    download: @Composable (VideoPlayer) -> (() -> Unit)?,
) {
    val tiles = rememberTilePlayer() ?: return
    val settings by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    val key = remember { Any() }
    var seen by remember { mutableStateOf(false) }
    val autoplay = settings?.get(MediaPrefs.autoplay) == true
    val ask by rememberUpdatedState<() -> Unit> { source(settings)?.let { tiles.ask(key, it) } }
    val byItself = VideoRules.startsByItself(autoplay, seen, playing, tiles.shared)
    LaunchedEffect(tiles, byItself) {
        if (!byItself) return@LaunchedEffect
        delay(VideoRules.SETTLE_MS)
        ask()
    }
    DisposableEffect(tiles, key) { onDispose { tiles.drop(key) } }
    val turn by tiles.holder.collectAsState()
    val player = tiles.playerOf(key)?.takeIf { turn?.key == key }
    val placed = modifier.onGloballyPositioned { seen = VideoRules.mostlySeen(it.boundsInWindow().height, it.size.height) }
    val fullscreen = LocalVideoFullscreen.current
    when {
        player == null -> IdleTile(autoplay, settings?.get(MediaPrefs.mute) ?: true, onPlay = { ask() }, modifier = placed)
        // Full screen has it: the tile keeps its poster meanwhile.
        fullscreen?.shows(player) == true -> Box(placed)
        else -> {
            val save = download(player)
            val open: (() -> Unit)? = fullscreen?.let { screen ->
                {
                    tiles.pin(key)
                    screen.open(FullscreenVideo(player, aspectRatio ?: (16f / 9f), save) { tiles.unpin(key) })
                }
            }
            PlayingTile(player, alt, poster, seen, own = !tiles.shared, chrome = VideoChrome(onFullscreen = open, onDownload = save), modifier = placed)
        }
    }
}

/** A GIF looping silently in its tile while it is seen (in a feed, while its row is the one the feed plays), as XTA's GIFs loop whatever Autoplay says. */
@Composable
internal fun TileGif(media: XMedia, poster: Any?, playing: Boolean, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val source = remember(media) { VideoSource.of(media, graph.settings.state.value) }
    TileGif(source, poster, playing, modifier)
}

/** [TileGif] for any GIF: [source] what loops, null for nothing. */
@Composable
internal fun TileGif(source: VideoSource?, poster: Any?, playing: Boolean, modifier: Modifier = Modifier) {
    val tiles = rememberTilePlayer() ?: return
    var seen by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    val wanted = VideoRules.gifLoops(seen, playing, tiles.shared)
    LaunchedEffect(wanted) {
        settled = false
        if (!wanted) return@LaunchedEffect
        delay(VideoRules.SETTLE_MS)
        settled = true
    }
    Box(modifier.onGloballyPositioned { seen = VideoRules.mostlySeen(it.boundsInWindow().height, it.size.height) }) {
        if (settled && source != null) LoopingVideo(tiles, source, poster, Modifier.matchParentSize())
    }
}

/** [source] looping silently in [tiles]' player while no video has it, over [poster] until its first frame. */
@Composable
internal fun LoopingVideo(tiles: PlayerTurns<VideoPlayer>, source: VideoSource, poster: Any?, modifier: Modifier) {
    val key = remember { Any() }
    DisposableEffect(tiles, source) {
        tiles.want(key, source)
        onDispose { tiles.drop(key) }
    }
    val turn by tiles.holder.collectAsState()
    val player = tiles.playerOf(key)?.takeIf { turn?.key == key } ?: return
    val state by player.state.collectAsState()
    Box(modifier) {
        player.Picture(fill = true, Modifier.matchParentSize())
        if (poster != null) PosterUntilStarted(state, Modifier.matchParentSize()) { TilePoster(poster, alt = null) }
    }
}

/**
 * [poster] over a player until its first frame, then fading out, as XTA's
 * poster overlay does, so the swap shows no black (at once with reduced
 * motion); never over a failure, which says why.
 */
@Composable
internal fun PosterUntilStarted(state: VideoState, modifier: Modifier, poster: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = !state.started && !state.failed,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduced) ExitTransition.None else fadeOut(tween(MOTION_STANDARD_MS)),
    ) { poster() }
}

@Composable
private fun TilePoster(poster: Any?, alt: String?) {
    AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** A video waiting in its tile: a tap plays it there; with Autoplay on, the mute corner says how it will start. */
@Composable
private fun IdleTile(autoplay: Boolean, muteSetting: Boolean, onPlay: () -> Unit, modifier: Modifier) {
    val video = stringResource(Res.string.reader_video)
    Box(modifier.clickable(onClickLabel = stringResource(Res.string.reader_media_play), role = Role.Button, onClick = onPlay).semantics { contentDescription = video }) {
        if (autoplay) {
            val chosen by VideoMute.choice.collectAsState()
            val muted = chosen ?: muteSetting
            MuteCorner(muted, onToggle = { VideoMute.set(!muted) }, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** The video playing in its tile: the picture filling it, the controls, the poster until the first frame, and the mute corner. */
@Composable
private fun PlayingTile(player: VideoPlayer, alt: String?, poster: Any?, seen: Boolean, own: Boolean, chrome: VideoChrome, modifier: Modifier) {
    val state by player.state.collectAsState()
    PausesUnseen(player, seen)
    // A feed's own player stops and starts again with its screen (InlinePlayer.pause).
    if (own) PausesWhenLeft(player)
    Box(modifier.background(Color.Black)) {
        player.Picture(fill = true, Modifier.matchParentSize())
        VideoControls(player, chrome, Modifier.matchParentSize())
        // Over the controls, as XTA's poster lies over its player; taps reach the controls through it.
        PosterUntilStarted(state, Modifier.matchParentSize()) { TilePoster(poster, alt) }
        MuteCorner(state.muted, onToggle = { toggleMute(player, state.muted) }, Modifier.align(Alignment.BottomEnd))
    }
}

/** Pauses [player] once less than half of its tile has been seen for a moment, and plays it again when it is back. */
@Composable
private fun PausesUnseen(player: VideoPlayer, seen: Boolean) {
    var paused by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player, seen) {
        if (seen) {
            if (paused) player.setActive(true)
            paused = false
            return@LaunchedEffect
        }
        delay(VideoRules.UNSEEN_PAUSE_MS)
        if (player.state.value.active) {
            paused = true
            player.setActive(false)
        }
    }
}

/** The tile's Download: the variant it plays, saved under its author's name, where the downloads save and the video has a file. */
@Composable
private fun rememberTileDownload(media: XMedia, post: XPost?, player: VideoPlayer): (() -> Unit)? {
    val saver = LocalMediaSaver.current ?: return null
    if (MediaFiles.downloadUrl(media) == null) return null
    return remember(saver, media, post, player) {
        { MediaFiles.save(media, post?.id.orEmpty(), post?.author?.screenName.orEmpty(), player.state.value.url)?.let(saver::save) }
    }
}
