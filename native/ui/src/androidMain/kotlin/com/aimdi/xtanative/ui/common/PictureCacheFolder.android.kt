package com.aimdi.xtanative.ui.common

import coil3.disk.DiskCache

/** Coil's own: Android's temporary folder is the app's private cache folder already. */
internal actual fun platformPictureDiskCache(): (() -> DiskCache)? = null
