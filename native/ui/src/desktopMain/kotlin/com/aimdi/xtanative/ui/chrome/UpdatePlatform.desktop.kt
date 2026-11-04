package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable

/** The Arch package's app runs on the Java runtime it put under /opt; any other is the tarball's. */
@Composable
internal actual fun updateFile(): UpdateFile = NativeUpdates.desktopFile(System.getProperty("java.home").orEmpty())
