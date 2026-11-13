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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_video
import com.aimdi.xtanative.ui.generated.resources.reader_video_length
import com.aimdi.xtanative.ui.generated.resources.reader_video_open_sound
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * A post's video in its tile, quiet until it is opened, as X's feed plays
 * one: with Autoplay videos on (unless motion is reduced or the phone saves
 * data) it plays by itself, with the sound as the app's mute stands (muted
 * unless the reader turned it on), once most of it has stayed in view a
 * moment (in a feed, while it is the tile seen most: [MostSeen]), pauses
 * when it leaves, and plays on from there when it is back. Its corners hold
 * the time left (with a small ▶ while nothing starts it by itself) and the
 * mute switch; a tap anywhere else opens it full screen with its sound,
 * going on from where it was. [poster] is the tile's picture, kept over the
 * video until its first frame; [post] names a saved file. Where nothing
 * plays in place, only the corner shows and the tile's tap opens the viewer.
 */
@Composable
internal fun TileVideo(media: XMedia, poster: Any?, post: XPost?, modifier: Modifier = Modifier) =
    TileVideo({ VideoSource.of(media, it) }, media.alt, media.aspectRatio, media.durationMs, poster, modifier) { rememberTileDownload(media, post) }

/**
 * [TileVideo] for any video: [source] what it plays as the settings given
 * say, [alt] its description, [aspectRatio] its shape full screen,
 * [durationMs] its length as its post says (null when it does not), and
 * [download] full screen's Download of what a player plays, null for none.
 */
@Composable
internal fun TileVideo(
    source: (Settings?) -> VideoSource?,
    alt: String?,
    aspectRatio: Float?,
    durationMs: Long?,
    poster: Any?,
    modifier: Modifier = Modifier,
    download: @Composable () -> ((VideoPlayer) -> Unit)?,
) {
    val tiles = rememberTilePlayer() ?: return Box(modifier) { TileCorner(VideoRules.cornerTimeMs(null, durationMs), play = true, Modifier.align(Alignment.BottomStart)) }
    val settings by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    val key = remember { Any() }
    var seen by remember { mutableStateOf(false) }
    var share by remember { mutableFloatStateOf(0f) }
    val autoplay = VideoRules.autoplays(settings?.get(MediaPrefs.autoplay) == true, LocalReducedMotion.current, rememberDataSaver())
    val muteSetting = settings?.get(MediaPrefs.mute) ?: true
    val chosen by tiles.seen.chosen.collectAsState()
    ReportsSeen(tiles, key, share, reports = autoplay && tiles.shared)
    val ask by rememberUpdatedState<() -> Unit> { source(settings)?.let { tiles.ask(key, it) } }
    val byItself = VideoRules.startsByItself(autoplay, share, chosen === key, tiles.shared)
    LaunchedEffect(tiles, byItself) {
        if (!byItself) return@LaunchedEffect
        delay(VideoRules.SETTLE_MS)
        ask()
    }
    DisposableEffect(tiles, key) { onDispose { tiles.drop(key) } }
    val turn by tiles.holder.collectAsState()
    val player = tiles.playerOf(key)?.takeIf { turn?.key == key }
    val placed = modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        seen = VideoRules.mostlySeen(bounds.height, coordinates.size.height)
        share = VideoRules.shareStep(VideoRules.seenShare(bounds.width, bounds.height, coordinates.size.width, coordinates.size.height))
    }
    val fullscreen = LocalVideoFullscreen.current
    val save = download()
    val open: () -> Unit = open@{
        // Its sound on, from where it is: the tile takes the player first if it has not.
        val playing = tiles.playerOf(key)?.takeIf { tiles.holder.value?.key == key }
            ?: source(settings)?.let { tiles.ask(key, it.copy(muted = false)); tiles.playerOf(key) }
            ?: return@open
        playing.setMuted(false)
        if (playing.state.value.completed) playing.seekTo(0)
        playing.setActive(true)
        val screen = fullscreen ?: return@open
        tiles.pin(key)
        val downloads: (() -> Unit)? = save?.let { saves -> { saves(playing) } }
        screen.open(
            FullscreenVideo(playing, aspectRatio ?: (16f / 9f), downloads) {
                tiles.unpin(key)
                playing.setMuted(VideoMute.muted(muteSetting))
            },
        )
    }
    val label = TileLabels(durationMs)
    when {
        player == null -> IdleTile(autoplay, VideoMute.muted(muteSetting), durationMs, label, onOpen = open, modifier = placed)
        // Full screen has it: the tile keeps its poster meanwhile.
        fullscreen?.shows(player) == true -> Box(placed)
        else -> PlayingTile(player, alt, poster, seen, own = !tiles.shared, durationMs, label, onOpen = open, modifier = placed)
    }
}

/** Tells the feed's [tiles] how much of tile [key] is seen ([share]) while it [reports], and forgets it when it does not or is gone. */
@Composable
private fun ReportsSeen(tiles: PlayerTurns<VideoPlayer>, key: Any, share: Float, reports: Boolean) {
    DisposableEffect(tiles, key, share, reports) {
        if (reports) tiles.seen.report(key, share) else tiles.seen.forget(key)
        onDispose { }
    }
    DisposableEffect(tiles, key) { onDispose { tiles.seen.forget(key) } }
}

