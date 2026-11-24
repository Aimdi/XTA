package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.shortcutKey
import com.aimdi.xtanative.ui.home.HoldsKeys

/** A video full screen: its player, its shape, its Download, and what is done once it closes. */
internal class FullscreenVideo(val player: VideoPlayer, val aspectRatio: Float, val onDownload: (() -> Unit)?, val onClosed: () -> Unit)

/** The video full screen over the whole window, if any (video_fullscreen.dart's route): one at a time. */
@Stable
internal class VideoFullscreen {
    var shown: FullscreenVideo? by mutableStateOf(null)
        private set

    fun open(video: FullscreenVideo) {
        shown?.onClosed?.invoke()
        shown = video
    }

    fun close() {
        val closing = shown ?: return
        shown = null
        closing.onClosed()
    }

    fun shows(player: VideoPlayer): Boolean = shown?.player === player
}

internal val LocalVideoFullscreen = staticCompositionLocalOf<VideoFullscreen?> { null }

/**
 * Where a video goes full screen, over [content] and all else in the window
 * (video_fullscreen.dart): upright and without the system bars on Android,
 * turned sideways and back by the controls' Rotate, filling the screen on the desktop, with
 * picture-in-picture where the phone has it (video_pip.dart). Back, or Esc
 * on the desktop, leaves it.
 */
@Composable
internal fun VideoFullscreenFrame(content: @Composable () -> Unit) {
    val fullscreen = remember { VideoFullscreen() }
    CompositionLocalProvider(LocalVideoFullscreen provides fullscreen) {
        Box(Modifier.fillMaxSize()) {
            content()
            fullscreen.shown?.let { video -> key(video) { FullscreenLayer(video, onClose = fullscreen::close) } }
        }
    }
}

/** The video on black, with the controls of full screen: mute, picture-in-picture, fit or fill, and leaving; only the video in picture-in-picture. */
@Composable
private fun FullscreenLayer(video: FullscreenVideo, onClose: () -> Unit) {
    val player = video.player
    val pip = rememberPictureInPicture(onDismissed = { player.setActive(false) })
    var sideways by remember { mutableStateOf(false) }
    FullscreenWindow(sideways)
    BackHandling(enabled = true, onBack = onClose)
    PausesWhenLeft(player)
    var fill by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Left, it hands the keys back to the viewer or the page under it.
    HoldsKeys(focus)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nothing under it is touched while it is up, picture-in-picture included.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .onKeyEvent { event -> (event.type == KeyEventType.KeyDown || typedChar(event) != null) && fullscreenKey(event, player, onClose) }
            .focusRequester(focus)
            .focusable(),
    ) {
        player.Picture(fill, Modifier.fillMaxSize())
        if (pip?.active != true) {
            val chrome = VideoChrome(
                fullscreen = true,
                onFullscreen = onClose,
                onPictureInPicture = pip?.let { { it.enter(video.aspectRatio) } },
                fill = fill,
                onToggleFill = { fill = !fill },
                onDownload = video.onDownload,
                onTurn = if (FullscreenTurns) ({ sideways = !sideways }) else null,
            )
            VideoControls(player, chrome, Modifier.fillMaxSize().safeDrawingPadding())
        }
    }
}

/** Picture-in-picture where the platform has it (video_pip.dart): whether the app is shrunk into its floating window now, and asking for it. */
internal interface PictureInPicture {
    val active: Boolean

    /** Shrinks the app into a floating window shaped like a video of [aspectRatio]; false when the phone refuses. */
    fun enter(aspectRatio: Float): Boolean
}

/**
 * While composed, the window shows a video full screen: Android holds the
 * phone upright, or sideways while [sideways], and hides the system bars
 * (until a swipe shows them for a moment); the desktop window fills the
 * screen.
 */
@Composable
internal expect fun FullscreenWindow(sideways: Boolean)

/** Whether full screen can be turned sideways ([FullscreenWindow]'s `sideways`): on a phone, not in a desktop window. */
internal expect val FullscreenTurns: Boolean

/** Picture-in-picture, or null where there is none (the desktop, a phone without it); [onDismissed] hears its window closed. */
@Composable
internal expect fun rememberPictureInPicture(onDismissed: () -> Unit): PictureInPicture?

/** [key] done to [playback] ([VideoKeys]): play, pause, skip, mute, start over, go to the end, a tenth or a speed; false for a key it does not take. */
internal fun playbackKey(key: VideoKey, playback: VideoPlayback): Boolean {
    val now = playback.state.value
    val skip = VideoRules.SEEK_SECONDS * 1000L
    when (key) {
        VideoKey.PLAY_PAUSE -> playback.playOrPause()
        VideoKey.BACK -> playback.seekTo(VideoRules.seekTarget(now.positionMs, -skip, now.durationMs))
        VideoKey.FORWARD -> playback.seekTo(VideoRules.seekTarget(now.positionMs, skip, now.durationMs))
        VideoKey.MUTE -> toggleMute(playback, now.muted)
        VideoKey.RESTART -> playback.seekTo(0)
        // Not while the length is unknown (still loading): every place would be the start.
        VideoKey.END -> if (now.durationMs > 0) playback.seekTo(now.durationMs)
        VideoKey.SLOWER -> playback.setSpeed(VideoRules.slower(now.speed))
        VideoKey.FASTER -> playback.setSpeed(VideoRules.faster(now.speed))
        else -> when {
            key.tenths < 0 -> return false
            now.durationMs > 0 -> playback.seekTo(VideoRules.jumpTarget(key.tenths, now.durationMs))
        }
    }
    return true
}

/** [event] done to the video full screen ([VideoKeys]); false when it is not the video's. */
private fun fullscreenKey(event: KeyEvent, playback: VideoPlayback, onClose: () -> Unit): Boolean {
    val typed = typedChar(event)
    val key = if (typed != null) {
        VideoKeys.of(escape = false, space = false, left = false, right = false, modified = event.isCtrlPressed || event.isAltPressed, typed = typed)
    } else VideoKeys.of(
        escape = event.key == Key.Escape,
        space = event.key == Key.Spacebar,
        left = event.key == Key.DirectionLeft,
        right = event.key == Key.DirectionRight,
        modified = event.isCtrlPressed || event.isAltPressed,
        back = event.key == Key.Back || (event.isAltPressed && event.key == Key.DirectionLeft),
        letter = PLAYER_LETTERS[shortcutKey(event)],
        home = event.key == Key.MoveHome,
        end = event.key == Key.MoveEnd,
    )
    if (key == VideoKey.LEAVE) return true.also { onClose() }
    return playbackKey(key, playback)
}

/** The letter keys the web's video players answer to, by where they sit ([VideoKeys]'s `letter`). */
internal val PLAYER_LETTERS = mapOf(Key.F to 'f', Key.K to 'k', Key.J to 'j', Key.L to 'l', Key.M to 'm')

/**
 * The character a typed key event carries (the desktop's, after its key went
 * down), so < > and the digits are found on any keyboard layout; null for
 * other events.
 */
internal fun typedChar(event: KeyEvent): Char? =
    if (event.type == KeyEventType.Unknown) event.utf16CodePoint.takeIf { it in 1..0xFFFF }?.toChar() else null
