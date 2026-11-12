package com.aimdi.xtanative.plugins.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Home's loads end with Home. */
class ThreadsShownLoadsTest {
    @Test
    fun aLoadStartedWhileHomeIsShownIsGivenUpWhenHomeGoes(): Unit = runBlocking {
        val loads = ThreadsShownLoads(graceMillis = 10)
        val hide = loads.show()
        val never = CompletableDeferred<String>()
        val load = async { loads.whileShown { never.await() } }
        delay(5)
        hide()
        assertNull(load.await())
    }

    @Test
    fun homeBackWithinTheGraceKeepsTheLoadGoing(): Unit = runBlocking {
        val loads = ThreadsShownLoads(graceMillis = 50)
        val hide = loads.show()
        val answer = CompletableDeferred<String>()
        val load = async { loads.whileShown { answer.await() } }
        delay(5)
        hide()
        loads.show()
        delay(100)
        answer.complete("read")
        assertEquals("read", load.await())
    }

    @Test
    fun aLoadStartedWhileHomeIsNotShownRunsToItsEnd(): Unit = runBlocking {
        val loads = ThreadsShownLoads(graceMillis = 1)
        assertEquals("read", loads.whileShown { delay(10); "read" })
    }

    @Test
    fun aFailedLoadStillSaysWhy(): Unit = runBlocking {
        val loads = ThreadsShownLoads(graceMillis = 1)
        loads.show()
        val failure = runCatching { loads.whileShown<String> { throw IllegalStateException("throttled") } }.exceptionOrNull()
        assertEquals("throttled", failure?.message)
    }
}
