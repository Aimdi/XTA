package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivFrameBytes
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/**
 * A looping GIF written a frame at a time, as XTA's encodePixivGif makes one
 * of an ugoira: each frame up for its own delay, with a palette of its own:
 * its colours when it has no more than 256, else 256 picked by median cut,
 * the rest dithered (Floyd–Steinberg) as XTA's encoder dithers them.
 */
internal class PixivGifWriter {
    private val out = ByteArrayOutputStream()
    private var frames = 0

    fun add(frame: PixivPixels, centiseconds: Int) {
        if (frames++ == 0) header(frame.width, frame.height)
        val palette = PixivPalette.of(frame)
        control(centiseconds)
        image(frame, palette)
    }

    /** The GIF once a frame is in it; null with none. */
    fun finish(): ByteArray? = if (frames == 0) null else out.apply { write(TRAILER) }.toByteArray()

    private fun header(width: Int, height: Int) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        out.write(byteArrayOf(0, 0, 0))
        // NETSCAPE2.0: loop for ever.
        out.write(byteArrayOf(0x21, -1, 11))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun control(centiseconds: Int) {
        out.write(byteArrayOf(0x21, -7, 4, DO_NOT_DISPOSE))
        short(centiseconds)
        out.write(byteArrayOf(0, 0))
    }

    private fun image(frame: PixivPixels, palette: PixivPalette) {
        out.write(0x2C)
        short(0)
        short(0)
        short(frame.width)
        short(frame.height)
        out.write(0x80 or 7)
        for (at in 0 until PixivPalette.SIZE) {
            val color = palette.colors.getOrElse(at) { 0 }
            out.write(color shr 16 and 0xff)
            out.write(color shr 8 and 0xff)
            out.write(color and 0xff)
        }
        PixivLzw.write(out, palette.indexes)
    }

    private fun short(value: Int) {
        out.write(value and 0xff)
        out.write(value shr 8 and 0xff)
    }

    companion object {
        private const val TRAILER = 0x3B

        /** The graphic control's packed field: disposal method 1, the frame left as it is. */
        private const val DO_NOT_DISPOSE: Byte = 4

        /** XTA's pixivGifCentiseconds: GIF counts delays in hundredths of a second, and most viewers speed up anything under two. */
        fun centiseconds(milliseconds: Long): Int = (milliseconds / 10.0).roundToInt().coerceIn(2, 65535)

        /**
         * [frames] as a looping GIF, each decoded by [decode] as it comes, so
         * a long animation never sits in memory decoded whole; [onFrame]
         * hears how many are in. Stops when its coroutine is cancelled.
         */
        suspend fun encode(frames: List<PixivFrameBytes>, decode: (ByteArray) -> PixivPixels?, onFrame: (done: Int) -> Unit = {}): ByteArray {
            val writer = PixivGifWriter()
            frames.forEachIndexed { index, frame ->
                coroutineContext.ensureActive()
                writer.add(decode(frame.bytes) ?: throw IOException("Unreadable ugoira frame ${index + 1}"), centiseconds(frame.delay.inWholeMilliseconds))
                onFrame(index + 1)
            }
            return writer.finish() ?: throw IOException("No ugoira frames")
        }
    }
}

/** A frame's colours as a GIF frame holds them: at most [SIZE] RGB colours, and each pixel's index among them. */
internal class PixivPalette(val colors: IntArray, val indexes: ByteArray) {
    companion object {
        const val SIZE = 256

        fun of(frame: PixivPixels): PixivPalette = exact(frame) ?: dithered(frame, PixivMedianCut.palette(frame.argb, SIZE))

        /** The frame's own colours, when it has no more than [SIZE]. */
        private fun exact(frame: PixivPixels): PixivPalette? {
            val found = LinkedHashMap<Int, Int>()
            val indexes = ByteArray(frame.argb.size)
            frame.argb.forEachIndexed { at, pixel ->
                val index = found.getOrPut(pixel and 0xffffff) { if (found.size == SIZE) return null else found.size }
                indexes[at] = index.toByte()
            }
            return PixivPalette(found.keys.toIntArray(), indexes)
        }

        /**
         * Each pixel as the nearest of [colors], what it misses by spread to
         * the pixels after it (Floyd–Steinberg, in sixteenths): [row] holds
         * what reaches this row's pixels, [below] the next row's, a column of
         * slack at each side.
         */
        private fun dithered(frame: PixivPixels, colors: IntArray): PixivPalette {
            val width = frame.width
            val nearest = PixivNearest(colors)
            val indexes = ByteArray(frame.argb.size)
            var row = Array(3) { IntArray(width + 2) }
            var below = Array(3) { IntArray(width + 2) }
            val wanted = IntArray(3)
            for (y in 0 until frame.height) {
                for (x in 0 until width) {
                    val pixel = frame.argb[y * width + x]
                    for (channel in 0..2) wanted[channel] = ((pixel shr (16 - channel * 8) and 0xff) + row[channel][x + 1] / 16).coerceIn(0, 255)
                    val index = nearest.of(wanted[0], wanted[1], wanted[2])
                    indexes[y * width + x] = index.toByte()
                    for (channel in 0..2) {
                        val miss = wanted[channel] - (colors[index] shr (16 - channel * 8) and 0xff)
                        row[channel][x + 2] += miss * 7
                        below[channel][x] += miss * 3
                        below[channel][x + 1] += miss * 5
                        below[channel][x + 2] += miss
                    }
                }
                row = below.also { below = row }
                below.forEach { it.fill(0) }
            }
            return PixivPalette(colors, indexes)
        }
    }
}

