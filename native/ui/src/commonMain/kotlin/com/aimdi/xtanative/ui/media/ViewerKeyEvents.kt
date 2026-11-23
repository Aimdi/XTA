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
)
