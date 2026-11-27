package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DesktopScaleTest {
    private val steps = DesktopPrefs.UI_SCALES

    @Test
    fun theDesktopsTextDpiGivesTheNearestInterfaceSize() {
        assertEquals(1.5, DesktopScale.sizeFor(144.0, 1.0, steps), "KDE at 150%")
        assertEquals(1.25, DesktopScale.sizeFor(120.0, 1.0, steps))
        assertEquals(2.0, DesktopScale.sizeFor(192.0, 1.0, steps))
        assertEquals(1.75, DesktopScale.sizeFor(168.0, 1.0, steps))
        assertEquals(1.1, DesktopScale.sizeFor(106.0, 1.0, steps), "GNOME's large text, 1.1")
        assertEquals(2.0, DesktopScale.sizeFor(288.0, 1.0, steps), "past the largest: the largest")
        assertEquals(0.8, DesktopScale.sizeFor(72.0, 1.0, steps))
    }

    @Test
    fun aDesktopAt100PercentOrOneJavaScalesAlreadyLeavesTheDefault() {
        assertNull(DesktopScale.sizeFor(96.0, 1.0, steps))
        assertNull(DesktopScale.sizeFor(98.0, 1.0, steps), "rounds to 1.0")
        assertNull(DesktopScale.sizeFor(192.0, 2.0, steps), "GDK_SCALE=2 with its dpi: Java draws at 2x itself")
        assertEquals(1.5, DesktopScale.sizeFor(288.0, 2.0, steps), "300% where Java does 2x")
        assertNull(DesktopScale.sizeFor(null, 1.0, steps))
        assertNull(DesktopScale.sizeFor(0.0, 1.0, steps))
        assertNull(DesktopScale.sizeFor(144.0, 0.0, steps))
    }

    @Test
    fun xftDpiIsReadFromXrdbAndXsettings() {
        assertEquals(144.0, DesktopScale.xrdbDpi("Xcursor.size:\t24\nXft.dpi:\t144\nXft.hinting:\t1\n"))
        assertEquals(120.5, DesktopScale.xrdbDpi("Xft.dpi: 120.5"))
        assertNull(DesktopScale.xrdbDpi("Xcursor.size:\t24\n"))
        assertNull(DesktopScale.xrdbDpi("Xft.dpi:\tlarge\n"))
        assertNull(DesktopScale.xrdbDpi(""))
        assertEquals(144.0, DesktopScale.xsettingsDpi(147_456))
        assertNull(DesktopScale.xsettingsDpi(-1))
        assertNull(DesktopScale.xsettingsDpi("144"))
        assertNull(DesktopScale.xsettingsDpi(null))
    }

    @Test
    fun onlyAFirstStartWithNoSizeOfTheReadersIsSeeded() {
        val empty = Settings(emptyMap())
        assertEquals(1.5, DesktopScale.seeded(empty) { 1.5 }[DesktopPrefs.uiScale])
        val chosen = empty.with(DesktopPrefs.uiScale, 1.0)
        var asked = false
        assertSame(chosen, DesktopScale.seeded(chosen) { asked = true; 1.5 })
        assertEquals(false, asked, "the desktop is not even asked")
        assertSame(empty, DesktopScale.seeded(empty) { null })
    }
}
