package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivFrameBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

/** The APNG an ugoira is saved as, taken apart chunk by chunk and each frame read back by the JDK's PNG reader. */
class PixivApngTest {
    private class Chunk(val type: String, val data: ByteArray)

    private fun chunks(png: ByteArray): List<Chunk> {
        val input = DataInputStream(ByteArrayInputStream(png, 8, png.size - 8))
        return buildList {
            while (input.available() > 0) {
                val data = ByteArray(input.readInt())
                val type = String(ByteArray(4).also(input::readFully), Charsets.US_ASCII)
                input.readFully(data)
                input.readInt()
                add(Chunk(type, data))
            }
        }
    }

    private fun ints(data: ByteArray, count: Int): List<Int> = DataInputStream(ByteArrayInputStream(data)).let { input -> List(count) { input.readInt() } }

    /** One frame's area as a PNG of its own, as a viewer composes it. */
    private fun framePng(width: Int, height: Int, data: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        out.write(PixivPng.SIGNATURE)
        PixivPng.chunk(out, "IHDR", PixivPng.header(width, height))
        PixivPng.chunk(out, "IDAT", data)
        PixivPng.chunk(out, "IEND", ByteArray(0))
    }.toByteArray()

    private val red = 0xffff0000.toInt()
    private val blue = 0xff0000ff.toInt()

    private fun frame(vararg changed: Pair<Int, Int>) = PixivPixels(4, 3, IntArray(12) { red }.also { pixels -> changed.forEach { (at, color) -> pixels[at] = color } })

    @Test
    fun framesAreWrittenWithTheirDelaysAndOnlyTheAreaThatChanged() {
        val frames = listOf(frame(), frame(5 to blue, 6 to blue), frame(5 to blue, 6 to blue))
        val writer = PixivApngWriter(frames.size)
        frames.forEachIndexed { index, pixels -> writer.add(pixels, listOf(80L, 120L, 10_000L)[index]) }
        val apng = writer.finish()!!
        val parts = chunks(apng)
        assertEquals(listOf("IHDR", "acTL", "fcTL", "IDAT", "fcTL", "fdAT", "fcTL", "fdAT", "IEND"), parts.map { it.type })
        assertEquals(listOf(3, 0), ints(parts[1].data, 2), "three frames, looping for ever")
        val controls = parts.filter { it.type == "fcTL" }.map { DataInputStream(ByteArrayInputStream(it.data)).run { List(5) { readInt() } + listOf(readShort().toInt(), readShort().toInt()) } }
        assertEquals(listOf(0, 4, 3, 0, 0, 80, 1000), controls[0], "sequence, the whole picture, 80 ms")
        assertEquals(listOf(1, 2, 1, 1, 1, 120, 1000), controls[1], "only the two pixels that changed")
        assertEquals(listOf(3, 1, 1, 0, 0, 10_000, 1000), controls[2], "nothing changed: one pixel")
        assertEquals(listOf(2, 4), parts.filter { it.type == "fdAT" }.map { ints(it.data, 1).single() }, "sequence numbers run through fcTL and fdAT")
        val first = ImageIO.read(ByteArrayInputStream(apng))
        assertEquals(red, first.getRGB(3, 2), "an APNG is a PNG of its first frame to any other reader")
        val second = ImageIO.read(ByteArrayInputStream(framePng(2, 1, parts[5].data.copyOfRange(4, parts[5].data.size))))
        assertEquals(listOf(blue, blue), listOf(second.getRGB(0, 0), second.getRGB(1, 0)))
    }

    @Test
    fun theChangedAreaBoundsEveryPixelThatDiffers() {
        assertEquals(PixivApngWriter.Area(1, 0, 3, 3), PixivApngWriter.changedArea(frame(), frame(1 to blue, 11 to blue)))
        assertEquals(PixivApngWriter.Area(0, 0, 1, 1), PixivApngWriter.changedArea(frame(), frame()))
    }

    @Test
    fun anUnreadableFrameOrNoneFailsAndFramesOfOtherSizesAreRefused() = runBlocking {
        val bytes = listOf(PixivFrameBytes(byteArrayOf(1), 50.milliseconds), PixivFrameBytes(byteArrayOf(2), 50.milliseconds))
        assertFailsWith<IOException> { PixivApngWriter.encode(bytes, decode = { if (it[0].toInt() == 1) frame() else null }) }
        assertFailsWith<IOException> { PixivApngWriter.encode(emptyList(), decode = { frame() }) }
        val writer = PixivApngWriter(2)
        writer.add(frame(), 50)
        assertFailsWith<IOException> { writer.add(PixivPixels(2, 2, IntArray(4)), 50) }
        var done = 0
        val apng = PixivApngWriter.encode(bytes, decode = { frame() }) { done = it }
        assertEquals(2, done)
        assertContentEquals(PixivPng.SIGNATURE, apng.copyOfRange(0, 8))
    }
}
