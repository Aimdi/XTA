package com.aimdi.xtanative.ui.media

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** [event] as the media viewer reads it ([ViewerKeys]), the photo shown [zoomed] in or not. */
internal fun viewerKeyOf(event: KeyEvent, zoomed: Boolean = false): ViewerKey = ViewerKeys.of(
    escape = event.key == Key.Escape,
    left = event.key == Key.DirectionLeft,
    right = event.key == Key.DirectionRight,
    back = event.key == Key.Back || (event.isAltPressed && event.key == Key.DirectionLeft),
    modified = event.isAltPressed || event.isCtrlPressed,
    copy = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.C,
    saveAs = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.S,
    closeKey = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.W,
    zoom = if (event.isAltPressed) ViewerKey.NONE else zoomKeyOf(event.key),
    up = event.key == Key.DirectionUp,
    down = event.key == Key.DirectionDown,
    home = event.key == Key.MoveHome,
    end = event.key == Key.MoveEnd,
    zoomed = zoomed,
)

/** + (or =), - and 0, on the main keys or the number pad: the photo's zoom. */
private fun zoomKeyOf(key: Key): ViewerKey = when (key) {
    Key.Plus, Key.Equals, Key.NumPadAdd -> ViewerKey.ZOOM_IN
    Key.Minus, Key.NumPadSubtract -> ViewerKey.ZOOM_OUT
    Key.Zero, Key.NumPad0 -> ViewerKey.ZOOM_RESET
    else -> ViewerKey.NONE
}

/** What a key does on the media viewer's page shown: the video's ([VideoKeys.onViewerPage]), the viewer's ([ViewerKeys]), or nothing. */
internal sealed interface PageKey {
    data class Video(val key: VideoKey) : PageKey

    data class Viewer(val key: ViewerKey) : PageKey

    data object None : PageKey
}

/**
 * [event] on the media viewer's page shown, as the app's viewer and the
 * plugins' read it: where a video is shown ([onVideo]) its keys come first
 * (Space, k, j, l, m, f, and < > and 0 to 9 as typed, on any layout), then
 * the viewer's own, the photo shown [zoomed] in or not.
 */
internal fun pageKeyOf(event: KeyEvent, onVideo: Boolean, zoomed: Boolean): PageKey {
    val modified = event.isCtrlPressed || event.isAltPressed
    val typed = typedChar(event)
    if (typed != null) {
        val key = if (onVideo) VideoKeys.onViewerPage(space = false, letter = null, modified = modified, typed = typed) else VideoKey.NONE
        return if (key == VideoKey.NONE) PageKey.None else PageKey.Video(key)
    }
    if (event.type != KeyEventType.KeyDown) return PageKey.None
    if (onVideo) {
        val key = VideoKeys.onViewerPage(event.key == Key.Spacebar, PLAYER_LETTERS[event.key], modified)
        if (key != VideoKey.NONE) return PageKey.Video(key)
    }
    val key = viewerKeyOf(event, zoomed)
    return if (key == ViewerKey.NONE) PageKey.None else PageKey.Viewer(key)
}
