package com.aimdi.xtanative.ui.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("", text))
