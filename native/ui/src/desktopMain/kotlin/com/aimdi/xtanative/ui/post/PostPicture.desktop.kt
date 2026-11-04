package com.aimdi.xtanative.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/** Skia draws [image] over [background] and writes it as a PNG. */
internal actual fun pngOf(image: ImageBitmap, background: Color): ByteArray? {
    val surface = Surface.makeRasterN32Premul(image.width, image.height)
    val drawn = Image.makeFromBitmap(image.asSkiaBitmap())
    return try {
        surface.canvas.clear(background.toArgb())
        surface.canvas.drawImage(drawn, 0f, 0f)
        val snapshot = surface.makeImageSnapshot()
        try {
            snapshot.encodeToData(EncodedImageFormat.PNG)?.bytes
        } finally {
            snapshot.close()
        }
    } finally {
        drawn.close()
        surface.close()
    }
}
