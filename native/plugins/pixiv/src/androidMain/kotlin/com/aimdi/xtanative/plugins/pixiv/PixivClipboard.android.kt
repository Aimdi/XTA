package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.platform.ClipEntry

internal actual fun ClipEntry.plainText(): String? =
    clipData.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
