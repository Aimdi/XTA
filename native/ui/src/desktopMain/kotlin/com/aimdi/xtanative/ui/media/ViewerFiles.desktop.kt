package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.downloads.downloadRequestHeaders
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat

@Composable
internal actual fun rememberViewerFileActions(): ViewerFileActions? {
    val saveAs = LocalMediaSaveAs.current
    return remember(saveAs) {
        object : ViewerFileActions {
            override suspend fun copyImage(url: String): Boolean = ClipboardImages.copy(url)

            override fun saveAs(save: MediaSave) {
                saveAs?.save(save)
            }
        }
    }
}

/**
 * A picture put on the desktop's clipboard as the picture itself, as a
 * browser's "Copy image" does: fetched (read only, as a download is),
 * decoded, and offered as an image other apps paste.
 */
internal object ClipboardImages {
    /** The largest picture copied; X's originals are a few megabytes. */
    private const val MAX_BYTES = 40L * 1024 * 1024

    private val http: MediaHttp by lazy { OkHttpMediaHttp() }

    suspend fun copy(url: String, fetch: MediaHttp = http): Boolean {
        val picture = try {
            withContext(Dispatchers.IO) { fetched(fetch, url)?.let(::decode) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return false
        return withContext(Dispatchers.Main) {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransfer(picture), null) }.isSuccess
        }
    }

    private suspend fun fetched(http: MediaHttp, url: String): ByteArray? = http.get(url, downloadRequestHeaders(url)).use { response ->
        if (response.code !in 200..299 || (response.contentLength ?: 0) > MAX_BYTES) return null
        response.body.readNBytes(MAX_BYTES.toInt() + 1).takeIf { it.size <= MAX_BYTES }
    }

    /** The most pixels a copied picture may have: about 200 MB once decoded, far above any real original. */
    const val MAX_PIXELS = 50L * 1000 * 1000

    /**
     * [bytes] as a picture: what Java reads itself (JPEG, PNG, GIF), else
     * through Skia (WebP, AVIF where it has them); null for no picture, or
     * for one whose header claims more than [MAX_PIXELS] (a decompression
     * bomb would fill the heap). The size is read before anything is decoded.
     */
    fun decode(bytes: ByteArray): BufferedImage? {
        val pixels = javaPixels(bytes)
        if (pixels != null && pixels > MAX_PIXELS) return null
        return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: runCatching {
            val png = org.jetbrains.skia.Image.makeFromEncoded(bytes).use { image ->
                if (image.width.toLong() * image.height > MAX_PIXELS) return null
                image.encodeToData(EncodedImageFormat.PNG)?.bytes
            } ?: return null
            ImageIO.read(ByteArrayInputStream(png))
        }.getOrNull()
    }

    /** Width times height as the header of [bytes] says, for a format Java reads; null when it reads none. */
    fun javaPixels(bytes: ByteArray): Long? = runCatching {
        (ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.setInput(input, true, true)
                reader.getWidth(0).toLong() * reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    /** A picture as the clipboard hands it out: Java's image flavour, which it offers to other apps as PNG and the like. */
    private class ImageTransfer(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}
