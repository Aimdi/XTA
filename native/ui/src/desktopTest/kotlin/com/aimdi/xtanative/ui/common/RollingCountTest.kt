package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RollingCountTest {
    @Test
    fun `a shown number changing to another rolls out of the old one`() {
        assertEquals("12", rollingCountFrom("12", "13", reduced = false))
    }

    @Test
    fun `the same text, a count appearing or going, and reduced motion change at once`() {
        assertNull(rollingCountFrom("12", "12", reduced = false))
        assertNull(rollingCountFrom("", "1", reduced = false))
        assertNull(rollingCountFrom("1", "", reduced = false))
        assertNull(rollingCountFrom("12", "13", reduced = true))
    }

    @Test
    fun `a rise or an unknown number rolls upwards, a fall downwards`() {
        assertTrue(rollingCountRises(12, 13))
        assertTrue(rollingCountRises(null, 13))
        assertFalse(rollingCountRises(13, 12))
    }
}
