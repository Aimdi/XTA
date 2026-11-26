package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
