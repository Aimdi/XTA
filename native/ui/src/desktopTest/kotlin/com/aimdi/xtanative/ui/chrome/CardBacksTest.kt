package com.aimdi.xtanative.ui.chrome

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** A post's card that does not open says so, so what asked for it (going forward) keeps its step. */
class CardBacksTest {
    /** Runs what is sent to it only when told to. */
    private class Held : CoroutineDispatcher() {
        val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued.addLast(block)
        }
        fun runAll() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    @Test
    fun aCardOpenedWhileAnotherIsOpeningIsAbandoned() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        val done = mutableListOf<String>()
        backs.open(scope, abandoned = { done += "first abandoned" }) { done += "first" }
        backs.open(scope, abandoned = { done += "second abandoned" }) { done += "second" }
        assertEquals(listOf("second abandoned"), done)
        held.runAll()
        assertEquals(listOf("second abandoned", "first"), done)
    }

    @Test
    fun aCardWhoseWorkIsCancelledIsAbandoned() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val done = mutableListOf<String>()
        CardBacks().open(scope, abandoned = { done += "abandoned" }) { done += "navigated" }
        scope.cancel()
        held.runAll()
        assertEquals(listOf("abandoned"), done)
    }
}
