package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun aNewPostWindowOpensWhollyOnTheWorkArea() {
        val screen = listOf(ScreenArea(0, 0, 1600, 900))
        assertEquals(SavedWindow(680, 810, x = 460, y = 45), SavedWindow.newPostWindow(1.0, screen, 0), "never as tall as the whole work area")
        assertEquals(SavedWindow(680, 810, x = 492, y = 77), SavedWindow.newPostWindow(1.0, screen, 1), "stepped from the one before")
        assertEquals(SavedWindow.newPostWindow(1.0, screen, 0), SavedWindow.newPostWindow(1.0, screen, 6))
        val panel = listOf(ScreenArea(0, 30, 1920, 1050), ScreenArea(1920, 0, 1920, 1080))
        assertEquals(SavedWindow(680, 900, x = 620, y = 105), SavedWindow.newPostWindow(1.0, panel, 0), "below the panel, on the primary screen")
        val small = SavedWindow.newPostWindow(2.0, listOf(ScreenArea(0, 0, 1280, 720)), 5)
        assertEquals(SavedWindow(1280, 648, x = 0, y = 72), small, "a step never pushes it off")
        assertEquals(SavedWindow(680, 900), SavedWindow.newPostWindow(1.0, emptyList(), 3), "no screen known: the desktop places it")
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
        windows.write(SavedWindow(640, 480, false))
        assertEquals(SavedWindow(640, 480, false), windows.read(), "a later write replaces the first whole")
        assertEquals(listOf("window.ini"), file.parentFile.list()!!.toList())
    }

    @Test
    fun onlyKnownLinesAreMessages() {
        assertEquals(InstanceMessage.Raise, InstanceMessage.parse("raise"))
        assertEquals(InstanceMessage.Raise, InstanceMessage.parse(" raise\n"))
        assertNull(InstanceMessage.parse("RAISE"))
        assertEquals(InstanceMessage.Hello, InstanceMessage.parse("hello\n"))
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
        assertEquals(InstanceMessage.Hello, InstanceMessage.parse(InstanceMessage.Hello.line))
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
        val first = assertNotNull(claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(claim(folder, InstanceMessage.Open("https://x.com/jack/status/20")))
            assertEquals(InstanceMessage.Open("https://x.com/jack/status/20"), heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aCopyTooOldToOpenLinksIsAskedToRaiseItsWindow() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        // A copy from before links: it answers "raise" only, and ignores what it does not know.
        first.listen { message -> if (message == InstanceMessage.Raise) heard.add(message) else throw IllegalStateException("unknown line") }
        try {
            assertNull(claim(folder, InstanceMessage.Open("https://x.com/jack")))
            assertEquals(InstanceMessage.Raise, heard.poll(10, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aSecondStartAsksTheFirstToRaiseItsWindowAndEnds() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(claim(folder))
            assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    @Test
    fun aMinimizedSecondStartEndsWithoutRaisingTheWindow() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            val started = System.currentTimeMillis()
            assertNull(claim(folder, InstanceMessage.Hello), "the autostart ends: a copy runs")
            assertTrue(System.currentTimeMillis() - started < 2_000, "answered at once, not waited out")
            assertNull(heard.poll(300, TimeUnit.MILLISECONDS), "the running window is left as it is")
        } finally {
            first.close()
        }
    }

    @Test
    fun theSocketLivesInTheRuntimeFolderNamedAfterTheDataFolder() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(folder))
        try {
            val sockets = File(runtime, "xta-native")
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(sockets.toPath())))
            assertEquals(listOf("instance-${SingleInstance.folderKey(folder)}.socket"), sockets.list()!!.filter { it.contains(SingleInstance.folderKey(folder)) })
            assertTrue(File(folder, "instance.socket").exists(), "the old place beside the lock is served too")
        } finally {
            first.close()
        }
        assertFalse(File(folder, "instance.socket").exists(), "both go when the copy ends")
        assertEquals(SingleInstance.folderKey(folder), SingleInstance.folderKey(File(folder, "../${folder.name}")), "one name for each spelling")
    }

    @Test
    fun copiesOnTwoDataFoldersKeepTheirOwnSockets() {
        val one = Files.createTempDirectory("xta-instance").toFile()
        val other = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(one))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            val second = assertNotNull(claim(other), "another data folder is another copy")
            try {
                // The second copy's start did not take the first one's socket away.
                assertNull(claim(one))
                assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS))
            } finally {
                second.close()
            }
        } finally {
            first.close()
        }
    }

    @Test
    fun aDataFolderTooDeepForASocketIsStillReached() {
        val deep = File(Files.createTempDirectory("xta-instance").toFile(), "d".repeat(60) + "/" + "e".repeat(60)).apply { mkdirs() }
        val first = assertNotNull(claim(deep))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(claim(deep))
            assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
        assertTrue(SingleInstance.socketsFor(deep, null).isEmpty(), "too long beside the lock")
    }

    @Test
    fun aCopyFromBeforeTheMoveIsReachedBesideItsLock() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val heard = LinkedBlockingQueue<String>()
        oldCopy(folder, heard).use {
            assertNull(claim(folder, InstanceMessage.Open("https://x.com/jack")))
            assertTrue("raise" in heard, "asked to raise after the link it does not know")
            assertTrue(heard.all { it == "raise" || it == "open https://x.com/jack" })
        }
    }

    @Test
    fun aMinimizedStartLeavesACopyTooOldForAHelloAlone() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val heard = LinkedBlockingQueue<String>()
        oldCopy(folder, heard).use {
            val started = System.currentTimeMillis()
            val err = System.err
            val said = java.io.ByteArrayOutputStream()
            System.setErr(java.io.PrintStream(said))
            try {
                assertNull(claim(folder, InstanceMessage.Hello), "ends: the lock is held")
            } finally {
                System.setErr(err)
            }
            assertTrue(System.currentTimeMillis() - started < 2_000, "the old copy read the hello and closed: no waiting out")
            assertTrue(heard.none { it == "raise" }, "never asked to raise")
            assertFalse("does not answer" in said.toString(), "nothing said of a copy that runs")
        }
    }

    @Test
    fun aStartFromBeforeTheMoveReachesANewCopyBesideTheLock() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val first = assertNotNull(claim(folder))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            // What a start of 1.0.17 or older does: it asks only beside the lock.
            val channel = java.nio.channels.SocketChannel.open(UnixDomainSocketAddress.of(File(folder, "instance.socket").toPath()))
            channel.use {
                it.write(ByteBuffer.wrap("raise\n".toByteArray()))
                assertEquals("raised", Channels.newReader(it, Charsets.UTF_8).buffered().readLine())
            }
            assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }

    private val runtime: File = Files.createTempDirectory("xta-run").toFile()

    private fun claim(folder: File, message: InstanceMessage = InstanceMessage.Raise) = SingleInstance.claim(folder, message, runtime)

    /** A copy from before the socket moved: the lock held, listening beside it, answering "raise" alone. */
    private fun oldCopy(folder: File, heard: LinkedBlockingQueue<String>): AutoCloseable {
        val lockChannel = FileChannel.open(File(folder, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = lockChannel.lock()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(File(folder, "instance.socket").toPath()))
        Thread {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use { channel ->
                    val line = Channels.newReader(channel, Charsets.UTF_8).buffered().readLine()?.trim().orEmpty()
                    heard.add(line)
                    if (line == "raise") channel.write(ByteBuffer.wrap("raised\n".toByteArray()))
                }
            }
        }.apply { isDaemon = true }.start()
        return AutoCloseable {
            server.close()
            lock.release()
            lockChannel.close()
        }
    }

    @Test
    fun aQuitCopysLastStepLeavesTheSocketOfTheCopyThatTookOver() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val quitting = assertNotNull(claim(folder))
        // Main's order on a quit: close() (socket gone, lock let go), then exitProcess runs the hook.
        quitting.close()
        val next = assertNotNull(claim(folder), "the next start takes over in the gap")
        val heard = LinkedBlockingQueue<InstanceMessage>()
        next.listen(heard::add)
        try {
            quitting.endOnExit()
            assertNull(claim(folder))
            assertEquals(InstanceMessage.Raise, heard.poll(5, TimeUnit.SECONDS), "a third start still reaches the copy that took over")
        } finally {
            next.close()
        }
    }

    @Test
    fun aCopyEndedBySignalStopsAnsweringAtOnce() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val ending = assertNotNull(claim(folder))
        ending.listen { }
        val socket = File(File(runtime, "xta-native"), "instance-${SingleInstance.folderKey(folder)}.socket")
        assertTrue(socket.exists())
        // SIGTERM: the hook runs before close(), the lock still held, and the socket goes with the server.
        ending.endOnExit()
        assertFalse(socket.exists())
        ending.close()
        val next = assertNotNull(claim(folder))
        next.close()
    }

    @Test
    fun aStartThatTheEndingCopyDoesNotAnswerRunsOnceItHasEnded() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val ending = assertNotNull(claim(folder))
        // The first copy never answers (it is ending) and lets go of its lock a moment later.
        Thread { Thread.sleep(500); ending.close() }.start()
        val next = assertNotNull(claim(folder), "the start runs once the ending copy is gone")
        next.close()
    }
}
