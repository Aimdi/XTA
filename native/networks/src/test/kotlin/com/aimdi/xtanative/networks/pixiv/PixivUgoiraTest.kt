package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

class PixivUgoiraTest {
    /** A ZIP as Pixiv serves ugoira frames, stored or deflated. */
    private fun zip(files: Map<String, ByteArray>, deflate: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            files.forEach { (name, content) ->
                val entry = ZipEntry(name)
                if (!deflate) {
                    entry.method = ZipEntry.STORED
                    entry.size = content.size.toLong()
                    entry.compressedSize = content.size.toLong()
                    entry.crc = CRC32().apply { update(content) }.value
                }
                out.putNextEntry(entry)
                out.write(content)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private val ugoira = PixivUgoira(
        "https://i.pximg.net/img-zip-ugoira/img/2026/07/01/00/00/00/120_ugoira600x600.zip",
        listOf(PixivUgoiraFrame("0.png", 80.milliseconds), PixivUgoiraFrame("1.png", 120.milliseconds)),
    )

    @Test
    fun theMetadataListsTheArchiveAndEachFramesDelay() {
        val parsed = PixivUgoira.parse(Fixtures.json("ugoira_metadata.json"))!!
        assertTrue(parsed.zipUrl.endsWith("9_ugoira600x600.zip"))
        assertEquals(
            listOf("000000.jpg" to 80L, "000001.jpg" to 16L, "000002.jpg" to 10_000L),
            parsed.frames.map { it.file to it.delay.inWholeMilliseconds },
            "a delay stays between 16 ms and 10 s, and a frame without a file is skipped",
        )
        assertNull(PixivUgoira.parse(J.parse("""{"ugoira_metadata": {}}""")))
        assertNull(PixivUgoira.parse(J.parse("""{"ugoira_metadata": {"zip_urls": {"medium": "https://i.pximg.net/z.zip"}, "frames": []}}""")))
    }

    @Test
    fun framesComeOutOfAStoredOrDeflatedArchiveIntact() {
        val files = mapOf("000000.jpg" to ByteArray(300) { (it % 7).toByte() }, "000001.jpg" to ByteArray(90) { (255 - it).toByte() })
        for (deflate in listOf(false, true)) {
            val read = PixivZip.read(zip(files, deflate))!!
            assertEquals(files.keys, read.keys, "deflate $deflate")
            files.forEach { (name, content) -> assertContentEquals(content, read[name], "$name, deflate $deflate") }
        }
    }

    @Test
    fun aDamagedArchiveReadsAsNone() {
        assertNull(PixivZip.read(byteArrayOf(1, 2, 3, 4)))
        assertNull(PixivZip.read(ByteArray(0)))
        assertTrue(ugoira.framesIn(byteArrayOf(80, 75, 5, 6)).isEmpty())
    }

    @Test
    fun framesComeInTheMetadatasOrderWithTheirDelays() {
        val archive = zip(mapOf("1.png" to byteArrayOf(2), "0.png" to byteArrayOf(1)), deflate = false)
        val frames = ugoira.framesIn(archive)
        assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2)), frames.map { it.bytes.toList() })
        assertEquals(listOf(80L, 120L), frames.map { it.delay.inWholeMilliseconds })
        assertTrue(ugoira.framesIn(zip(mapOf("other.png" to byteArrayOf(1)), deflate = true)).isEmpty())
    }

    @Test
    fun theArchiveComesFromTheServerTheReaderPicked() {
        assertEquals("https://i.pixiv.re/img-zip-ugoira/img/2026/07/01/00/00/00/120_ugoira600x600.zip", ugoira.archiveUrl(PixivImages.MIRROR_HOST))
        assertEquals(ugoira.zipUrl, ugoira.archiveUrl())
    }

    @Test
    fun theMetadataIsAskedByWorkId() = runBlocking {
        val http = FakePixivHttp { answer(Fixtures.text("ugoira_metadata.json")) }
        val read = pixiv(http).ugoiraMetadata(9)
        assertEquals("/v1/ugoira/metadata" to mapOf("illust_id" to "9"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(3, read.frames.size)
    }
}
