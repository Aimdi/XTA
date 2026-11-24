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
    fun theSmallestWindowGrowsWithTheInterfaceSizeButNeverPastTheScreen() {
        assertEquals(360 to 560, SavedWindow.minimum(1.0))
        assertEquals(720 to 1120, SavedWindow.minimum(2.0), "no screen known")
        assertEquals(288 to 448, SavedWindow.minimum(0.8, 1920, 1080))
        assertEquals(450 to 700, SavedWindow.minimum(1.25, 1920, 1080))
        assertEquals(720 to 1050, SavedWindow.minimum(2.0, 1920, 1050), "a 1080p work area")
        assertEquals(700 to 600, SavedWindow.minimum(2.0, 700, 600))
    }

    @Test
    fun aPostWindowIsTheReadingColumnAtTheInterfaceSize() {
        assertEquals(680 to 900, SavedWindow.postWindow(1.0))
        assertEquals(680 to 700, SavedWindow.postWindow(1.0, 1600, 700))
        assertEquals(1020 to 900, SavedWindow.postWindow(1.5, 1920, 1050))
        assertEquals(1280 to 900, SavedWindow.postWindow(2.0, 1280, 1000), "never wider than the screen")
        assertEquals(544 to 900, SavedWindow.postWindow(0.8))
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
    fun thePlaceIsKeptWhileFloatingAndOldFilesHaveNone() {
        val placed = SavedWindow(800, 900, x = 120, y = -40)
        assertEquals(placed, SavedWindow.parse(placed.toIni()))
        assertEquals(SavedWindow(900, 700, true), SavedWindow.parse("[window]\nwidth=900\nheight=700\nmaximized=true\n"), "a file of before has no place")
        assertEquals(SavedWindow(900, 700), SavedWindow.parse("[window]\nwidth=900\nheight=700\nx=10\n"), "half a place is none")
        assertEquals(SavedWindow(900, 700), SavedWindow.parse("[window]\nwidth=900\nheight=700\nx=10\ny=999999\n"))
        assertEquals(SavedWindow(800, 900, true, 120, -40), placed.after(WindowPlacement.Maximized, 1920, 1080, 0, 0), "maximized keeps the floating place")
        assertEquals(SavedWindow(700, 950, false, 300, 200), placed.after(WindowPlacement.Floating, 700, 950, 300, 200))
        assertEquals(SavedWindow(700, 950, false, 120, -40), placed.after(WindowPlacement.Floating, 700, 950), "a place unknown keeps the last")
    }

    @Test
    fun aWindowComesBackOnTheScreenThatShowsItMovedWhollyOntoIt() {
        val left = ScreenArea(0, 30, 1920, 1050)
        val right = ScreenArea(1920, 0, 2560, 1440)
        val screens = listOf(left, right)
        assertEquals(SavedWindow(800, 600, x = 100, y = 100), SavedWindow(800, 600, x = 100, y = 100).placedOn(screens))
        assertEquals(SavedWindow(800, 600, x = 2200, y = 300), SavedWindow(800, 600, x = 2200, y = 300).placedOn(screens), "the second screen")
        assertEquals(SavedWindow(800, 600, x = 1120, y = 30), SavedWindow(800, 600, x = 1300, y = 0).placedOn(screens), "pulled in from under the panel and off the edge")
        assertEquals(SavedWindow(800, 600, x = 3680, y = 840), SavedWindow(800, 600, x = 4000, y = 1200).placedOn(screens), "a window half off the right screen comes back onto it")
        assertEquals(SavedWindow(2560, 1440, x = 1920, y = 0), SavedWindow(3000, 1600, x = 1900, y = 0).placedOn(screens), "sized to the screen it is on")
    }

    @Test
    fun aWindowLeftWhereNoScreenIsNowOpensWhereTheDesktopPutsIt() {
        val laptop = listOf(ScreenArea(0, 0, 1366, 768))
        assertEquals(SavedWindow(800, 600), SavedWindow(800, 600, x = 2200, y = 300).placedOn(laptop), "its screen unplugged")
        assertEquals(SavedWindow(800, 600), SavedWindow(800, 600, x = 1360, y = 760).placedOn(laptop), "only a corner showing")
        assertEquals(SavedWindow(1366, 768), SavedWindow(1920, 1080).placedOn(laptop), "no place, fitted to the primary screen")
        assertEquals(SavedWindow(800, 600, x = 5, y = 5), SavedWindow(800, 600, x = 5, y = 5).placedOn(emptyList()), "no screens known")
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
        assertEquals(InstanceMessage.Raise, InstanceMessage.parse("raise"))
        assertEquals(InstanceMessage.Raise, InstanceMessage.parse(" raise\n"))
        assertNull(InstanceMessage.parse("RAISE"))
        assertEquals(InstanceMessage.Open("https://x.com/a/status/1"), InstanceMessage.parse("open https://x.com/a/status/1\n"))
        assertNull(InstanceMessage.parse("open file:///etc/passwd"))
        assertNull(InstanceMessage.parse("open https://x.com/a b"))
        assertNull(InstanceMessage.parse("open "))
        assertNull(InstanceMessage.parse(""))
        assertNull(InstanceMessage.parse(null))
    }

    @Test
    fun aMessageTravelsAsTheLineItIsReadFrom() {
        val open = InstanceMessage.Open("https://twitter.com/jack")
        assertEquals(open, InstanceMessage.parse(open.line))
        assertEquals(InstanceMessage.Raise, InstanceMessage.parse(InstanceMessage.Raise.line))
    }

    @Test
    fun theLinkAStartIsGivenIsItsFirstWebAddress() {
        assertEquals("https://x.com/jack", InstanceMessage.linkIn(arrayOf("--verbose", "https://x.com/jack", "http://t.co/a")))
        assertEquals("http://t.co/a", InstanceMessage.linkIn(arrayOf("http://t.co/a")))
        assertNull(InstanceMessage.linkIn(arrayOf()))
        assertNull(InstanceMessage.linkIn(arrayOf("x.com/jack", "javascript:alert(1)", "HTTPS://X.COM")))
        assertNull(InstanceMessage.linkIn(arrayOf("https://x.com/" + "a".repeat(InstanceMessage.MAX_URL))))
    }

    @Test
    fun aSecondStartHandsTheFirstItsLink() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(SingleInstance.claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(SingleInstance.claim(folder, InstanceMessage.Open("https://x.com/jack/status/20")))
            assertEquals(InstanceMessage.Open("https://x.com/jack/status/20"), heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aCopyTooOldToOpenLinksIsAskedToRaiseItsWindow() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(SingleInstance.claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        // A copy from before links: it answers "raise" only, and ignores what it does not know.
        first.listen { message -> if (message == InstanceMessage.Raise) heard.add(message) else throw IllegalStateException("unknown line") }
        try {
            assertNull(SingleInstance.claim(folder, InstanceMessage.Open("https://x.com/jack")))
            assertEquals(InstanceMessage.Raise, heard.poll(10, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aSecondStartAsksTheFirstToRaiseItsWindowAndEnds() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(SingleInstance.claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(SingleInstance.claim(folder))
            assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS))
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
