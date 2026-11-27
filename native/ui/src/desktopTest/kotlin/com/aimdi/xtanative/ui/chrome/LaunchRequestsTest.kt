package com.aimdi.xtanative.ui.chrome

import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files a start asks for (`xta-native backup.json`, `--open`), and how they reach the copy running. */
class LaunchRequestsTest {
    private val cwd = File("/home/me/Downloads")

    @Test
    fun filesAreNamedByOpenOrAsArguments() {
        assertEquals(listOf("/home/me/Downloads/feeds.opml"), CommandLine.filesIn(arrayOf("--open", "feeds.opml"), cwd))
        assertEquals(listOf("/tmp/b.json"), CommandLine.filesIn(arrayOf("--open=/tmp/b.json"), cwd))
        assertEquals(listOf("/home/me/a.json"), CommandLine.filesIn(arrayOf("../a.json"), cwd))
        assertEquals(listOf("/home/me/My feeds.opml"), CommandLine.filesIn(arrayOf("file:///home/me/My%20feeds.opml"), cwd), "a file manager's %U")
        assertEquals(listOf("/home/me/a.opml"), CommandLine.filesIn(arrayOf("file://localhost/home/me/a.opml"), cwd))
        assertEquals(emptyList(), CommandLine.filesIn(arrayOf("file://other-host/a.opml", "smb://nas/a.opml", "https://x.com/a", "--minimized"), cwd))
        assertEquals(listOf("/home/me/Downloads/-odd.json"), CommandLine.filesIn(arrayOf("--open", "-odd.json"), cwd))
    }

    @Test
    fun openIsAKnownOption() {
        assertEquals(CommandLine.Answer.RUN, CommandLine.answer(arrayOf("--open", "-odd.json")), "--open's file is no option")
        assertEquals(CommandLine.Answer.RUN, CommandLine.answer(arrayOf("--open=a.json")))
        assertEquals(CommandLine.Answer.UNKNOWN, CommandLine.answer(arrayOf("--opened", "a.json")))
        listOf("--open", "FILE").forEach { assertTrue(it in CommandLine.usage, it) }
    }

    @Test
    fun filesTravelAsLines() {
        val file = InstanceMessage.OpenFile("/home/me/a b.json")
        assertEquals(file, InstanceMessage.parse(file.line))
        assertNull(InstanceMessage.parse("file relative.json"), "only an absolute path")
        assertNull(InstanceMessage.parse("file /a\u0000b"))
        assertTrue(file.raisesWhenUnknown)
        assertFalse(InstanceMessage.Hello.raisesWhenUnknown)
    }

    @Test
    fun aSecondStartHandsTheFirstItsFile() {
        val folder = Files.createTempDirectory("xta-instance").toFile()
        val runtime = Files.createTempDirectory("xta-run").toFile()
        val first = assertNotNull(SingleInstance.claim(folder, InstanceMessage.Raise, runtime))
        val heard = LinkedBlockingQueue<InstanceMessage>()
        first.listen(heard::add)
        try {
            assertNull(SingleInstance.claim(folder, InstanceMessage.OpenFile("/tmp/feeds.opml"), runtime))
            assertEquals(InstanceMessage.OpenFile("/tmp/feeds.opml"), heard.poll(5, TimeUnit.SECONDS))
        } finally {
            first.close()
        }
    }
}
