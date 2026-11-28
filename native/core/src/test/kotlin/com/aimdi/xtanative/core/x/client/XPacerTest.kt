package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.x.XPacer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class XPacerTest {
    private fun millis(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        runBlocking { block() }
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun oneAccountsRequestsAreSpacedOthersAreNot() {
        val pacer = XPacer(100L..100L)
        assertTrue(millis { repeat(3) { pacer.await("a") } } >= 190, "three requests of one account: two gaps")
        assertTrue(millis { pacer.await("b"); pacer.await("c") } < 90, "other accounts go at once")
    }

    @Test
    fun noGapByDefault() {
        val pacer = XPacer()
        assertTrue(millis { repeat(50) { pacer.await("a") } } < 50)
    }
}
