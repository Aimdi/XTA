package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivFrameBytes
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The GIF an ugoira is saved as, read back by the JDK's own GIF reader. */
class PixivGifTest {
    @Test
    fun cancellingDuringTheEncodeStopsItAndReportsEachFrameInOrder() = runBlocking {
        val frames = listOf(
            PixivFrameBytes(byteArrayOf(1), 100.milliseconds),
            PixivFrameBytes(byteArrayOf(2), 100.milliseconds),
            PixivFrameBytes(byteArrayOf(3), 100.milliseconds),
        )
        val decoded = mutableListOf<Int>()
        val reported = mutableListOf<Int>()
        val job = launch {
            PixivGifWriter.encode(
                frames,
                decode = { bytes -> decoded += bytes[0].toInt(); frame(1, 1) { _, _ -> 0xffffff } },
                onFrame = { done ->
                    reported += done
                    if (done == 2) throw CancellationException("stop")
                },
            )
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf(1, 2), decoded, "the frame being decoded is finished, and no later one starts")
        assertEquals(listOf(1, 2), reported)
    }

    private fun frame(width: Int, height: Int, color: (x: Int, y: Int) -> Int) =
        PixivPixels(width, height, IntArray(width * height) { at -> (0xff shl 24) or (color(at % width, at / width) and 0xffffff) })

    private fun gifOf(vararg frames: Pair<PixivPixels, Int>): ByteArray =
        PixivGifWriter().apply { frames.forEach { (pixels, delay) -> add(pixels, delay) } }.finish() ?: error("no GIF")

    private class Read(val images: List<BufferedImage>, val delays: List<Int>, val loops: Boolean)

    private fun read(gif: ByteArray): Read {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(gif))
        val count = reader.getNumImages(true)
        val trees = (0 until count).map { reader.getImageMetadata(it).getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode }
        val delays = trees.map { (it.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode).getAttribute("delayTime").toInt() }
        val application = trees.first().getElementsByTagName("ApplicationExtension").item(0) as IIOMetadataNode?
        val loops = application?.getAttribute("applicationID") == "NETSCAPE" && (application.userObject as ByteArray).contentEquals(byteArrayOf(1, 0, 0))
        return Read((0 until count).map(reader::read), delays, loops)
    }

    private fun assertSamePixels(expected: PixivPixels, actual: BufferedImage) {
        assertEquals(expected.width to expected.height, actual.width to actual.height)
        val read = IntArray(expected.width * expected.height) { at -> actual.getRGB(at % expected.width, at / expected.width) and 0xffffff }
        assertContentEquals(expected.argb.map { it and 0xffffff }.toIntArray(), read)
    }

    @Test
    fun aFrameOfFewColoursComesBackExactly() {
        val pixels = frame(64, 48) { x, y -> ((x * 7 + y * 13) % 200) * 0x010203 }
        val read = read(gifOf(pixels to 5))
        assertSamePixels(pixels, read.images.single())
        assertEquals(listOf(5), read.delays)
        assertTrue(read.loops, "it loops for ever, as XTA's does")
    }

    @Test
    fun aBusyFrameRunsPastTheCodeTableAndStillReadsBack() {
        var seed = 12345
        val pixels = frame(300, 300) { _, _ ->
            seed = seed * 1103515245 + 12345
            (seed ushr 16 and 0xff) * 0x010101
        }
        assertSamePixels(pixels, read(gifOf(pixels to 3)).images.single())
    }

    @Test
    fun framesKeepTheirOrderAndDelays() {
        val red = frame(10, 6) { _, _ -> 0xff0000 }
        val blue = frame(10, 6) { _, _ -> 0x0000ff }
        val read = read(gifOf(red to 12, blue to 40))
        assertEquals(listOf(12, 40), read.delays)
        assertSamePixels(red, read.images[0])
        assertSamePixels(blue, read.images[1])
    }

    @Test
    fun aFrameOfManyColoursIsDitheredToAPaletteOfItsOwn() {
        val pixels = frame(128, 128) { x, y -> (x * 2 shl 16) or (y * 2 shl 8) or ((x + y) and 0xff) }
        val image = read(gifOf(pixels to 10)).images.single()
        val misses = pixels.argb.indices.map { at ->
            val shown = image.getRGB(at % 128, at / 128)
            (0..2).sumOf { channel -> abs((pixels.argb[at] shr (channel * 8) and 0xff) - (shown shr (channel * 8) and 0xff)) }
        }
        assertTrue(misses.average() < 18, "on average ${misses.average()} off over three channels")
    }

    @Test
    fun delaysAreHundredthsOfASecondAsXtaRoundsThem() {
        assertEquals(10, PixivGifWriter.centiseconds(100))
        assertEquals(4, PixivGifWriter.centiseconds(35))
        assertEquals(2, PixivGifWriter.centiseconds(16), "most viewers speed up anything shorter")
        assertEquals(65535, PixivGifWriter.centiseconds(10_000_000))
    }

    @Test
    fun anAnimationIsEncodedAFrameAtATime(): Unit = runBlocking {
        val colors = listOf(0xff0000, 0x00ff00, 0x0000ff)
        val frames = colors.mapIndexed { index, _ -> PixivFrameBytes(byteArrayOf(index.toByte()), (index + 1).times(100).milliseconds) }
        val done = mutableListOf<Int>()
        val gif = PixivGifWriter.encode(frames, decode = { bytes -> frame(4, 4) { _, _ -> colors[bytes[0].toInt()] } }) { done += it }
        val read = read(gif)
        assertEquals(listOf(1, 2, 3), done)
        assertEquals(listOf(10, 20, 30), read.delays)
        assertEquals(colors, read.images.map { it.getRGB(0, 0) and 0xffffff })
    }

    @Test
    fun aFrameThatWillNotDecodeFailsTheExport(): Unit = runBlocking {
        val frames = listOf(PixivFrameBytes(byteArrayOf(1), 100.milliseconds))
        assertFailsWith<IOException> { PixivGifWriter.encode(frames, decode = { null }) }
        assertFailsWith<IOException> { PixivGifWriter.encode(emptyList(), decode = { null }) }
        assertNull(PixivGifWriter().finish())
    }
}
