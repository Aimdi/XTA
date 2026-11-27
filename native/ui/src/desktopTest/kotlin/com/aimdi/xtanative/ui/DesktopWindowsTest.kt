package com.aimdi.xtanative.ui

import com.aimdi.xtanative.ui.chrome.KeptWindow
import com.aimdi.xtanative.ui.chrome.SavedWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun closedWindowsReopenLastFirstWhereTheyWere() {
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
        DesktopWindows.open("post/a")
        DesktopWindows.open("profile/b")
        val (a, b) = DesktopWindows.opened.value
        DesktopWindows.placed(b, SavedWindow(500, 600, x = 30, y = 40))
        assertEquals(listOf(KeptWindow("post/a"), KeptWindow("profile/b", SavedWindow(500, 600, x = 30, y = 40))), DesktopWindows.keptOpen())
        DesktopWindows.close(a)
        DesktopWindows.close(b)
        assertTrue(DesktopWindows.reopen())
        val back = DesktopWindows.opened.value.single()
        assertEquals("profile/b", back.route, "the last closed comes back first")
        assertEquals(SavedWindow(500, 600, x = 30, y = 40), back.bounds, "where it was")
        assertNotEquals(b.serial, back.serial, "as a new window")
        assertTrue(DesktopWindows.reopen())
        assertEquals(listOf("profile/b", "post/a"), DesktopWindows.opened.value.map { it.route })
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
    }

    @Test
    fun onlyTheLastClosedWindowsAreKept() {
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
        repeat(DesktopWindows.CLOSED_KEPT + 5) { DesktopWindows.open("post/$it") }
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
        var reopened = 0
        while (DesktopWindows.reopen()) reopened++
        assertEquals(DesktopWindows.CLOSED_KEPT, reopened)
        assertEquals("post/${DesktopWindows.CLOSED_KEPT + 4}", DesktopWindows.opened.value.first().route, "the newest closed first")
        assertEquals("post/5", DesktopWindows.opened.value.last().route, "the oldest five are forgotten")
        DesktopWindows.opened.value.forEach(DesktopWindows::close)
    }

    @Test
    fun theDesktopOpensWindows() {
        val open = windowOpener()
        assertNotNull(open)
    }

    @Test
    fun theMainWindowComesUpForWhatAPostWindowAskedOnlyWhenNotInFrontAlready() {
        assertTrue(DesktopWindows.showsMain(mainShown = true, mainActive = false, appActive = true), "behind the post window that asked")
        assertTrue(DesktopWindows.showsMain(mainShown = false, mainActive = false, appActive = true), "hidden in the tray, or minimized")
        assertFalse(DesktopWindows.showsMain(mainShown = true, mainActive = true, appActive = true), "in front already")
        assertFalse(DesktopWindows.showsMain(mainShown = true, mainActive = false, appActive = false), "another program in use keeps its focus")
        assertFalse(DesktopWindows.showsMain(mainShown = false, mainActive = false, appActive = false))
    }
}
