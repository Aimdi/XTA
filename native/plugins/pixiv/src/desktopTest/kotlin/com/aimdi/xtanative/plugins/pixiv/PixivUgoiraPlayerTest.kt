package com.aimdi.xtanative.plugins.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

/** How often the ugoira player decodes a frame while it loops. */
class PixivUgoiraPlayerTest {
    private class Played : RuntimeException()

    /** Decodes asked for while [frames] frames of 60 ms each play for [seconds] seconds, each frame taking [frameBytes] decoded. */
    private fun decodesWhilePlaying(frames: Int, frameBytes: Long, seconds: Int): Pair<Int, List<Int>> = runBlocking {
        var decodes = 0
        val shown = mutableListOf<Int>()
        val cache = UgoiraFrameCache(frames) { index -> decodes++; DecodedFrame(index, frameBytes) }
        var clock = 0L
        runCatching {
            play(List(frames) { 60.milliseconds }, cache, wait = { clock += 60; if (clock >= seconds * 1000L) throw Played() }) { shown += it }
        }
        decodes to shown
    }

    @Test
    fun anAnimationThatFitsIsDecodedOnceAndThenLoopsFromMemory() {
        val (decodes, shown) = decodesWhilePlaying(frames = 20, frameBytes = 600L * 600 * 4, seconds = 10)
        assertEquals(20, decodes, "20 frames of 1.4 MB fit in 48 MB: each decoded once")
        assertEquals(List(shown.size) { it % 20 }, shown, "frames still show in order, round and round")
    }

    @Test
    fun aLongAnimationIsDecodedAheadEachLoopAsBefore() {
        val (decodes, shown) = decodesWhilePlaying(frames = 100, frameBytes = 600L * 600 * 4, seconds = 10)
        assertEquals(shown.size + 1, decodes, "100 frames of 1.4 MB do not fit: one decode per frame shown, plus the one ahead")
    }

    @Test
    fun aFrameThatWouldNotDecodeIsAskedForAgain() = runBlocking {
        var asked = 0
        val cache = UgoiraFrameCache<Int>(2) { index -> asked++; if (index == 1) null else DecodedFrame(index, 10) }
        repeat(3) { cache.frame(0); cache.frame(1) }
        assertEquals(4, asked, "frame 0 once, frame 1 each time")
    }
}
