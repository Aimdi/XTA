package com.aimdi.xtanative.ui.common

import androidx.compose.ui.input.key.Key

// Compose names no key for it on the desktop.
internal actual val ContextMenuKey: Key = Key(java.awt.event.KeyEvent.VK_CONTEXT_MENU)
