package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.ObjectPath
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalFilesTest {
    private val pictures = FileFilter("Pictures", setOf("png", "jpg"))

    @Test
    fun aFilterMatchesAnyCase() {
        assertEquals("*.[pP][nN][gG]", FileFilter.glob("png"))
        assertEquals("*.[mM]4[aA]", FileFilter.glob("M4a"))
        assertEquals(listOf("Pictures", listOf(listOf(0L, "*.[jJ][pP][gG]"), listOf(0L, "*.[pP][nN][gG]"))), pictures.toPortal())
        assertTrue(pictures.accepts("A.PNG"))
        assertFalse(pictures.accepts("a.gif"))
        assertFalse(pictures.accepts("png"))
    }

    @Test
    fun theRequestLivesUnderTheConnectionsName() {
        assertEquals("/org/freedesktop/portal/desktop/request/1_42/t", PortalFiles.requestPath(":1.42", "t"))
        assertTrue(PortalFiles.token() != PortalFiles.token())
    }

    @Test
    fun optionsSayWhatIsAsked() {
        val folder = Files.createTempDirectory("xta-portal").toFile()
        val save = PortalFiles.options(FileAsk("Save", FileAskKind.SAVE, name = "a.json", folder = folder), "t")
        assertEquals(Variant("s", "t"), save["handle_token"])
        assertEquals(Variant("s", "a.json"), save["current_name"])
        assertContentEquals((folder.path + "\u0000").toByteArray(), save["current_folder"]?.value as ByteArray)
        assertNull(save["directory"])
        val open = PortalFiles.options(FileAsk("Open", FileAskKind.OPEN, pictures, name = "ignored", folder = File(folder, "missing")), "t")
        assertNull(open["current_name"], "only a save fills in a name")
        assertNull(open["current_folder"], "a folder that is not there is not offered")
        assertEquals(Variant("(sa(us))", pictures.toPortal()), open["current_filter"])
        assertEquals(Variant("b", true), PortalFiles.options(FileAsk("Folder", FileAskKind.FOLDER), "t")["directory"])
        assertEquals("SaveFile", PortalFiles.method(FileAsk("", FileAskKind.SAVE)))
        assertEquals("OpenFile", PortalFiles.method(FileAsk("", FileAskKind.FOLDER)))
    }

    @Test
    fun foldersNeedAPortalOfVersionThree() {
        assertTrue(PortalFiles.usable(1, FileAsk("", FileAskKind.OPEN)))
        assertFalse(PortalFiles.usable(2, FileAsk("", FileAskKind.FOLDER)))
        assertTrue(PortalFiles.usable(3, FileAsk("", FileAskKind.FOLDER)))
        assertFalse(PortalFiles.usable(null, FileAsk("", FileAskKind.OPEN)))
    }

    private fun response(code: Long, vararg uris: String) = DBusMessage(
        DBusType.SIGNAL,
        path = "/r",
        iface = PortalFiles.REQUEST,
        member = "Response",
        signature = "ua{sv}",
        body = listOf(code, mapOf("uris" to Variant("as", uris.toList()))),
    )

    @Test
    fun theResponseSaysHowItEnded() {
        assertEquals(FileChoice.Picked(File("/home/a b/c.png")), PortalFiles.result(response(0, "file:///home/a%20b/c.png")))
        assertEquals(FileChoice.Cancelled, PortalFiles.result(response(1)))
        assertEquals(FileChoice.Unavailable, PortalFiles.result(response(2)))
        assertEquals(FileChoice.Unavailable, PortalFiles.result(response(0, "smb://host/c.png")), "only a file here can be read")
        assertEquals(FileChoice.Unavailable, PortalFiles.result(response(0)))
    }

    private fun chooser(choice: FileChoice, fallback: MutableList<FileAsk>) = FileChooser(
        portal = { _, _ -> choice },
        fallback = { ask, _ -> fallback += ask; File("/fallback") },
        owner = { null },
    )

    @Test
    fun theAppsOwnDialogOpensOnlyWhereNoPortalAsked() = runBlocking {
        val asked = mutableListOf<FileAsk>()
        val ask = FileAsk("Open", FileAskKind.OPEN)
        assertEquals(File("/a"), chooser(FileChoice.Picked(File("/a")), asked).choose(ask))
        assertNull(chooser(FileChoice.Cancelled, asked).choose(ask), "a reader who closed the portal's dialog is not asked again")
        assertEquals(emptyList(), asked)
        assertEquals(File("/fallback"), chooser(FileChoice.Unavailable, asked).choose(ask))
        assertEquals(listOf(ask), asked)
    }

    @Test
    fun oneChooserOpensAtATime() = runBlocking {
        val open = CompletableDeferred<Unit>()
        val release = CompletableDeferred<FileChoice>()
        val chooser = FileChooser(portal = { _, _ -> open.complete(Unit); release.await() }, fallback = { _, _ -> null }, owner = { null })
        val first = async(Dispatchers.Default) { chooser.choose(FileAsk("", FileAskKind.OPEN)) }
        open.await()
        assertNull(chooser.choose(FileAsk("", FileAskKind.OPEN)))
        release.complete(FileChoice.Picked(File("/a")))
        assertEquals(File("/a"), first.await())
        val again = FileChooser(portal = { _, _ -> FileChoice.Picked(File("/b")) }, fallback = { _, _ -> null }, owner = { null })
        assertEquals(File("/b"), again.choose(FileAsk("", FileAskKind.OPEN)))
    }

    @Test
    fun noWindowIsAnEmptyParent() {
        assertEquals("", ParentWindow.handle(null))
        assertEquals("x11:2a00007", ParentWindow.x11(0x2a00007))
    }

    @Test
    fun theDownloadsFolderIsXdgs() {
        val home = File("/home/r")
        assertEquals(File("/data/dl"), XdgFolders.downloads(home) { if (it == "XDG_DOWNLOAD_DIR") "/data/dl" else null })
        assertEquals(File(home, "Downloads"), XdgFolders.downloads(home) { if (it == "XDG_CONFIG_HOME") "/nonexistent" else null })
    }

    /** How the fake portal answers. */
    private enum class Mode { PICK, CANCEL, OTHER, EARLY, OLD_HANDLE, SILENT_DIALOG, NO_REPLY }

    /** A file chooser portal of the test's own on [socket], recording what it was asked. */
    private class FakePortal(socket: File, private val mode: Mode, private val version: Long = 4) : AutoCloseable {
        val bus = DBusConnection.connect(socket)!!
        val asked = CopyOnWriteArrayList<DBusMessage>()
        val closed = CopyOnWriteArrayList<String>()
        val opened = CompletableDeferred<String>()

        init {
            bus.onCall = ::answer
            bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalFiles.PORTAL, 4L))
        }

        private fun answer(call: DBusMessage) {
            when {
                call.iface == PortalFiles.PROPERTIES && call.member == "Get" -> bus.reply(call, "v", listOf(Variant("u", version)))
                call.iface == PortalFiles.REQUEST && call.member == "Close" -> closed += call.path
                call.iface == PortalFiles.CHOOSER -> chooser(call)
                else -> bus.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
            }
        }

        private fun chooser(call: DBusMessage) {
            asked += call
            val token = ((call.body[2] as Map<*, *>)["handle_token"] as Variant).value as String
            val handle = if (mode == Mode.OLD_HANDLE) "${PortalFiles.PATH}/request/old/1" else PortalFiles.requestPath(call.sender!!, token)
            opened.complete(handle)
            fun respond(code: Long) = bus.signal(handle, PortalFiles.REQUEST, "Response", "ua{sv}", listOf(code, mapOf("uris" to Variant("as", listOf("file:///tmp/picked.png")))))
            when (mode) {
                Mode.NO_REPLY -> return
                Mode.EARLY -> respond(0).also { bus.reply(call, "o", listOf(ObjectPath(handle))) }
                Mode.SILENT_DIALOG -> bus.reply(call, "o", listOf(ObjectPath(handle)))
                else -> {
                    bus.reply(call, "o", listOf(ObjectPath(handle)))
                    respond(
                        when (mode) {
                            Mode.CANCEL -> 1
                            Mode.OTHER -> 2
                            else -> 0
                        },
                    )
                }
            }
        }

        override fun close() = bus.close()
    }

    /** A bus of the test's own with no service folders (never the desktop's session bus, whose portal would open a real dialog), when dbus-daemon is installed. */
    private fun privateBus(): Pair<Process, File>? {
        val folder = Files.createTempDirectory("xta-dbus").toFile()
        val socket = File(folder, "bus")
        val config = File(folder, "bus.conf")
        config.writeText(
            """
            <busconfig>
              <type>session</type>
              <listen>unix:path=${socket.path}</listen>
              <auth>EXTERNAL</auth>
              <policy context="default"><allow send_destination="*" eavesdrop="true"/><allow eavesdrop="true"/><allow own="*"/></policy>
            </busconfig>
            """.trimIndent(),
        )
        val daemon = runCatching { ProcessBuilder("dbus-daemon", "--config-file=${config.path}", "--nofork", "--nopidfile").redirectErrorStream(true).start() }.getOrNull() ?: return null
        repeat(50) {
            if (socket.exists()) return daemon to socket
            Thread.sleep(100)
        }
        daemon.destroy()
        return null
    }

    private fun onBus(test: suspend (File) -> Unit) {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            runBlocking { withTimeout(20_000) { test(socket) } }
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private val open = FileAsk("Open", FileAskKind.OPEN, pictures)

    private suspend fun askOn(socket: File, mode: Mode, ask: FileAsk = open, version: Long = 4, callMs: Long = 5_000): Pair<FileChoice, FakePortal> =
        FakePortal(socket, mode, version).use { portal -> PortalFileChooser({ DBusConnection.connect(socket) }, callMs).ask(ask, "x11:1f") to portal }

    @Test
    fun aPortalPicksTheFile() = onBus { socket ->
        val (choice, portal) = askOn(socket, Mode.PICK)
        assertEquals(FileChoice.Picked(File("/tmp/picked.png")), choice)
        val call = portal.asked.single()
        assertEquals("OpenFile", call.member)
        assertEquals(listOf("x11:1f", "Open"), call.body.take(2))
        assertEquals(emptyList(), portal.closed)
    }

    @Test
    fun aCancelledPortalIsCancelledAndAnotherAnswerUnavailable() = onBus { socket ->
        assertEquals(FileChoice.Cancelled, askOn(socket, Mode.CANCEL).first)
        assertEquals(FileChoice.Unavailable, askOn(socket, Mode.OTHER).first)
    }

    @Test
    fun aResponseBeforeTheReplyIsKept() = onBus { socket ->
        assertEquals(FileChoice.Picked(File("/tmp/picked.png")), askOn(socket, Mode.EARLY).first)
    }

    @Test
    fun anOldPortalsOwnHandleIsFollowed() = onBus { socket ->
        assertEquals(FileChoice.Picked(File("/tmp/picked.png")), askOn(socket, Mode.OLD_HANDLE).first)
    }

    @Test
    fun noPortalOnTheBusIsUnavailable() = onBus { socket ->
        assertEquals(FileChoice.Unavailable, PortalFileChooser({ DBusConnection.connect(socket) }, 5_000).ask(open, ""))
        assertEquals(FileChoice.Unavailable, PortalFileChooser({ null }).ask(open, ""))
    }

    @Test
    fun anOldPortalAsksForNoFolder() = onBus { socket ->
        val (choice, portal) = askOn(socket, Mode.PICK, FileAsk("Folder", FileAskKind.FOLDER), version = 2)
        assertEquals(FileChoice.Unavailable, choice)
        assertEquals(emptyList(), portal.asked)
    }

    @Test
    fun aCallUnansweredClosesTheRequest() = onBus { socket ->
        FakePortal(socket, Mode.NO_REPLY).use { portal ->
            assertEquals(FileChoice.Unavailable, PortalFileChooser({ DBusConnection.connect(socket) }, 300).ask(open, ""))
            repeat(100) { if (portal.closed.isEmpty()) Thread.sleep(20) }
            assertEquals(listOf(portal.opened.await()), portal.closed)
        }
    }

    @Test
    fun aChooserGivenUpClosesItsDialog() = onBus { socket ->
        coroutineScope {
            FakePortal(socket, Mode.SILENT_DIALOG).use { portal ->
                val asking = async(Dispatchers.IO) { PortalFileChooser({ DBusConnection.connect(socket) }, 5_000).ask(open, "") }
                val handle = portal.opened.await()
                Thread.sleep(200)
                asking.cancel()
                repeat(100) { if (portal.closed.isEmpty()) Thread.sleep(20) }
                assertEquals(listOf(handle), portal.closed)
            }
        }
    }
}