/** The nearest of a palette's colours, found once for each of the 32 768 five-bit colours asked about. */
private class PixivNearest(private val colors: IntArray) {
    private val known = IntArray(1 shl 15) { -1 }

    /** The nearest to the middle of the five-bit colour [red], [green], [blue] fall in. */
    fun of(red: Int, green: Int, blue: Int): Int {
        val key = (red shr 3 shl 10) or (green shr 3 shl 5) or (blue shr 3)
        if (known[key] < 0) known[key] = closest(red shr 3 shl 3 or 4, green shr 3 shl 3 or 4, blue shr 3 shl 3 or 4)
        return known[key]
    }

    private fun closest(red: Int, green: Int, blue: Int): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        colors.forEachIndexed { index, color ->
            val dr = (color shr 16 and 0xff) - red
            val dg = (color shr 8 and 0xff) - green
            val db = (color and 0xff) - blue
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }
}

/** Median cut over five-bit colours: the box with the most pixels for its size halved at its median until there are enough. */
private object PixivMedianCut {
    private class Box(val bins: IntArray, val counts: IntArray) {
        private val low = IntArray(3) { channel -> bins.minOf { channelOf(it, channel) } }
        private val high = IntArray(3) { channel -> bins.maxOf { channelOf(it, channel) } }
        val population: Long = bins.sumOf { counts[it].toLong() }

        val longest: Int get() = (0..2).maxBy { high[it] - low[it] }

        /** Pixels times the size of the box: a big box of few colours is split before a small one of many. */
        val weight: Long get() = if (bins.size < 2) -1 else population * (0..2).fold(1L) { size, channel -> size * (high[channel] - low[channel] + 1) }

        fun split(): List<Box> {
            val channel = longest
            val sorted = bins.sortedBy { channelOf(it, channel) }
            var seen = 0L
            val cut = sorted.indexOfFirst { bin -> seen += counts[bin]; seen * 2 >= population }.coerceIn(0, sorted.size - 2) + 1
            return listOf(Box(sorted.subList(0, cut).toIntArray(), counts), Box(sorted.subList(cut, sorted.size).toIntArray(), counts))
        }
    }

    private fun channelOf(bin: Int, channel: Int): Int = bin shr (10 - channel * 5) and 31

    fun palette(argb: IntArray, size: Int): IntArray {
        val counts = IntArray(1 shl 15)
        val sums = Array(3) { LongArray(1 shl 15) }
        for (pixel in argb) {
            val bin = (pixel shr 19 and 31 shl 10) or (pixel shr 11 and 31 shl 5) or (pixel shr 3 and 31)
            counts[bin]++
            for (channel in 0..2) sums[channel][bin] += (pixel shr (16 - channel * 8) and 0xff).toLong()
        }
        val boxes = mutableListOf(Box(counts.indices.filter { counts[it] > 0 }.toIntArray(), counts))
        while (boxes.size < size) {
            val widest = boxes.maxBy { it.weight }.takeIf { it.weight > 0 } ?: break
            boxes.remove(widest)
            boxes += widest.split()
        }
        return boxes.map { box -> mean(box, counts, sums) }.toIntArray()
    }

    private fun mean(box: Box, counts: IntArray, sums: Array<LongArray>): Int = (0..2).fold(0) { color, channel ->
        val total = box.bins.sumOf { sums[channel][it] }
        color or ((total / box.population).toInt() shl (16 - channel * 8))
    }
}

/** GIF's LZW of a frame's eight-bit indexes, its codes packed from the low bit up in blocks of at most 255 bytes. */
private object PixivLzw {
    private const val CLEAR = 256
    private const val END = 257
    private const val FIRST = 258
    private const val LIMIT = 4096
    private const val SLOTS = 8192

    fun write(out: ByteArrayOutputStream, indexes: ByteArray) {
        out.write(8)
        val bits = Blocks(out)
        val keys = IntArray(SLOTS) { -1 }
        val codes = IntArray(SLOTS)
        var width = 9
        var next = FIRST
        bits.put(CLEAR, width)
        var prefix = indexes[0].toInt() and 0xff
        for (at in 1 until indexes.size) {
            val symbol = indexes[at].toInt() and 0xff
            val key = (prefix shl 8) or symbol
            var slot = (key * -0x61c88647) ushr 19
            while (keys[slot] != -1 && keys[slot] != key) slot = (slot + 1) and (SLOTS - 1)
            if (keys[slot] == key) {
                prefix = codes[slot]
                continue
            }
            bits.put(prefix, width)
            if (next < LIMIT) {
                keys[slot] = key
                codes[slot] = next++
                if (next > (1 shl width) && width < 12) width++
            } else {
                bits.put(CLEAR, width)
                keys.fill(-1)
                width = 9
                next = FIRST
            }
            prefix = symbol
        }
        bits.put(prefix, width)
        bits.put(END, width)
        bits.flush()
        out.write(0)
    }

    private class Blocks(private val out: ByteArrayOutputStream) {
        private val block = ByteArray(255)
        private var filled = 0
        private var pending = 0L
        private var count = 0

        fun put(code: Int, width: Int) {
            pending = pending or (code.toLong() shl count)
            count += width
            while (count >= 8) {
                byte((pending and 0xff).toInt())
                pending = pending ushr 8
                count -= 8
            }
        }

        fun flush() {
            if (count > 0) byte((pending and 0xff).toInt())
            pending = 0
            count = 0
            if (filled > 0) emit()
        }

        private fun byte(value: Int) {
            block[filled++] = value.toByte()
            if (filled == block.size) emit()
        }

        private fun emit() {
            out.write(filled)
            out.write(block, 0, filled)
            filled = 0
        }
    }
}
