package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.ui.DesktopWindowTitle
import com.aimdi.xtanative.ui.WindowTitles
import com.aimdi.xtanative.ui.chrome.tray.StatusNotifier
import com.aimdi.xtanative.ui.chrome.tray.TrayIcon
import com.aimdi.xtanative.ui.chrome.tray.TrayNow
import com.aimdi.xtanative.ui.chrome.tray.TrayService
import com.aimdi.xtanative.ui.chrome.tray.TrayWords
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttentionTest {
    @Test
    fun theTitleCountsUnreadPostsBeforeTheScreensName() {
        assertEquals("(3) XTA Native", WindowTitles.title(WindowTitles.counted(null, 3), "XTA Native"))
        assertEquals("(99+) Search – XTA Native", WindowTitles.title(WindowTitles.counted("Search", 120), "XTA Native"))
        assertEquals("Search", WindowTitles.counted("Search", 0))
    }

    @Test
    fun theWindowInUseNeedsNoCount() {
        DesktopWindowTitle.show("Search")
        DesktopWindowTitle.unread(4)
        DesktopWindowTitle.focused(true)
        assertEquals("Search", DesktopWindowTitle.part.value)
        DesktopWindowTitle.focused(false)
        assertEquals("(4) Search", DesktopWindowTitle.part.value)
        DesktopWindowTitle.unread(0)
        assertEquals("Search", DesktopWindowTitle.part.value)
        DesktopWindowTitle.show(null)
        DesktopWindowTitle.focused(true)
    }

    @Test
    fun theSettingOffSaysNothing() {
        assertEquals(5, DesktopAttention.shown(5, on = true))
        assertEquals(0, DesktopAttention.shown(5, on = false))
        assertEquals(0, DesktopAttention.shown(-1, on = true))
    }

    @Test
    fun theTrayAsksForAttentionWithTheCountAlone() {
        val words = TrayWords(title = "XTA", unread = "${TrayWords.UNREAD_COUNT} unread")
        val plain = listOf(listOf<Any>(1, 1, byteArrayOf(0, 0, 0, 0)))
        val dotted = listOf(listOf<Any>(1, 1, byteArrayOf(-1, 0, 0, 0)))
        val quiet = StatusNotifier.itemProperties(TrayNow(words), "", plain, dotted)
        assertEquals(Variant("s", "Active"), quiet["Status"])
        assertEquals("", ((quiet["ToolTip"]!!.value as List<*>)[3]))
        val lit = StatusNotifier.itemProperties(TrayNow(words, unread = 7), "", plain, dotted)
        assertEquals(Variant("s", "NeedsAttention"), lit["Status"])
        assertEquals(Variant("a(iiay)", dotted), lit["AttentionIconPixmap"])
        assertEquals("7 unread", ((lit["ToolTip"]!!.value as List<*>)[3]))
        assertNotEquals(TrayIcon.argb(TrayIcon.draw(32)).toList(), TrayIcon.argb(TrayIcon.draw(32, attention = true)).toList(), "the attention icon carries its dot")
    }

    @Test
    fun theLauncherIsToldTheCountAndWhetherToShowIt() {
        val (uri, props) = LauncherEntry.update(12)
        assertEquals("application://com.aimdi.xtanative.desktop", uri)
        assertEquals(mapOf("count" to Variant("x", 12L), "count-visible" to Variant("b", true)), props)
        assertEquals(Variant("b", false), (LauncherEntry.update(0)[1] as Map<*, *>)["count-visible"])
    }

    /** A bus of the test's own (never the desktop's session bus), when dbus-daemon is installed. */
    private fun privateBus(): Pair<Process, File>? {
        val folder = Files.createTempDirectory("xta-dbus").toFile()
        val socket = File(folder, "bus")
        val config = File(folder, "bus.conf")
        config.writeText(
            """
            <busconfig><type>session</type><listen>unix:path=${socket.path}</listen><auth>EXTERNAL</auth>
            <policy context="default"><allow send_destination="*" eavesdrop="true"/><allow eavesdrop="true"/><allow own="*"/></policy></busconfig>
            """.trimIndent(),
        )
        val daemon = runCatching { ProcessBuilder("dbus-daemon", "--config-file=${config.path}", "--nofork", "--nopidfile").start() }.getOrNull() ?: return null
        repeat(50) {
            if (socket.exists()) return daemon to socket
            Thread.sleep(100)
        }
        daemon.destroy()
        return null
    }

    private fun listener(socket: File, rule: String): Pair<DBusConnection, MutableList<DBusMessage>> {
        val bus = assertNotNull(DBusConnection.connect(socket))
        // Read while the bus's thread adds to it: a copy-on-write list never throws mid-iteration.
        val heard = CopyOnWriteArrayList<DBusMessage>()
        bus.onSignal = { heard += it }
        bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(rule))
        return bus to heard
    }

    private fun waitFor(what: () -> Boolean) {
        repeat(50) {
            if (what()) return
            Thread.sleep(100)
        }
    }

    @Test
    fun theBadgeAndTheTraySignalOnABus() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val (launcher, updates) = listener(socket, "type='signal',interface='${LauncherEntry.IFACE}'")
            val badge = LauncherBadge { DBusConnection.connect(socket) }
            badge.show(0)
            badge.show(5)
            badge.show(5)
            badge.show(0)
            badge.settle()
            waitFor { updates.size >= 2 }
            assertEquals(2, updates.size, "nothing for a first zero or the same count again")
            assertEquals(LauncherEntry.update(5), updates[0].body)
            assertEquals(LauncherEntry.update(0), updates[1].body)

            val (bar, said) = listener(socket, "type='signal',interface='${StatusNotifier.ITEM}'")
            val tray = TrayService({ DBusConnection.connect(socket) }, onMain = { it() }, iconName = "", pid = 51)
            tray.start()
            tray.settle()
            tray.unread(3)
            tray.settle()
            waitFor { listOf("NewStatus", "NewAttentionIcon", "NewToolTip").all { member -> said.any { it.member == member } } }
            assertEquals(listOf("NeedsAttention"), said.first { it.member == "NewStatus" }.body)
            assertTrue(said.any { it.member == "NewAttentionIcon" } && said.any { it.member == "NewToolTip" })
            tray.stop()
            launcher.close()
            bar.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
