package com.aimdi.xtanative.ui.plugins

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PullWorkTest {
    @Test
    fun theIndicatorStaysUntilTheWorkHasEnded(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val pull = PullWork(this) { gate.await() }
        assertFalse(pull.running.value)

        pull.start()
        assertTrue(pull.running.value, "up as soon as it is pulled")
        gate.complete(Unit)
        pull.running.first { !it }
    }

    @Test
    fun aPullWhileOneRunsStartsNothing(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val pull = PullWork(this) {
            runs++
            gate.await()
        }
        pull.start()
        pull.start()
        gate.complete(Unit)
        pull.running.first { !it }
        assertEquals(1, runs)

        pull.start()
        pull.running.first { !it }
        assertEquals(2, runs, "and the next pull runs again")
    }

    @Test
    fun workThatFailsStillEndsThePull(): Unit = runBlocking {
        val pull = PullWork(this) { throw IOException("offline") }
        pull.start()
        pull.running.first { !it }
    }
}
