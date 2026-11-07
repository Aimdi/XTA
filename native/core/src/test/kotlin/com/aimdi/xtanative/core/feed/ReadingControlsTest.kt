package com.aimdi.xtanative.core.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's home_reading_controls_test, for the rule the row follows. */
class ReadingControlsTest {
    private val saved = HashMap<String, Boolean>()

    private fun controls(): ReadingControls = ReadingControls().also { it.bind("blue", false) }

    private fun ReadingControls.scroll(
        delta: Double,
        before: Double = 200.0,
        extent: Double = 1000.0,
        height: Double = 48.0,
        protected: Boolean = false,
        source: String = "blue",
    ) = observe(source, before, extent, height, delta, protected)

    @Test
    fun deliberateDownHidesSmallReversalsDoNotFlickerAndDeliberateUpReveals() {
        val store = controls()
        store.scroll(20.0)
        store.scroll(20.0)
        assertTrue(store.state.visible)
        store.scroll(30.0)
        assertFalse(store.state.visible)
        store.scroll(-8.0)
        assertFalse(store.state.visible)
        store.scroll(-20.0)
        assertTrue(store.state.visible)
    }

    @Test
    fun programmaticJumpsAndStaleSourceNotificationsCannotHideControls() {
        val store = controls()
        store.scroll(0.0, before = 900.0)
        assertTrue(store.state.visible)
        store.scroll(200.0, source = "departed")
        assertTrue(store.state.visible)
    }

    @Test
    fun shortFeedsAndProtectedMomentsStayVisible() {
        val store = controls()
        store.scroll(200.0, extent = 100.0)
        assertTrue(store.state.visible)
        store.scroll(200.0, protected = true)
        assertTrue(store.state.visible)
        store.scroll(200.0)
        assertFalse(store.state.visible)
        store.scroll(0.0, protected = true)
        assertTrue(store.state.visible)
    }

    @Test
    fun reclaimedSpaceDoesNotCreateAShortFeedLoop() {
        val store = controls()
        store.scroll(100.0, extent = 160.0)
        assertFalse(store.state.visible)
        store.scroll(0.0, extent = 112.0)
        store.scroll(0.0, extent = 112.0)
        assertFalse(store.state.visible)
        store.scroll(0.0, before = 0.0, extent = 112.0)
        assertTrue(store.state.visible)
    }

    @Test
    fun pinIsReversiblePersistedAndRestoredByANewSession() = runBlocking {
        val store = controls()
        store.setPinned(true) { saved["pin"] = it; true }
        store.scroll(500.0)
        assertTrue(store.state.visible)
        assertEquals(true, saved["pin"])
        val next = ReadingControls().also { it.bind("masto", saved["pin"] == true) }
        assertTrue(next.state.pinned)
        next.setPinned(false) { saved["pin"] = it; true }
        assertFalse(next.state.pinned)
        assertEquals(false, saved["pin"])
        next.close()
    }

    @Test
    fun aFailedSaveKeepsThePreviousPin() = runBlocking {
        val store = controls()
        store.setPinned(true) { false }
        assertFalse(store.state.pinned)
        store.setPinned(true) { throw IllegalStateException("disk") }
        assertFalse(store.state.pinned)
        store.scroll(80.0)
        assertFalse(store.state.visible, "an unpinned row still hides")
    }

    @Test
    fun sourceChangesRevealControlsWithoutChangingThePin() = runBlocking {
        val store = controls()
        store.setPinned(true) { true }
        store.setPinned(false) { true }
        store.scroll(100.0)
        assertFalse(store.state.visible)
        store.bind("substack", false)
        assertTrue(store.state.visible)
        assertEquals("substack", store.state.source)
        assertFalse(store.state.pinned)
    }

    @Test
    fun aClosedStoreIgnoresAnythingFurther() = runBlocking {
        val store = controls()
        store.close()
        store.scroll(80.0)
        store.bind("other", true)
        store.setPinned(true) { true }
        assertEquals(ReadingControls.State(source = "blue"), store.state)
    }

    @Test
    fun aListThatFitsIsShortAndOneThatRunsPastIsNot() {
        assertNull(ReadingScroll.of(0, 0, 0, 0, 0, 0, 0))
        assertEquals(0.0 to 0.0, ReadingScroll.of(0, 800, 0, 0, 0, 0, 0))
        assertEquals(0.0 to 0.0, ReadingScroll.of(0, 800, 0, 0, 3, 2, 100))
        assertEquals(0.0 to 100.0, ReadingScroll.of(0, 800, 0, 0, 3, 2, 900))
        assertEquals(40.0 to 100.0, ReadingScroll.of(0, 800, 0, 40, 3, 2, 860))
        assertEquals(0.0 to ReadingScroll.LONG, ReadingScroll.of(0, 800, 0, 0, 20, 4, 800))
        assertEquals(ReadingScroll.LONG to ReadingScroll.LONG, ReadingScroll.of(0, 800, 3, 10, 20, 8, 800))
    }
}
