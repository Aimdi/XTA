package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewerGesturesTest {
    private fun hold(touch: Boolean = true, playing: Boolean = true, live: Boolean = false, durationMs: Long = 60_000, speed: Double = 1.0) =
        VideoRules.holdSpeed(touch, playing, live, durationMs, speed)

    @Test
    fun aFingerHeldOnAPlayingVideoDoublesItsSpeed() {
        assertEquals(2.0, hold())
        assertEquals(2.0, hold(speed = 1.75))
        assertEquals(2.0, hold(speed = 0.5))
    }

    @Test
    fun holdingDoesNothingWhereThereIsNothingToSpeedUp() {
        assertNull(hold(touch = false))
        assertNull(hold(playing = false))
        assertNull(hold(live = true))
        assertNull(hold(durationMs = 0))
        assertNull(hold(speed = 2.0))
    }

    @Test
    fun aTapHidesTheBarOfAPhotoOnly() {
        assertTrue(ViewerChrome.shown(hidden = false, photo = true, screenReader = false))
        assertFalse(ViewerChrome.shown(hidden = true, photo = true, screenReader = false))
        assertTrue(ViewerChrome.shown(hidden = true, photo = false, screenReader = false))
    }

    @Test
    fun theBarStaysUpForAScreenReader() {
        assertTrue(ViewerChrome.shown(hidden = true, photo = true, screenReader = true))
    }

    @Test
    fun altShowsOnADescribedPhotoWhileTheBarIsUp() {
        assertEquals("a cat", ViewerChrome.alt(" a cat ", photo = true, shown = true))
        assertNull(ViewerChrome.alt("a cat", photo = true, shown = false))
        assertNull(ViewerChrome.alt("a cat", photo = false, shown = true))
        assertNull(ViewerChrome.alt("  ", photo = true, shown = true))
        assertNull(ViewerChrome.alt(null, photo = true, shown = true))
    }
}
