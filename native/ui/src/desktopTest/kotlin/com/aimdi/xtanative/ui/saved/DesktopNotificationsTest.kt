package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopNotificationsTest {
    @Test
    fun onlyTheEndOfAPieceOfWorkIsTold() {
        assertTrue(DesktopNotifications.tells(KeepNotice.SavedTo("Pictures/XTA", "file:///a.jpg", "a.jpg")))
        assertTrue(DesktopNotifications.tells(KeepNotice.DownloadFailed))
        assertTrue(DesktopNotifications.tells(KeepNotice.Batch(3, 4)))
        assertFalse(DesktopNotifications.tells(KeepNotice.Downloading), "work under way stays in the window")
        assertFalse(DesktopNotifications.tells(KeepNotice.LikesStayHere))
        assertFalse(DesktopNotifications.tells(KeepNotice.NoteSaved))
    }

    /** A bus of the test's own, never the desktop's; null without dbus-daemon. */
    private fun privateBus(): Pair<Process, File>? {
        val folder = Files.createTempDirectory("xta-dbus").toFile()
        val socket = File(folder, "bus")
        File(folder, "bus.conf").writeText(
            """
            <busconfig><type>session</type><listen>unix:path=${socket.path}</listen><auth>EXTERNAL</auth>
            <policy context="default"><allow send_destination="*" eavesdrop="true"/><allow eavesdrop="true"/><allow own="*"/></policy></busconfig>
            """.trimIndent(),
        )
        val daemon = runCatching { ProcessBuilder("dbus-daemon", "--config-file=${File(folder, "bus.conf").path}", "--nofork", "--nopidfile").start() }.getOrNull() ?: return null
        repeat(50) {
            if (socket.exists()) return daemon to socket
            Thread.sleep(100)
        }
        daemon.destroy()
        return null
    }

    @Test
    fun aNotificationReachesTheDesktopAndItsClickTheWindow() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            // A notification server of the test's own.
            val server = assertNotNull(DBusConnection.connect(socket))
            val calls = LinkedBlockingQueue<DBusMessage>()
            server.onCall = { call ->
                calls.add(call)
                server.reply(call, "u", listOf(7L))
            }
            server.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(DesktopNotifications.NAME, 4L))
            val raised = CountDownLatch(1)
            val notifications = DesktopNotifications({ DBusConnection.connect(socket) }, onMain = { it() })
            notifications.onActivate = { raised.countDown() }
            notifications.notify("XTA Native", "Saved to Pictures/XTA")
            notifications.settle()
            val call = assertNotNull(calls.poll(5, TimeUnit.SECONDS))
            assertEquals("Notify", call.member)
            assertEquals(listOf("XTA Native", "Saved to Pictures/XTA"), listOf(call.body[3], call.body[4]))
            assertEquals(listOf("default", ""), call.body[5])
            server.signal(DesktopNotifications.PATH, DesktopNotifications.NAME, "ActionInvoked", "us", listOf(8L, "default"))
            server.signal(DesktopNotifications.PATH, DesktopNotifications.NAME, "ActionInvoked", "us", listOf(7L, "default"))
            assertTrue(raised.await(5, TimeUnit.SECONDS), "a click on its own notification raises the window")
            server.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aFinishedDownloadsNotificationShowsItInItsFolder() {
        assertEquals(listOf("default", ""), DesktopNotifications.actionsFor(null, "Show in folder"))
        assertEquals(listOf("default", "", "show-in-folder", "Show in folder"), DesktopNotifications.actionsFor("file:///a.jpg", "Show in folder"))
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val server = assertNotNull(DBusConnection.connect(socket))
            val calls = LinkedBlockingQueue<DBusMessage>()
            server.onCall = { call ->
                calls.add(call)
                server.reply(call, "u", listOf(9L))
            }
            server.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(DesktopNotifications.NAME, 4L))
            val shown = LinkedBlockingQueue<String>()
            val notifications = DesktopNotifications({ DBusConnection.connect(socket) }, onMain = { it() })
            notifications.onShowInFolder = { shown.add(it) }
            notifications.notify("XTA Native", "Saved to Pictures/XTA", folder = "file:///home/me/Pictures/XTA/a.jpg", folderLabel = "Show in folder")
            notifications.settle()
            val call = assertNotNull(calls.poll(5, TimeUnit.SECONDS))
            assertEquals(listOf("default", "", "show-in-folder", "Show in folder"), call.body[5])
            server.signal(DesktopNotifications.PATH, DesktopNotifications.NAME, "ActionInvoked", "us", listOf(9L, "show-in-folder"))
            assertEquals("file:///home/me/Pictures/XTA/a.jpg", shown.poll(5, TimeUnit.SECONDS))
            server.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
