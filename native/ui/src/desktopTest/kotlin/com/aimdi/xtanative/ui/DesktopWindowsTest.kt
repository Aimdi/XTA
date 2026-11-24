package com.aimdi.xtanative.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DesktopWindowsTest {
    @Test
    fun windowsOpenInOrderAndCloseAlone() {
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
        DesktopWindows.open("post/1")
        DesktopWindows.open("post/1")
        DesktopWindows.open("post/2")
        val opened = DesktopWindows.opened.value
        assertEquals(listOf("post/1", "post/1", "post/2"), opened.map { it.route })
        assertNotEquals(opened[0], opened[1], "the same post twice is two windows")
        DesktopWindows.close(opened[0])
        assertEquals(listOf(opened[1], opened[2]), DesktopWindows.opened.value, "closing one leaves the others")
        DesktopWindows.close(opened[0])
        assertEquals(2, DesktopWindows.opened.value.size, "closing it twice changes nothing")
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
    }

    @Test
    fun theDesktopOpensWindows() {
        val open = windowOpener()
        assertNotNull(open)
    }
}
