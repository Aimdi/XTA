package com.aimdi.xtanative.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/** A sheet closed from a button in it slides away first, then is dismissed and the button's work done. */
class SheetCloserTest {
    private val steps = mutableListOf<String>()

    @Test
    fun theSheetSlidesAwayBeforeItIsDismissedAndThePickRuns() = runBlocking {
        val slid = CompletableDeferred<Unit>()
        val closer = SheetCloser(CoroutineScope(coroutineContext), { steps += "slide"; slid.await() }) { steps += "dismiss" }
        closer.close { steps += "pick" }
        yield()
        assertEquals(listOf("slide"), steps)
        slid.complete(Unit)
        yield()
        yield()
        assertEquals(listOf("slide", "dismiss", "pick"), steps)
    }

    @Test
    fun aSecondPressWhileItSlidesDoesNothing() = runBlocking {
        val slid = CompletableDeferred<Unit>()
        val closer = SheetCloser(CoroutineScope(coroutineContext), { slid.await() }) { steps += "dismiss" }
        closer.close { steps += "first" }
        closer.close { steps += "second" }
        slid.complete(Unit)
        yield()
        yield()
        assertEquals(listOf("dismiss", "first"), steps)
    }

    @Test
    fun withMotionReducedEverythingHappensAtOnce() {
        val closer = SheetCloser(CoroutineScope(Dispatchers.Unconfined), { steps += "slide" }) { steps += "dismiss" }
        closer.reduced = true
        closer.close { steps += "pick" }
        assertEquals(listOf("dismiss", "pick"), steps)
    }

    @Test
    fun aSlideStoppedLeavesTheSheetOpenAndThePressUndone() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var dragged = true
        val closer = SheetCloser(scope, { if (dragged) throw CancellationException("dragged back") }) { steps += "dismiss" }
        closer.close { steps += "pick" }
        assertEquals(emptyList(), steps)
        // A later press closes it.
        dragged = false
        closer.close { steps += "pick" }
        assertEquals(listOf("dismiss", "pick"), steps)
        scope.cancel()
    }

    @Test
    fun outsideASheetTheWorkIsDoneAtOnce() {
        SheetCloser.None.close { steps += "pick" }
        val action = SheetCloser.None.closing { value: Int -> steps += "picked $value" }
        action(3)
        assertEquals(listOf("pick", "picked 3"), steps)
    }
}
