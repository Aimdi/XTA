package com.aimdi.xtanative.plugins.pixiv

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs

/** A picture's pixels, row after row, each as ARGB: what a decoded frame or a chosen picture is before it is written again. */
internal class PixivPixels(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "$width × $height pixels need ${width * height} values, not ${argb.size}" }
    }

    /** These pixels no wider than [maxWidth], each the average of the area it stands for; these themselves when narrow enough. */
    fun scaledToWidth(maxWidth: Int): PixivPixels {
        if (width <= maxWidth) return this
        val newHeight = (height.toLong() * maxWidth / width).toInt().coerceAtLeast(1)
        return PixivPixels(maxWidth, newHeight, IntArray(maxWidth * newHeight) { at -> averageOf(at % maxWidth, at / maxWidth, maxWidth, newHeight) })
    }

    private fun averageOf(x: Int, y: Int, newWidth: Int, newHeight: Int): Int {
        val left = x * width / newWidth
        val right = maxOf(left + 1, (x + 1) * width / newWidth)
        val top = y * height / newHeight
        val bottom = maxOf(top + 1, (y + 1) * height / newHeight)
        val sums = LongArray(4)
        for (row in top until bottom) for (column in left until right) {
            val pixel = argb[row * width + column]
            for (channel in 0..3) sums[channel] += (pixel ushr (24 - channel * 8)) and 0xff
        }
        val count = (right - left) * (bottom - top)
        return (0..3).fold(0) { packed, channel -> packed or (((sums[channel] + count / 2) / count).toInt() shl (24 - channel * 8)) }
    }

    companion object {
        /** The power of two a decoder may shrink a [width]-wide picture by on the way in and keep it at least [atLeast] wide (all of it without one). */
        fun sampleSize(width: Int, atLeast: Int?): Int {
            if (atLeast == null || atLeast <= 0) return 1
            var sample = 1
            while (width / (sample * 2) >= atLeast) sample *= 2
            return sample
        }

        /** Pixels from bytes in blue, green, red, alpha order, as Skia's BGRA_8888 holds them. */
        fun ofBgra(width: Int, height: Int, bytes: ByteArray): PixivPixels = PixivPixels(width, height, IntArray(width * height) { at ->
            val b = bytes[at * 4].toInt() and 0xff
            val g = bytes[at * 4 + 1].toInt() and 0xff
            val r = bytes[at * 4 + 2].toInt() and 0xff
            val a = bytes[at * 4 + 3].toInt() and 0xff
            (a shl 24) or (r shl 16) or (g shl 8) or b
        })
    }
}

/**
 * A PNG of a picture's colours (RGB, eight bits each, every row Paeth
 * filtered), as XTA hands SauceNAO a chosen picture: any format the device
 * reads goes in one SauceNAO takes.
 */
internal object PixivPng {
    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun encode(pixels: PixivPixels): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunk(out, "IHDR", ByteArrayOutputStream().also { header ->
            DataOutputStream(header).run {
                writeInt(pixels.width)
                writeInt(pixels.height)
                write(byteArrayOf(8, 2, 0, 0, 0))
            }
        }.toByteArray())
        chunk(out, "IDAT", ByteArrayOutputStream().also { data -> DeflaterOutputStream(data).use { it.write(filtered(pixels)) } }.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Each row as Paeth's filter writes it after its filter byte. */
    private fun filtered(pixels: PixivPixels): ByteArray {
        val stride = pixels.width * 3
        val out = ByteArray((stride + 1) * pixels.height)
        var above = ByteArray(stride)
        for (y in 0 until pixels.height) {
            val row = ByteArray(stride)
            for (x in 0 until pixels.width) {
                val pixel = pixels.argb[y * pixels.width + x]
                row[x * 3] = (pixel shr 16).toByte()
                row[x * 3 + 1] = (pixel shr 8).toByte()
                row[x * 3 + 2] = pixel.toByte()
            }
            val start = y * (stride + 1)
            out[start] = 4
            for (i in 0 until stride) {
                val left = if (i >= 3) row[i - 3].toInt() and 0xff else 0
                val up = above[i].toInt() and 0xff
                val corner = if (i >= 3) above[i - 3].toInt() and 0xff else 0
                out[start + 1 + i] = ((row[i].toInt() and 0xff) - paeth(left, up, corner)).toByte()
            }
            above = row
        }
        return out
    }

    private fun paeth(left: Int, up: Int, corner: Int): Int {
        val estimate = left + up - corner
        val toLeft = abs(estimate - left)
        val toUp = abs(estimate - up)
        val toCorner = abs(estimate - corner)
        return if (toLeft <= toUp && toLeft <= toCorner) left else if (toUp <= toCorner) up else corner
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(name)
            update(data)
        }
        DataOutputStream(out).run {
            writeInt(data.size)
            write(name)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
