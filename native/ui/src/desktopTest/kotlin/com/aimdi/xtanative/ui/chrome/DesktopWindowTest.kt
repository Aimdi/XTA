package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopWindowTest {
    @Test
    fun theWindowComesBackAsItWasLeftAndAFirstOneOpensTall() {
        assertEquals(SavedWindow(1100, 820, false), SavedWindow.parse(null))
        assertEquals(SavedWindow(900, 700, true), SavedWindow.parse("[window]\nwidth=900\nheight=700\nmaximized=true\n"))
        val kept = SavedWindow(640, 980, true)
        assertEquals(kept, SavedWindow.parse(kept.toIni()))
    }

    @Test
    fun aDamagedFileKeepsTheFirstSizeButStillItsMaximizing() {
        assertEquals(SavedWindow(1100, 820, true), SavedWindow.parse("[window]\nwidth=0\nheight=700\nmaximized=true"))
        assertEquals(SavedWindow(1100, 820, false), SavedWindow.parse("[window]\nwidth=20000\nheight=700"))
        assertEquals(SavedWindow(1100, 820, false), SavedWindow.parse("[other]\nwidth=900\nheight=700\nmaximized=true"))
        assertEquals(SavedWindow(1100, 820, false), SavedWindow.parse("garbage"))
    }

    @Test
    fun aSizeKeptOnALargerScreenIsCutToThisOne() {
        assertEquals(SavedWindow(1280, 720, true), SavedWindow(1920, 1200, true).fitTo(1280, 720))
        assertEquals(SavedWindow(800, 600), SavedWindow(800, 600).fitTo(1280, 720))
        assertEquals(SavedWindow(1920, 1200), SavedWindow(1920, 1200).fitTo(0, 0), "an unknown screen fits anything")
    }

    @Test
    fun theSizeKeptIsTheOneTheWindowHasWhileNeitherMaximizedNorFullScreen() {
        val floating = SavedWindow(800, 900)
        assertEquals(SavedWindow(800, 900, true), floating.after(WindowPlacement.Maximized, 1920, 1080))
        assertEquals(floating, floating.after(WindowPlacement.Fullscreen, 1920, 1080))
        assertEquals(SavedWindow(700, 950), floating.after(WindowPlacement.Maximized, 1920, 1080).after(WindowPlacement.Floating, 700, 950))
    }

    @Test
    fun theFileIsWrittenWholeAndReadBack() {
        val file = File(Files.createTempDirectory("xta-window").toFile(), "data/window.ini")
        val windows = SavedWindowFile(file)
        assertEquals(SavedWindow.FIRST, windows.read())
        windows.write(SavedWindow(500, 900, true))
        assertEquals(SavedWindow(500, 900, true), windows.read())
        assertEquals(listOf("window.ini"), file.parentFile.list()!!.toList())
    }

    @Test
    fun onlyKnownLinesAreMessages() {
        assertEquals(InstanceMessage.RAISE, InstanceMessage.parse("raise"))
        assertEquals(InstanceMessage.RAISE, InstanceMessage.parse(" raise\n"))
        assertNull(InstanceMessage.parse("RAISE"))
        assertNull(InstanceMessage.parse("open https://x.com"))
        assertNull(InstanceMessage.parse(""))
        assertNull(InstanceMessage.parse(null))
    }

    @Test
    fun aSecondStartAsksTheFirstToRaiseItsWindowAndEnds() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(SingleInstance.claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(SingleInstance.claim(folder))
            assertEquals(InstanceMessage.RAISE, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aStartThatTheEndingCopyDoesNotAnswerRunsOnceItHasEnded() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val ending = assertNotNull(SingleInstance.claim(folder))
        // The first copy never answers (it is ending) and lets go of its lock a moment later.
        Thread { Thread.sleep(500); ending.close() }.start()
        val next = assertNotNull(SingleInstance.claim(folder), "the start runs once the ending copy is gone")
        next.close()
    }
}
