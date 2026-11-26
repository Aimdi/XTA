package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class ViewerFilesTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun ctrlCCopiesAndCtrlSSavesAsInTheViewer() {
        assertEquals(ViewerKey.COPY, ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = true, copy = true))
        assertEquals(ViewerKey.SAVE_AS, ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = true, saveAs = true))
        assertEquals(ViewerKey.NONE, ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = true))
        assertEquals(ViewerKey.CLOSE, ViewerKeys.of(escape = true, left = false, right = false, back = false, modified = true, copy = true))
    }

    @Test
    fun theMenuKeyOpensTheFileMenuARightClickOpens() {
        fun key(menu: Boolean, modified: Boolean = false) = ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = modified, menu = menu)
        assertEquals(ViewerKey.MENU, key(menu = true))
        assertEquals(ViewerKey.NONE, key(menu = true, modified = true), "with Ctrl or Alt held it is no menu key")
        assertEquals(ViewerKey.NONE, key(menu = false))
    }

    @Test
    fun aPictureIsReadWhateverItsFormat() {
        assertEquals(3, ClipboardImages.decode(png(3, 2))?.width)
        // WebP goes through Skia, whose native library the window loads (the tests have none): no picture is no picture either way.
        assertNull(ClipboardImages.decode("<html>no picture</html>".toByteArray()))
    }

    /** A PNG whose header alone claims [width]×[height]: no pixel data follows. */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typed = type.toByteArray() + data
            val crc = CRC32().apply { update(typed) }.value
            return ByteBuffer.allocate(4).putInt(data.size).array() + typed + ByteBuffer.allocate(4).putInt(crc.toInt()).array()
        }
        val header = ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(2).put(0).put(0).put(0).array()
        return byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) + chunk("IHDR", header) + chunk("IEND", ByteArray(0))
    }

    @Test
    fun aPictureClaimingTooManyPixelsIsRefusedBeforeDecoding() {
        val bomb = pngHeader(30000, 30000)
        assertEquals(900_000_000L, ClipboardImages.javaPixels(bomb))
        assertNull(ClipboardImages.decode(bomb))
        assertEquals(6L, ClipboardImages.javaPixels(png(3, 2)))
        assertNull(ClipboardImages.javaPixels("<html/>".toByteArray()))
        val served = MediaHttp { _, _ -> MediaResponse(200, emptyMap(), null, ByteArrayInputStream(bomb)) }
        assertFalse(runBlocking { ClipboardImages.copy("https://pbs.twimg.com/media/x.png", served) })
    }

    @Test
    fun aFailedFetchCopiesNothing() {
        runBlocking { failedFetches() }
    }

    private suspend fun failedFetches() {
        val missing = MediaHttp { _, _ -> MediaResponse(404, emptyMap(), null, ByteArrayInputStream(ByteArray(0))) }
        assertFalse(ClipboardImages.copy("https://pbs.twimg.com/media/x.jpg", missing))
        val page = MediaHttp { _, _ -> MediaResponse(200, emptyMap(), null, ByteArrayInputStream("<html/>".toByteArray())) }
        assertFalse(ClipboardImages.copy("https://pbs.twimg.com/media/x.jpg", page))
        assertNotNull(ClipboardImages.decode(png(1, 1)))
    }
}