/** A GIF looping silently in its tile while it is seen (in a feed, while it is the tile seen most), as XTA's GIFs loop whatever Autoplay says. */
@Composable
internal fun TileGif(media: XMedia, poster: Any?, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val source = remember(media) { VideoSource.of(media, graph.settings.state.value) }
    TileGif(source, poster, modifier)
}

/** [TileGif] for any GIF: [source] what loops, null for nothing. */
@Composable
internal fun TileGif(source: VideoSource?, poster: Any?, modifier: Modifier = Modifier) {
    val tiles = rememberTilePlayer() ?: return
    val key = remember { Any() }
    var share by remember { mutableFloatStateOf(0f) }
    var settled by remember { mutableStateOf(false) }
    val chosen by tiles.seen.chosen.collectAsState()
    ReportsSeen(tiles, key, share, reports = tiles.shared && source != null)
    val wanted = VideoRules.gifLoops(share, chosen === key, tiles.shared)
    LaunchedEffect(wanted) {
        settled = false
        if (!wanted) return@LaunchedEffect
        delay(VideoRules.SETTLE_MS)
        settled = true
    }
    val placed = modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        share = VideoRules.shareStep(VideoRules.seenShare(bounds.width, bounds.height, coordinates.size.width, coordinates.size.height))
    }
    Box(placed) {
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
 * motion); not over a failure, where controls say why, unless
 * [overFailure] (a tile, which has none, keeps its picture).
 */
@Composable
internal fun PosterUntilStarted(state: VideoState, modifier: Modifier, overFailure: Boolean = false, poster: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = !state.started && (overFailure || !state.failed),
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduced) ExitTransition.None else fadeOut(tween(MOTION_STANDARD_MS)),
    ) { poster() }
}

@Composable
private fun TilePoster(poster: Any?, alt: String?) {
    AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** What a video tile says to a screen reader: that it is a video, how long, and that a tap opens it full screen with sound. */
private class TileLabels(val description: String, val onClick: String)

@Composable
private fun TileLabels(durationMs: Long?): TileLabels {
    val time = VideoRules.cornerTimeMs(null, durationMs)
    val description = if (time == null) stringResource(Res.string.reader_video) else stringResource(Res.string.reader_video_length, VideoRules.timeText(time))
    return TileLabels(description, stringResource(Res.string.reader_video_open_sound))
}

/**
 * A video waiting in its tile: its length in the corner, with a small ▶
 * while nothing starts it by itself; with Autoplay, the mute switch says
 * how it will start. A tap opens it full screen with sound.
 */
@Composable
private fun IdleTile(autoplay: Boolean, muted: Boolean, durationMs: Long?, labels: TileLabels, onOpen: () -> Unit, modifier: Modifier) {
    Box(modifier.clickable(onClickLabel = labels.onClick, role = Role.Button, onClick = onOpen).semantics { contentDescription = labels.description }) {
        TileCorner(VideoRules.cornerTimeMs(null, durationMs), play = VideoRules.cornerPlay(autoplay, holding = false), Modifier.align(Alignment.BottomStart))
        if (autoplay) {
            val chosen by VideoMute.choice.collectAsState()
            MuteCorner(chosen ?: muted, onToggle = { VideoMute.set(!(chosen ?: muted)) }, Modifier.align(Alignment.BottomEnd), disc = TileMuteDisc, inset = TileInset)
        }
    }
}

/**
 * The video playing in its tile: the picture filling it, the poster until
 * its first frame (and over a failure, which full screen says and retries),
 * the time left and the mute switch; a tap opens it full screen with sound.
 */
@Composable
private fun PlayingTile(player: VideoPlayer, alt: String?, poster: Any?, seen: Boolean, own: Boolean, durationMs: Long?, labels: TileLabels, onOpen: () -> Unit, modifier: Modifier) {
    val state by player.state.collectAsState()
    PausesUnseen(player, seen)
    // A feed's own player stops and starts again with its screen (InlinePlayer.pause).
    if (own) PausesWhenLeft(player)
    Box(
        modifier
            .background(Color.Black)
            .clickable(onClickLabel = labels.onClick, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = labels.description },
    ) {
        player.Picture(fill = true, Modifier.matchParentSize())
        PosterUntilStarted(state, Modifier.matchParentSize(), overFailure = true) { TilePoster(poster, alt) }
        TileCorner(VideoRules.cornerTimeMs(state, durationMs), play = false, Modifier.align(Alignment.BottomStart))
        MuteCorner(state.muted, onToggle = { toggleMute(player, state.muted) }, Modifier.align(Alignment.BottomEnd), disc = TileMuteDisc, inset = TileInset)
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

/** The tile's Download: the variant a player plays, saved under its author's name, where the downloads save and the video has a file. */
@Composable
private fun rememberTileDownload(media: XMedia, post: XPost?): ((VideoPlayer) -> Unit)? {
    val saver = LocalMediaSaver.current ?: return null
    if (MediaFiles.downloadUrl(media) == null) return null
    return remember(saver, media, post) {
        { player -> MediaFiles.save(media, post?.id.orEmpty(), post?.author?.screenName.orEmpty(), player.state.value.url)?.let(saver::save) }
    }
}

/** The mute switch of a tile: smaller than the viewer's, as X's feed draws it, inside the same touch target. */
private val TileMuteDisc = 28.dp
private val TileInset = 10.dp
