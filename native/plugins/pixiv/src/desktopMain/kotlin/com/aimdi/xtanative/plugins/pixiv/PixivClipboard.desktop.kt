package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

internal actual fun ClipEntry.plainText(): String? = runCatching {
    (nativeClipEntry as? Transferable)?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
        ?.getTransferData(DataFlavor.stringFlavor) as? String
}.getOrNull()
