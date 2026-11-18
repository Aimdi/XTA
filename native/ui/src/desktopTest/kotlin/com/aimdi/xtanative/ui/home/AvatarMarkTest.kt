package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarMarkTest {
    @Test
    fun aPluginsOwnCountWinsOverHomesDot() {
        val counted = AvatarMark(unread = true, count = 12)
        assertEquals("12", counted.badge)
        assertFalse(counted.dot, "the number says it already")
        assertEquals("99+", AvatarMark(unread = false, count = 140).badge)
    }

    @Test
    fun withoutACountHomesUnreadIsADotAndNothingReadIsNothing() {
        assertTrue(AvatarMark(unread = true, count = null).dot)
        assertNull(AvatarMark(unread = true, count = null).badge)
        val read = AvatarMark(unread = true, count = 0)
        assertNull(read.badge, "the plugin says none are unread")
        assertFalse(read.dot)
        assertFalse(AvatarMark(unread = false, count = null).dot)
    }
}
