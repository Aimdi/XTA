package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivFrameBytes
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A looping animated PNG written a frame at a time: every colour of each
 * frame kept (no palette, no dithering, unlike [PixivGifWriter]), each frame
 * up for its own delay to the millisecond. After the first, a frame holds
 * only the area that changed, drawn over the one before, so a still
 * background is stored once.
 */
internal class PixivApngWriter(private val frameCount: Int) {
    private val out = ByteArrayOutputStream()
    private var previous: PixivPixels? = null
    private var sequence = 0

    fun add(frame: PixivPixels, milliseconds: Long) {
        val before = previous
        if (before == null) {
            out.write(PixivPng.SIGNATURE)
            PixivPng.chunk(out, "IHDR", PixivPng.header(frame.width, frame.height))
            PixivPng.chunk(out, "acTL", ints(frameCount, 0))
        } else if (before.width != frame.width || before.height != frame.height) {
            throw IOException("Ugoira frames of different sizes")
        }
        val area = before?.let { changedArea(it, frame) } ?: Area(0, 0, frame.width, frame.height)
        PixivPng.chunk(out, "fcTL", control(area, milliseconds))
        val data = PixivPng.deflated(frame, area.left, area.top, area.width, area.height)
        if (before == null) PixivPng.chunk(out, "IDAT", data) else PixivPng.chunk(out, "fdAT", ints(sequence++) + data)
        previous = frame
    }

    /** The APNG once a frame is in it; null with none. */
    fun finish(): ByteArray? = previous?.let {
        PixivPng.chunk(out, "IEND", ByteArray(0))
        out.toByteArray()
    }

    /** fcTL's data: the frame's [area] and delay, drawn over what the frame before left (dispose none, blend source). */
    private fun control(area: Area, milliseconds: Long): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).run {
            writeInt(sequence++)
            writeInt(area.width)
            writeInt(area.height)
            writeInt(area.left)
            writeInt(area.top)
            writeShort(milliseconds.coerceIn(0, 65_535).toInt())
            writeShort(1000)
            write(byteArrayOf(0, 0))
        }
    }.toByteArray()

    private fun ints(vararg values: Int): ByteArray = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).run { values.forEach(::writeInt) } }.toByteArray()

    /** A rectangle of a frame. */
    data class Area(val left: Int, val top: Int, val width: Int, val height: Int)

    companion object {
        /**
         * The smallest rectangle holding every pixel whose colour differs
         * between [before] and [after]; one pixel at the corner when none
         * does, as a frame needs an area.
         */
        fun changedArea(before: PixivPixels, after: PixivPixels): Area {
            var left = after.width
            var top = after.height
            var right = -1
            var bottom = -1
            for (y in 0 until after.height) for (x in 0 until after.width) {
                val at = y * after.width + x
                if (((before.argb[at] xor after.argb[at]) and 0xffffff) == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
            return if (right < 0) Area(0, 0, 1, 1) else Area(left, top, right - left + 1, bottom - top + 1)
        }

        /**
         * [frames] as a looping APNG, each decoded by [decode] as it comes, so
         * no more than two sit in memory decoded; [onFrame] hears how many
         * are in. Stops when its coroutine is cancelled.
         */
        suspend fun encode(frames: List<PixivFrameBytes>, decode: (ByteArray) -> PixivPixels?, onFrame: (done: Int) -> Unit = {}): ByteArray {
            val writer = PixivApngWriter(frames.size)
            frames.forEachIndexed { index, frame ->
                coroutineContext.ensureActive()
                writer.add(decode(frame.bytes) ?: throw IOException("Unreadable ugoira frame ${index + 1}"), frame.delay.inWholeMilliseconds)
                onFrame(index + 1)
            }
            return writer.finish() ?: throw IOException("No ugoira frames")
        }
    }
}
