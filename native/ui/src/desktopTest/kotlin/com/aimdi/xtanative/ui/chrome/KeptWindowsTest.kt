package com.aimdi.xtanative.ui.chrome

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeptWindowsTest {
    private val two = listOf(
        KeptWindow("post/1", SavedWindow(680, 900, x = 10, y = 20)),
        KeptWindow("search?q=%23kotlin&tab=latest", SavedWindow(700, 800, maximized = true)),
        KeptWindow("profile/maya"),
    )

    @Test
    fun windowsComeBackInTheirOrderWithTheirPlaces() {
        assertEquals(two, KeptWindows.parse(KeptWindows.toIni(two)))
    }

    @Test
    fun aDamagedFileKeepsWhatItCanAndNoFlood() {
        assertEquals(emptyList(), KeptWindows.parse(null))
        assertEquals(emptyList(), KeptWindows.parse("garbage\n[window0]\nwidth=3\n"), "a group without a route is skipped")
        val many = (1..30).map { KeptWindow("post/$it") }
        assertEquals(KeptWindows.MAX, KeptWindows.parse(KeptWindows.toIni(many) + "[window99]\nroute=post/99\n").size)
        assertEquals(listOf("post/b", "post/a"), KeptWindows.parse("[window10]\nroute=post/a\n[window2]\nroute=post/b\n").map { it.route }, "by number, not by text")
    }

    @Test
    fun noWindowsLeftOpenRemovesTheFile() {
        val folder = createTempDirectory("kept").toFile()
        val file = File(folder, "windows.ini")
        val kept = KeptWindowsFile(file)
        kept.write(two)
        assertEquals(two, kept.read())
        kept.write(emptyList())
        assertFalse(file.exists())
        assertTrue(kept.read().isEmpty())
        folder.deleteRecursively()
    }

    @Test
    fun aRouteNoWindowOpensOnIsNotBroughtBack() {
        val text = "[window0]\nroute=settings/data\n[window1]\nroute=community/42/about\n[window2]\nroute=gallery/x/1\n[window3]\nroute=list/7\n[window4]\nroute=post/\n"
        assertEquals(listOf("community/42/about", "list/7"), KeptWindows.parse(text).map { it.route })
    }

    @Test
    fun afterACrashNoWindowIsKept() {
        val folder = createTempDirectory("kept").toFile()
        val file = File(folder, "windows.ini")
        val kept = KeptWindowsFile(file)
        kept.write(two)
        kept.crashed()
        assertFalse(file.exists(), "removed as the app crashes")
        kept.write(two)
        assertFalse(file.exists(), "and not written again as it quits")
        folder.deleteRecursively()
    }
}
