package com.aimdi.xtanative.ui.post

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream

/**
 * [image] drawn over [background] into a bitmap of its own and written as a
 * PNG: a layer's picture may be a hardware bitmap, which is copied first, as
 * a software canvas cannot draw one.
 */
internal actual fun pngOf(image: ImageBitmap, background: Color): ByteArray? {
    val drawn = image.asAndroidBitmap()
    val source = if (drawn.config == Bitmap.Config.HARDWARE) drawn.copy(Bitmap.Config.ARGB_8888, false) ?: return null else drawn
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(out)
        canvas.drawColor(background.toArgb())
        canvas.drawBitmap(source, 0f, 0f, null)
        return ByteArrayOutputStream().use { stream -> if (out.compress(Bitmap.CompressFormat.PNG, 100, stream)) stream.toByteArray() else null }
    } finally {
        out.recycle()
        if (source !== drawn) source.recycle()
    }
}
