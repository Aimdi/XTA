package com.aimdi.xtanative.ui.chrome.tray

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.DBusWire
import com.aimdi.xtanative.ui.media.mpris.MprisNow
import com.aimdi.xtanative.ui.media.mpris.ObjectPath
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrayTest {
    private val words = TrayWords("XTA", "Show window", "Hide window", "Quit XTA", "New search", "Play", "Pause")
    private val shown = TrayNow(words, windowShown = true)
    private val pixmaps = listOf(listOf(1, 1, byteArrayOf(-1, 0, 0, -1)))

    private fun call(path: String, iface: String, member: String, signature: String = "", vararg body: Any?) =
        DBusMessage(DBusType.METHOD_CALL, serial = 1, path = path, iface = iface, member = member, signature = signature, body = body.toList())

    private fun menu(member: String, signature: String = "", vararg body: Any?) = call(StatusNotifier.MENU_PATH, StatusNotifier.MENU, member, signature, *body)

    @Test
    fun theMenuSaysHideWhileTheWindowIsShownAndShowWhileHidden() {
        val answer = StatusNotifier.answer(menu("GetLayout", "iias", 0, -1, emptyList<String>()), shown, "", pixmaps)
        assertEquals("u(ia{sv}av)", answer.signature)
        val (_, root, children) = answer.body[1] as List<*>
        assertEquals(Variant("s", "submenu"), (root as Map<*, *>)["children-display"])
        val labels = (children as List<*>).map { ((it as Variant).value as List<*>)[1] as Map<*, *> }
        assertEquals(Variant("s", "Hide window"), labels[0]["label"])
        assertEquals(Variant("s", "New search"), labels[1]["label"])
        assertEquals(Variant("s", "separator"), labels[2]["type"])
        assertEquals(Variant("s", "Quit XTA"), labels[3]["label"])
        val hidden = StatusNotifier.answer(menu("GetProperty", "is", StatusNotifier.TOGGLE_ID, "label"), shown.copy(windowShown = false), "", pixmaps)
        assertEquals(listOf(Variant("s", "Show window")), hidden.body)
        // Depth 0: the root alone.
        val root0 = StatusNotifier.answer(menu("GetLayout", "iias", 0, 0, emptyList<String>()), shown, "", pixmaps).body[1] as List<*>
        assertEquals(emptyList<Any>(), root0[2])
    }

    @Test
    fun clicksAskWhatTheirItemSays() {
        assertEquals(TrayAction.TOGGLE, StatusNotifier.answer(menu("Event", "isvu", 1, "clicked", Variant("i", 0), 0L), shown, "", pixmaps).action)
        assertEquals(TrayAction.QUIT, StatusNotifier.answer(menu("Event", "isvu", 3, "clicked", Variant("i", 0), 0L), shown, "", pixmaps).action)
        assertEquals(TrayAction.NEW_SEARCH, StatusNotifier.answer(menu("Event", "isvu", StatusNotifier.SEARCH_ID, "clicked", Variant("i", 0), 0L), shown, "", pixmaps).action)
        // The media item answers only while it is shown.
        assertNull(StatusNotifier.answer(menu("Event", "isvu", StatusNotifier.MEDIA_ID, "clicked", Variant("i", 0), 0L), shown, "", pixmaps).action)
        val playing = shown.copy(media = TrayMedia.PAUSE)
        assertEquals(TrayAction.PLAY_PAUSE, StatusNotifier.answer(menu("Event", "isvu", StatusNotifier.MEDIA_ID, "clicked", Variant("i", 0), 0L), playing, "", pixmaps).action)
        assertNull(StatusNotifier.answer(menu("Event", "isvu", 3, "hovered", Variant("i", 0), 0L), shown, "", pixmaps).action)
        assertNull(StatusNotifier.answer(menu("Event", "isvu", 2, "clicked", Variant("i", 0), 0L), shown, "", pixmaps).action)
        val group = StatusNotifier.answer(menu("EventGroup", "a(isvu)", listOf(listOf(9, "clicked", Variant("i", 0), 0L), listOf(1, "clicked", Variant("i", 0), 0L))), shown, "", pixmaps)
        assertEquals(TrayAction.TOGGLE, group.action)
        assertEquals(listOf(listOf(9)), group.body)
        val item = { member: String -> StatusNotifier.answer(call(StatusNotifier.ITEM_PATH, StatusNotifier.ITEM, member, "ii", 0, 0), shown, "", pixmaps) }
        assertEquals(TrayAction.SHOW, item("Activate").action)
        assertEquals(TrayAction.SHOW, item("SecondaryActivate").action)
        assertNull(item("ContextMenu").action)
        assertNotNull(item("Nothing").error)
    }

    @Test
    fun anUnderscoreIsNoAccessKey() {
        assertEquals("a__b", StatusNotifier.label("a_b"))
    }

    @Test
    fun theIconIsDrawnAsARGBInNetworkOrder() {
        val sizes = TrayIcon.pixmaps().map { it[0] }
        assertEquals(listOf(16, 22, 24, 32, 48, 64), sizes)
        val (w, h, bytes) = TrayIcon.pixmaps().last()
        assertEquals((w as Int) * (h as Int) * 4, (bytes as ByteArray).size)
        // The middle of the X is drawn, opaque; a corner outside the rounded square is clear.
        val middle = ((32 * 64) + 32) * 4
        assertEquals(-1, bytes[middle].toInt())
        assertEquals(0, bytes[0].toInt())
        // The pixels travel as an 'ay' and come back as its bytes.
        val message = DBusMessage(DBusType.METHOD_RETURN, replySerial = 1, signature = "v", body = listOf(Variant("a(iiay)", listOf(listOf(1, 1, byteArrayOf(-1, 2, 3, 4))))))
        val back = DBusWire.decode(DBusWire.encode(message, 2))!!.body.single() as Variant
        assertEquals(listOf(listOf(1, 1, listOf(255, 2, 3, 4))), back.value)
    }

    @Test
    fun theThemedIconIsNamedOnlyWhereItIsInstalled() {
        val data = Files.createTempDirectory("xta-share").toFile()
        assertEquals("", TrayService.installedIconName(data.path))
        File(data, "icons/hicolor/scalable/apps").apply { mkdirs() }.resolve("${StatusNotifier.ICON_NAME}.svg").writeText("<svg/>")
        assertEquals(StatusNotifier.ICON_NAME, TrayService.installedIconName("/nowhere:${data.path}"))
    }

    /** A bus of the test's own (never the desktop's session bus), when dbus-daemon is installed. */
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

    /** A stand-in for the bar's StatusNotifierWatcher: it keeps what registers, and says whether a host shows them. */
    private class Watcher(socket: File, private val host: Boolean = true) {
        val bus = assertNotNull(DBusConnection.connect(socket))
        val items: MutableList<String> = Collections.synchronizedList(mutableListOf())

        init {
            bus.onCall = { call ->
                when (call.member) {
                    "RegisterStatusNotifierItem" -> {
                        items += call.body.first() as String
                        bus.reply(call)
                    }
                    "Get" -> bus.reply(call, "v", listOf(Variant("b", host)))
                    else -> bus.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
                }
            }
            val owned = bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(StatusNotifier.WATCHER, 4L))
            assertEquals(1L, owned?.body?.first())
        }
    }

    private fun waitFor(what: () -> Boolean) {
        repeat(50) {
            if (what()) return
            Thread.sleep(100)
        }
    }

    @Test
    fun theIconRegistersAndItsMenuAnswersOnABus() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val watcher = Watcher(socket)
            val actions = Collections.synchronizedList(mutableListOf<TrayAction>())
            val tray = TrayService({ DBusConnection.connect(socket) }, onMain = { it() }, iconName = "", pixmaps = pixmaps, pid = 42)
            tray.onAction = { actions += it }
            tray.words(words)
            tray.start()
            tray.settle()
            assertEquals(true, tray.shown.value)
            assertEquals(listOf("org.kde.StatusNotifierItem-42-1"), watcher.items)
            val host = assertNotNull(DBusConnection.connect(socket))
            val name = "org.kde.StatusNotifierItem-42-1"
            val props = host.call(name, StatusNotifier.ITEM_PATH, StatusNotifier.PROPERTIES, "GetAll", "s", listOf(StatusNotifier.ITEM))
            val all = props?.body?.single() as Map<*, *>
            assertEquals(Variant("s", "XTA"), all["Title"])
            assertEquals(Variant("o", ObjectPath(StatusNotifier.MENU_PATH)), all["Menu"])
            assertEquals(Variant("a(iiay)", listOf(listOf(1, 1, listOf(255, 0, 0, 255)))), all["IconPixmap"])
            assertEquals(DBusType.METHOD_RETURN, host.call(name, StatusNotifier.ITEM_PATH, StatusNotifier.ITEM, "Activate", "ii", listOf(0, 0))?.type)
            val layout = host.call(name, StatusNotifier.MENU_PATH, StatusNotifier.MENU, "GetLayout", "iias", listOf(0, -1, emptyList<String>()))
            assertEquals(DBusType.METHOD_RETURN, layout?.type)
            assertEquals(DBusType.METHOD_RETURN, host.call(name, StatusNotifier.MENU_PATH, StatusNotifier.MENU, "Event", "isvu", listOf(3, "clicked", Variant("i", 0), 0L))?.type)
            waitFor { actions.size == 2 }
            assertEquals(listOf(TrayAction.SHOW, TrayAction.QUIT), actions.toList())

            // The window hidden: the menu's revision goes up and it says Show.
            tray.windowShown(false)
            tray.settle()
            val after = host.call(name, StatusNotifier.MENU_PATH, StatusNotifier.MENU, "GetLayout", "iias", listOf(StatusNotifier.TOGGLE_ID, 0, listOf("label")))
            assertTrue((after?.body?.first() as Long) > (layout!!.body.first() as Long))
            assertEquals(Variant("s", "Show window"), ((after.body[1] as List<*>)[1] as Map<*, *>)["label"])

            // The bar goes away: no tray, so closing would quit; it comes back: the icon registers again.
            watcher.bus.close()
            waitFor { !tray.shown.value }
            assertEquals(false, tray.shown.value)
            val again = Watcher(socket)
            waitFor { tray.shown.value }
            assertEquals(true, tray.shown.value)
            assertEquals(listOf(name), again.items)
            host.close()
            again.bus.close()
            tray.stop()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun noTrayWithoutAWatcherOrAHost() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val alone = TrayService({ DBusConnection.connect(socket) }, onMain = { it() }, iconName = "", pixmaps = pixmaps, pid = 7)
            alone.start()
            alone.settle()
            assertEquals(false, alone.shown.value)
            alone.stop()
            val watcher = Watcher(socket, host = false)
            val hostless = TrayService({ DBusConnection.connect(socket) }, onMain = { it() }, iconName = "", pixmaps = pixmaps, pid = 8)
            hostless.start()
            hostless.settle()
            assertEquals(false, hostless.shown.value, "a watcher no bar reads shows nothing")
            hostless.stop()
            watcher.bus.close()
            val nobus = TrayService({ null }, onMain = { it() }, iconName = "", pixmaps = pixmaps, pid = 9)
            nobus.start()
            nobus.settle()
            assertEquals(false, nobus.shown.value)
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun labels(now: TrayNow): List<Any?> {
        val (_, _, children) = StatusNotifier.answer(menu("GetLayout", "iias", 0, -1, emptyList<String>()), now, "", pixmaps).body[1] as List<*>
        return (children as List<*>).map { (((it as Variant).value as List<*>)[1] as Map<*, *>).let { props -> props["label"] ?: props["type"] } }
    }

    @Test
    fun theMediaItemPlaysOrPausesWhileTheAppHasASession() {
        assertEquals(4, labels(shown).size)
        assertEquals(Variant("s", "Pause"), labels(shown.copy(media = TrayMedia.PAUSE))[2])
        assertEquals(Variant("s", "Play"), labels(shown.copy(media = TrayMedia.PLAY))[2])
        assertEquals(Variant("s", "separator"), labels(shown.copy(media = TrayMedia.PLAY))[3])
        assertNull(StatusNotifier.media(null))
        assertEquals(TrayMedia.PAUSE, StatusNotifier.media(MprisNow(playing = true, canPlay = true, canPause = true)))
        assertEquals(TrayMedia.PLAY, StatusNotifier.media(MprisNow(playing = false, canPlay = true, canPause = true)))
        // A reading that can only stop offers no Play/Pause.
        assertNull(StatusNotifier.media(MprisNow(playing = true)))
    }

    @Test
    fun theMenuReadsRightToLeftInALanguageWrittenSo() {
        val direction = { now: TrayNow ->
            StatusNotifier.answer(call(StatusNotifier.MENU_PATH, StatusNotifier.PROPERTIES, "Get", "ss", StatusNotifier.MENU, "TextDirection"), now, "", pixmaps).body.single()
        }
        assertEquals(Variant("s", "ltr"), direction(shown))
        assertEquals(Variant("s", "rtl"), direction(shown.copy(words = words.copy(rtl = true))))
        assertTrue(StatusNotifier.rightToLeft("ar"))
        assertTrue(StatusNotifier.rightToLeft("he"))
        assertFalse(StatusNotifier.rightToLeft("de"))
        assertFalse(StatusNotifier.rightToLeft(""))
    }
}
