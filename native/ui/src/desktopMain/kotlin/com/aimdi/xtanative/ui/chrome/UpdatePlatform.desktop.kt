package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable
import java.io.File

/** The Arch package's app runs on the Java runtime it put under /opt, and pacman lists it installed; any other is the tarball's. */
@Composable
internal actual fun updateFile(): UpdateFile = NativeUpdates.desktopFile(
    System.getProperty("java.home").orEmpty(),
    runCatching { File(NativeUpdates.PACMAN_LOCAL).list()?.toList() }.getOrNull().orEmpty(),
)
