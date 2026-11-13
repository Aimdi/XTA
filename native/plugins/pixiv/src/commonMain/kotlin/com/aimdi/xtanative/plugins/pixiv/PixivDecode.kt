package com.aimdi.xtanative.plugins.pixiv

/**
 * [bytes] decoded to their pixels, the platform's way: Android's
 * BitmapFactory, which may shrink a picture wider than [atLeastWidth] by a
 * power of two on the way in (never below it); the JDK's decoders on the
 * desktop, then Skia's for what they do not read. Null for what will not decode.
 */
internal expect fun decodePixels(bytes: ByteArray, atLeastWidth: Int?): PixivPixels?
