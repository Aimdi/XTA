package com.aimdi.xtanative.ui.chrome

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClosingWritesTest {
    @Test
    fun theClosingWritesRunOnceThoughBothTheQuitAndTheHookAskForThem() {
        val runs = AtomicInteger()
        val closing = ClosingWrites { runs.incrementAndGet() }
        assertTrue(closing.run(), "the quit runs them")
        assertFalse(closing.run(), "the hook that exitProcess starts finds them done")
        assertEquals(1, runs.get())
    }

    @Test
    fun aWriteThatFailsStillCountsAsDoneAndDoesNotThrow() {
        val runs = AtomicInteger()
        val closing = ClosingWrites {
            runs.incrementAndGet()
            error("disk full")
        }
        assertTrue(closing.run())
        assertFalse(closing.run())
        assertEquals(1, runs.get())
    }

    @Test
    fun twoEndsAtOnceRunThemOnceAndTheLaterWaitsForTheWrites() {
        val runs = AtomicInteger()
        val finished = AtomicInteger()
        val closing = ClosingWrites {
            runs.incrementAndGet()
            Thread.sleep(200)
            finished.incrementAndGet()
        }
        val go = CountDownLatch(1)
        val seen = List(8) { AtomicInteger(-1) }
        val ends = seen.map { after -> thread { go.await(); closing.run(); after.set(finished.get()) } }
        go.countDown()
        ends.forEach { it.join() }
        assertEquals(1, runs.get())
        assertTrue(seen.all { it.get() == 1 }, "every end returned once the writes were done")
    }
}
