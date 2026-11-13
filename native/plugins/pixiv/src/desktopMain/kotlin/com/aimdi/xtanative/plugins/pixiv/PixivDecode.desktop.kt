package com.aimdi.xtanative.plugins.pixiv

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** The JDK's decoders (JPEG, PNG, GIF, BMP), then Skia's, which Compose draws with, for the rest (WebP). The whole picture: the desktop has room. */
internal actual fun decodePixels(bytes: ByteArray, atLeastWidth: Int?): PixivPixels? = byImageIo(bytes) ?: bySkia(bytes)

private fun byImageIo(bytes: ByteArray): PixivPixels? = try {
    ImageIO.read(ByteArrayInputStream(bytes))?.let { image ->
        PixivPixels(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }
} catch (e: Exception) {
    null
}

private fun bySkia(bytes: ByteArray): PixivPixels? = try {
    Image.makeFromEncoded(bytes).use { image ->
        val info = ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        Bitmap().use { bitmap ->
            if (!bitmap.allocPixels(info) || !image.readPixels(bitmap, 0, 0)) {
                null
            } else {
                bitmap.readPixels(info, image.width * 4, 0, 0)?.let { PixivPixels.ofBgra(image.width, image.height, it) }
            }
        }
    }
} catch (e: Exception) {
    null
} catch (e: LinkageError) {
    null
}
