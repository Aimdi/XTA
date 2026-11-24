package com.aimdi.xtanative.ui.common

import coil3.disk.DiskCache

/**
 * Where a platform keeps the app's picture cache, made when the first
 * picture is read; null keeps Coil's own. Coil's own is its process-wide
 * cache in the system's temporary folder: on Android that is the app's
 * private cache folder, but on the desktop it is the shared `/tmp`, which
 * other users of the machine may read and which is lost at every restart.
 */
internal expect fun platformPictureDiskCache(): (() -> DiskCache)?
