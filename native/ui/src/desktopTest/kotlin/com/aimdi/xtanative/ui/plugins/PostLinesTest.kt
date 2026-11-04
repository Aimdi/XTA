package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class PostLinesTest {
    @Test
    fun aNoteFollowsTheTime() {
        assertEquals("5m · Edited", PostLines.time("5m", "Edited"))
    }

    @Test
    fun aMissingPartLeavesNoDot() {
        assertEquals("5m", PostLines.time("5m", null))
        assertEquals("Edited", PostLines.time("", "Edited"))
        assertEquals("", PostLines.time("", " "))
    }
}
