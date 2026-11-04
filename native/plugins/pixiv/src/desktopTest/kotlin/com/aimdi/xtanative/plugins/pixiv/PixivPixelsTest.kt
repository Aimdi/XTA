package com.aimdi.xtanative.plugins.pixiv

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PixivPixelsTest {
    private fun opaque(vararg rgb: Int) = rgb.map { (0xff shl 24) or it }.toIntArray()

    @Test
    fun aPngReadsBackAsItsPixels() {
        var seed = 7
        val pixels = PixivPixels(37, 23, IntArray(37 * 23) {
            seed = seed * 1103515245 + 12345
            (0xff shl 24) or (seed ushr 8 and 0xffffff)
        })
        val image = ImageIO.read(ByteArrayInputStream(PixivPng.encode(pixels)))
        assertEquals(37 to 23, image.width to image.height)
        assertContentEquals(pixels.argb.map { it and 0xffffff }.toIntArray(), IntArray(37 * 23) { image.getRGB(it % 37, it / 37) and 0xffffff })
    }

    @Test
    fun aWidePictureIsShrunkToTheWidthAskedEachPixelTheAverageOfItsArea() {
        val pixels = PixivPixels(4, 2, opaque(0x000000, 0x080808, 0xff0000, 0xff0000, 0x101010, 0x181818, 0x0000ff, 0x0000ff))
        val shrunk = pixels.scaledToWidth(2)
        assertEquals(2 to 1, shrunk.width to shrunk.height)
        assertContentEquals(opaque(0x0c0c0c, 0x800080), shrunk.argb)
    }

    @Test
    fun aPictureNarrowEnoughStaysAsItIs() {
        val pixels = PixivPixels(3, 1, opaque(1, 2, 3))
        assertSame(pixels, pixels.scaledToWidth(1000))
    }

    @Test
    fun aDecoderShrinksByPowersOfTwoButNeverBelowTheWidthAsked() {
        assertEquals(4, PixivPixels.sampleSize(4000, 1000))
        assertEquals(2, PixivPixels.sampleSize(3999, 1000))
        assertEquals(1, PixivPixels.sampleSize(800, 1000))
        assertEquals(1, PixivPixels.sampleSize(4000, null))
    }

    @Test
    fun skiasBgraBytesBecomeArgb() {
        val pixels = PixivPixels.ofBgra(2, 1, byteArrayOf(0x30, 0x20, 0x10, -1, 0, 0, -1, 0x7f))
        assertContentEquals(intArrayOf(0xff102030.toInt(), 0x7fff0000), pixels.argb)
    }
}
