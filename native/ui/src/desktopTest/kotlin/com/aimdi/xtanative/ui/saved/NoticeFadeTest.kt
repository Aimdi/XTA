package com.aimdi.xtanative.ui.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The notice at the window's foot still says what it said while it fades out. */
class NoticeFadeTest {
    private val saved = ShownNotice(KeepNotice.MediaSaved, 1)
    private val failed = ShownNotice(KeepNotice.DownloadFailed, 2)

    @Test
    fun theNoticeShownIsDrawn() {
        assertEquals(failed, NoticeFade.drawn(failed, saved, reduced = false, settled = false))
        assertEquals(failed, NoticeFade.drawn(failed, saved, reduced = true, settled = true))
    }

    @Test
    fun aNoticeGoneIsDrawnWhileItFades() {
        assertEquals(saved, NoticeFade.drawn(null, saved, reduced = false, settled = false))
    }

    @Test
    fun onceFadedOrWithMotionReducedNothingIsDrawn() {
        assertNull(NoticeFade.drawn(null, saved, reduced = false, settled = true))
        assertNull(NoticeFade.drawn(null, saved, reduced = true, settled = false))
    }
}
