package com.aimdi.xtanative.ui.settings

import coil3.ImageLoader

/** [loader]'s picture cache trimmed of what [ImageCacheAge] keeps no longer, once, in the background of the launch. */
fun trimImageCache(loader: ImageLoader) {
    val trim = Thread({ runCatching { loader.diskCache?.let { ImageCacheAge.trim(it.directory.toFile()) } } }, "image-cache-trim")
    trim.isDaemon = true
    trim.start()
}
