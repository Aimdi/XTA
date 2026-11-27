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
}
