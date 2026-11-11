package com.aimdi.xtanative.ui.common

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

internal actual fun ComponentRegistry.Builder.addPlatformPictureDecoders() {
    add(SmoothPictureDecoder.Factory())
}

/**
 * Decodes a picture as Coil's Skia decoder does, to the size its box asks
 * for, but shrinks it through Skia's mipmaps with linear filtering instead
 * of taking the nearest pixel. A picture it cannot draw that way is left to
 * Coil's own decoder.
 */
internal class SmoothPictureDecoder(private val bytes: ByteArray, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        return try {
            smooth(image)
        } catch (_: RuntimeException) {
            null
        } finally {
            image.close()
        }
    }

    private fun smooth(image: Image): DecodeResult? {
        run {
            val (width, height) = PictureScaling.decodedSize(
                image.width,
                image.height,
                pixels(options.size.width),
                pixels(options.size.height),
                fill = options.scale == Scale.FILL,
            )
            val bitmap = Bitmap()
            if (!bitmap.allocN32Pixels(width, height)) return null
            Canvas(bitmap).use { canvas ->
                canvas.drawImageRect(
                    image,
                    Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    Rect.makeWH(width.toFloat(), height.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                    null,
                    true,
                )
            }
            bitmap.setImmutable()
            return DecodeResult(bitmap.asImage(), isSampled = width < image.width || height < image.height)
        }
    }

    private fun pixels(dimension: Dimension): Int = (dimension as? Dimension.Pixels)?.px ?: 0

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            // Read here, so a picture Skia cannot open still reaches Coil's own decoder with its source untouched.
            val bytes = runCatching { result.source.source().peek().readByteArray() }.getOrNull() ?: return null
            if (bytes.isEmpty()) return null
            return SmoothPictureDecoder(bytes, options)
        }
    }
}
