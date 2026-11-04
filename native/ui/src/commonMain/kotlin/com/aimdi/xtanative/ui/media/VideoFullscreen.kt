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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.aimdi.xtanative.ui.common.BackHandling

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
 * (video_fullscreen.dart): turned to the video's orientation and without the
 * system bars on Android, filling the screen on the desktop, with
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
    FullscreenWindow(video.aspectRatio)
    BackHandling(enabled = true, onBack = onClose)
    PausesWhenLeft(player)
    var fill by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nothing under it is touched while it is up, picture-in-picture included.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .onKeyEvent { event -> (event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) onClose() } }
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
 * While composed, the window shows a video of [aspectRatio] full screen:
 * Android turns to the video's orientation and hides the system bars (until
 * a swipe shows them for a moment); the desktop window fills the screen.
 */
@Composable
internal expect fun FullscreenWindow(aspectRatio: Float)

/** Picture-in-picture, or null where there is none (the desktop, a phone without it); [onDismissed] hears its window closed. */
@Composable
internal expect fun rememberPictureInPicture(onDismissed: () -> Unit): PictureInPicture?
