package com.aimdi.xtanative.ui.common

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

/**
 * The key [event] is for a shortcut (Ctrl+1, j, Ctrl+W...): on the desktop
 * the same physical key on any keyboard layout, where the layout's own gives
 * no Latin letter or digit there (the digit row of a French keyboard, the
 * letters of a Russian one); the event's key elsewhere. Text typed into a
 * field is not changed.
 */
internal expect fun shortcutKey(event: KeyEvent): Key
