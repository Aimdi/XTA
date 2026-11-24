package com.aimdi.xtanative.ui.common

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/** Android's keyboards name their keys by place already. */
internal actual fun shortcutKey(event: KeyEvent): Key = event.key
