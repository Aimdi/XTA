package com.aimdi.xtanative.plugins.threads

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.aimdi.xtanative.networks.threads.ThreadsPost
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Threads post as its card draws it, for its sheet's "Share post as
 * image", as the app shares an X post (`ui.post`'s PostPicture): asked for
 * one, the card draws itself into a layer as well ([recordedIn]) for a
 * frame, and what it drew becomes a PNG on the screen's background
 * ([ThreadsPng]). Nothing leaves the device but what the reader then shares.
 */
@Stable
internal class ThreadsPostPicture(internal val layer: GraphicsLayer, private val background: Color) {
    internal var recording by mutableStateOf(false)
        private set

    /** The card as a PNG; null when it is not drawn, or too large to take. */
    suspend fun png(): ByteArray? {
        recording = true
        return try {
            // One frame draws the card into the layer; by the next it is there.
            withFrameNanos { }
            withFrameNanos { }
            val width = layer.size.width
            val height = layer.size.height
            if (!ThreadsPng.fits(width, height)) return null
            val image = layer.toImageBitmap()
            withContext(Dispatchers.Default) {
                val pixels = IntArray(width * height)
                image.readPixels(pixels, width = width, height = height)
                ThreadsPng.encode(width, height, pixels, background.toArgb())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            recording = false
        }
    }
}

/** A card's picture, for the card drawn where [recordedIn] records it. */
@Composable
internal fun rememberThreadsPostPicture(): ThreadsPostPicture {
    val layer = rememberGraphicsLayer()
    val background = MaterialTheme.colorScheme.background
    return remember(layer, background) { ThreadsPostPicture(layer, background) }
}

/** What it modifies, drawn into [picture]'s layer as well while a picture is asked for. */
internal fun Modifier.recordedIn(picture: ThreadsPostPicture): Modifier =
    if (!picture.recording) {
        this
    } else {
        drawWithContent {
            picture.layer.record { this@drawWithContent.drawContent() }
            drawLayer(picture.layer)
        }
    }

/**
 * "Share post as image" for [post] drawn as [picture]: taken, then handed
 * to the system's share sheet (the desktop, which has none, saves it); the
 * reader is told when it could not be taken. It runs with the card, beyond
 * the sheet that asked for it.
 */
@Composable
internal fun rememberPictureShare(app: ThreadsApp, post: ThreadsPost, picture: ThreadsPostPicture): () -> Unit {
    val coroutines = rememberCoroutineScope()
    return remember(app, coroutines, post, picture) {
        {
            coroutines.launch {
                val png = picture.png()
                if (png == null) app.host.tell(Strings.share_image_failed.get(app.host.language)) else app.host.shareFile(png, ThreadsPng.name(post), ThreadsPng.MIME)
            }
            Unit
        }
    }
}

/**
 * A picture as a PNG file, written here so that the plugin needs nothing of
 * a platform for it: true colour without transparency, each pixel laid over
 * the screen's background first, as the app's own post pictures are.
 */
internal object ThreadsPng {
    const val MIME = "image/png"

    /** The most pixels a picture is taken of: a card longer than that (a very long attached text on a large screen) is not shared as one. */
    const val MAX_PIXELS = 24_000_000L

    /** Whether a picture [width] by [height] can be taken at all. */
    fun fits(width: Int, height: Int): Boolean = width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS

    /** What [post]'s picture is saved or shared as: "threads-<author>-<post's code>.png", beside its media's files ([ThreadsMediaFiles]). */
    fun name(post: ThreadsPost): String = ThreadsMediaFiles.stem(post) + ".png"

    /**
     * [argb], [width] by [height] pixels row by row, each a colour with its
     * transparency not multiplied in, as a PNG file over [background]; null
     * when the pixels are not that many.
     */
    fun encode(width: Int, height: Int, argb: IntArray, background: Int): ByteArray? {
        if (!fits(width, height) || argb.size != width * height) return null
        val out = ByteArrayOutputStream(argb.size / 2 + 128)
        val file = DataOutputStream(out)
        file.write(SIGNATURE)
        val header = ByteArrayOutputStream(13)
        DataOutputStream(header).apply {
            writeInt(width)
            writeInt(height)
            writeByte(8) // bits a channel
            writeByte(2) // true colour
            writeByte(0)
            writeByte(0)
            writeByte(0)
        }
        chunk(file, "IHDR", header.toByteArray())
        chunk(file, "IDAT", scanlines(width, height, argb, background))
        chunk(file, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** [pixel] over [background], both ARGB: the colour seen, without transparency (0xRRGGBB). */
    fun over(pixel: Int, background: Int): Int {
        val alpha = pixel ushr 24
        if (alpha == 255) return pixel and 0xFFFFFF
        if (alpha == 0) return background and 0xFFFFFF
        fun mix(shift: Int): Int {
            val top = (pixel ushr shift) and 0xFF
            val under = (background ushr shift) and 0xFF
            return (top * alpha + under * (255 - alpha) + 127) / 255
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** The rows compressed, each led by the byte that says it is written as it is. */
    private fun scanlines(width: Int, height: Int, argb: IntArray, background: Int): ByteArray {
        val packed = ByteArrayOutputStream(width * height / 4 + 64)
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            DeflaterOutputStream(packed, deflater, 1 shl 15).use { rows ->
                val row = ByteArray(1 + width * 3)
                for (y in 0 until height) {
                    var at = 1
                    val start = y * width
                    for (x in 0 until width) {
                        val seen = over(argb[start + x], background)
                        row[at++] = (seen ushr 16).toByte()
                        row[at++] = (seen ushr 8).toByte()
                        row[at++] = seen.toByte()
                    }
                    rows.write(row)
                }
            }
        } finally {
            deflater.end()
        }
        return packed.toByteArray()
    }

    private fun chunk(file: DataOutputStream, type: String, data: ByteArray) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val sum = CRC32().apply {
            update(name)
            update(data)
        }
        file.writeInt(data.size)
        file.write(name)
        file.write(data)
        file.writeInt(sum.value.toInt())
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
