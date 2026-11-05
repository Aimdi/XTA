package com.aimdi.xtanative.ui.common

import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry(StringSelection(text))
