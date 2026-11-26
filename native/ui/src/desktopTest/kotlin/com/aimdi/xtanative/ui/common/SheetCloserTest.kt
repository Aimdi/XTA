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
    fun aSlideStoppedByAClickOnTheScrimStillRunsThePickOnceTheSheetGoes() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val settled = CompletableDeferred<Boolean>()
        val closer = SheetCloser(
            scope,
            { throw CancellationException("scrim clicked") },
            settledOpen = { settled.await() },
        ) { steps += "dismiss" }
        closer.close { steps += "pick" }
        assertEquals(emptyList(), steps)
        // The scrim's own slide ends hidden: the sheet's onDismissRequest.
        closer.dismissed()
        settled.complete(false)
        assertEquals(listOf("dismiss", "pick"), steps)
        // A later dismissal does not run the pick again.
        closer.dismissed()
        assertEquals(listOf("dismiss", "pick", "dismiss"), steps)
        scope.cancel()
    }

    @Test
    fun aSheetDraggedBackOpenDoesNotRunTheUndonePickWhenItIsDismissedLater() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val closer = SheetCloser(
            scope,
            { throw CancellationException("dragged back") },
            settledOpen = { true },
        ) { steps += "dismiss" }
        closer.close { steps += "pick" }
        closer.dismissed()
        assertEquals(listOf("dismiss"), steps)
        scope.cancel()
    }

    @Test
    fun aSlideStoppedByAClickGoesOnOnceItIsLetGo() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val slides = List(2) { CompletableDeferred<Unit>() }
        var next = 0
        val closer = SheetCloser(scope, { slides[next++].await() }, settledOpen = { true }) { steps += "dismiss" }
        closer.close { steps += "pick" }
        // A press while it slides stops it; nothing happens while it is held.
        closer.pressDown()
        slides[0].completeExceptionally(CancellationException("pressed while it slid"))
        assertEquals(emptyList(), steps)
        closer.pressUp()
        slides[1].complete(Unit)
        assertEquals(listOf("dismiss", "pick"), steps)
        scope.cancel()
    }

    @Test
    fun aDoubleClickWhoseSecondClickAlsoPicksAndWhoseSettlingStopsThatSlideStillClosesOnce() = runBlocking {
        val slides = List(3) { CompletableDeferred<Unit>() }
        var next = 0
        val closer = SheetCloser(CoroutineScope(coroutineContext), { slides[next++].await() }, settledOpen = { true }) { steps += "dismiss" }
        closer.close { steps += "pick" }
        yield()
        // The second click: its press stops the slide...
        closer.pressDown()
        slides[0].completeExceptionally(CancellationException("pressed while it slid"))
        yield()
        // ...its release is a click on the chip again, and the sheet settling back open stops that slide.
        closer.pressUp()
        closer.close { steps += "pick again" }
        yield()
        slides[1].completeExceptionally(CancellationException("the sheet settled"))
        repeat(4) { yield() }
        assertEquals(3, next)
        slides[2].complete(Unit)
        repeat(3) { yield() }
        assertEquals(listOf("dismiss", "pick again"), steps)
    }

    @Test
    fun aPressThatDragsTheSheetBackUndoesThePick() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val slid = CompletableDeferred<Unit>()
        val closer = SheetCloser(scope, { slid.await() }, settledOpen = { true }) { steps += "dismiss" }
        closer.close { steps += "pick" }
        closer.pressDown()
        slid.completeExceptionally(CancellationException("dragged"))
        closer.pressDragged()
        closer.pressUp()
        assertEquals(emptyList(), steps)
        closer.dismissed()
        assertEquals(listOf("dismiss"), steps)
        scope.cancel()
    }

    @Test
    fun aPressBeforeTheSlideDoesNotCountAsStoppingIt() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val closer = SheetCloser(scope, { throw CancellationException("dragged back") }, settledOpen = { true }) { steps += "dismiss" }
        // The button's own click: down and up before the slide starts.
        closer.pressDown()
        closer.pressUp()
        closer.close { steps += "pick" }
        assertEquals(emptyList(), steps)
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
