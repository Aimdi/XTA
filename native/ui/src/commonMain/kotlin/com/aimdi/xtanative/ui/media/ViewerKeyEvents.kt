package com.aimdi.xtanative.ui.media

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/** [event] as the media viewer reads it ([ViewerKeys]). */
internal fun viewerKeyOf(event: KeyEvent): ViewerKey = ViewerKeys.of(
    escape = event.key == Key.Escape,
    left = event.key == Key.DirectionLeft,
    right = event.key == Key.DirectionRight,
    back = event.key == Key.Back || (event.isAltPressed && event.key == Key.DirectionLeft),
    modified = event.isAltPressed || event.isCtrlPressed,
    copy = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.C,
    saveAs = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.S,
    closeKey = event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && event.key == Key.W,
    zoom = if (event.isAltPressed) ViewerKey.NONE else zoomKeyOf(event.key),
)

/** + (or =), - and 0, on the main keys or the number pad: the photo's zoom. */
private fun zoomKeyOf(key: Key): ViewerKey = when (key) {
    Key.Plus, Key.Equals, Key.NumPadAdd -> ViewerKey.ZOOM_IN
    Key.Minus, Key.NumPadSubtract -> ViewerKey.ZOOM_OUT
    Key.Zero, Key.NumPad0 -> ViewerKey.ZOOM_RESET
    else -> ViewerKey.NONE
}
