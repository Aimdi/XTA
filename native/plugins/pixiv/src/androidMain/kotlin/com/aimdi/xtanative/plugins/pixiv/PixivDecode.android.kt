package com.aimdi.xtanative.plugins.pixiv

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** BitmapFactory, sampled down first when a picture is far wider than asked, so a phone's photo never sits in memory whole. */
internal actual fun decodePixels(bytes: ByteArray, atLeastWidth: Int?): PixivPixels? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = PixivPixels.sampleSize(bounds.outWidth, atLeastWidth)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::pixelsOf)
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

private fun pixelsOf(bitmap: Bitmap): PixivPixels = try {
    val argb = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    PixivPixels(bitmap.width, bitmap.height, argb)
} finally {
    bitmap.recycle()
}
