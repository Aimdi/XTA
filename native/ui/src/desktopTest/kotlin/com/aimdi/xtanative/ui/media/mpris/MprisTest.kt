package com.aimdi.xtanative.ui.media.mpris

import com.aimdi.xtanative.ui.media.AudioSessionBinding
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MprisTest {
    @Test
    fun aMessageReadsBackAsItWasWritten() {
        val message = DBusMessage(
            DBusType.SIGNAL,
            path = Mpris.PATH,
            iface = Mpris.PROPERTIES,
            member = "PropertiesChanged",
            signature = "sa{sv}as",
            body = listOf(
                Mpris.PLAYER,
                mapOf(
                    "PlaybackStatus" to Variant("s", "Playing"),
                    "Metadata" to Variant("a{sv}", mapOf("mpris:length" to Variant("x", 61_000_000L), "xesam:artist" to Variant("as", listOf("Ä", "b")))),
                    "Rate" to Variant("d", 1.5),
                    "CanSeek" to Variant("b", true),
                ),
                emptyList<String>(),
            ),
        )
        val bytes = DBusWire.encode(message, 7)
        assertEquals(bytes.size, DBusWire.length(bytes.copyOf(DBusWire.FIXED)))
        assertEquals(message.copy(serial = 7), DBusWire.decode(bytes))
    }

    @Test
    fun aStructAndAPathTravelAligned() {
        val message = DBusMessage(DBusType.METHOD_CALL, path = "/a", member = "M", signature = "y(xo)ad", body = listOf(3, listOf(-5L, ObjectPath("/b/c")), listOf(0.25, 2.0)))
        assertEquals(message.copy(serial = 1), DBusWire.decode(DBusWire.encode(message, 1)))
        assertEquals(listOf("y", "(xo)", "ad"), DBusWire.split("y(xo)ad"))
        assertEquals(listOf("a{sv}", "as", "a(ya{sv})"), DBusWire.split("a{sv}asa(ya{sv})"))
    }

    @Test
    fun theBusIsFoundAtItsPath() {
        assertEquals(File("/run/user/1000/bus"), DBusAddress.socketOf("unix:path=/run/user/1000/bus", null))
        assertEquals(File("/tmp/a b"), DBusAddress.socketOf("unix:abstract=/tmp/x;unix:guid=1,path=/tmp/a%20b", null))
        assertNull(DBusAddress.socketOf("unix:abstract=/tmp/dbus-1", null))
        assertNull(DBusAddress.socketOf("tcp:host=localhost,port=1", null))
        assertNull(DBusAddress.socketOf(null, Files.createTempDirectory("xta-run").toString()))
    }

    private val reading = MprisNow(track = 1, title = "A post", playing = true)
    private val video = MprisNow(track = 2, playing = true, positionMs = 5_000, durationMs = 60_000, canPlay = true, canPause = true, canSeek = true)

    private fun call(iface: String, member: String, signature: String = "", vararg body: Any?) =
        DBusMessage(DBusType.METHOD_CALL, serial = 1, path = Mpris.PATH, iface = iface, member = member, signature = signature, body = body.toList())

    @Test
    fun thePlayKeyPausesAVideoAndStopsAReading() {
        assertEquals(MprisAction.Pause, Mpris.answer(call(Mpris.PLAYER, "PlayPause"), video, "X").action)
        assertEquals(MprisAction.Play, Mpris.answer(call(Mpris.PLAYER, "PlayPause"), video.copy(playing = false), "X").action)
        assertEquals(MprisAction.Stop, Mpris.answer(call(Mpris.PLAYER, "PlayPause"), reading, "X").action)
        assertEquals(MprisAction.Stop, Mpris.answer(call(Mpris.PLAYER, "Pause"), reading, "X").action)
        assertNull(Mpris.answer(call(Mpris.PLAYER, "Play"), reading, "X").action)
        assertEquals(MprisAction.Raise, Mpris.answer(call(Mpris.ROOT, "Raise"), reading, "X").action)
    }

    @Test
    fun seekingStaysInTheTrack() {
        assertEquals(MprisAction.Seek(15_000), Mpris.answer(call(Mpris.PLAYER, "Seek", "x", 10_000_000L), video, "X").action)
        assertEquals(MprisAction.Seek(0), Mpris.answer(call(Mpris.PLAYER, "Seek", "x", -10_000_000L), video, "X").action)
        assertEquals(MprisAction.Stop, Mpris.answer(call(Mpris.PLAYER, "Seek", "x", 100_000_000L), video, "X").action)
        assertNull(Mpris.answer(call(Mpris.PLAYER, "Seek", "x", 1_000_000L), reading, "X").action)
        assertEquals(MprisAction.Seek(30_000), Mpris.answer(call(Mpris.PLAYER, "SetPosition", "ox", Mpris.trackId(video), 30_000_000L), video, "X").action)
        assertNull(Mpris.answer(call(Mpris.PLAYER, "SetPosition", "ox", Mpris.trackId(reading), 30_000_000L), video, "X").action)
    }

    @Test
    fun propertiesSayWhatPlays() {
        val status = Mpris.answer(call(Mpris.PROPERTIES, "Get", "ss", Mpris.PLAYER, "PlaybackStatus"), video.copy(playing = false), "X")
        assertEquals(listOf(Variant("s", "Paused")), status.body)
        val all = Mpris.answer(call(Mpris.PROPERTIES, "GetAll", "s", Mpris.ROOT), video, "XTA Native").body.single() as Map<*, *>
        assertEquals(Variant("s", "XTA Native"), all["Identity"])
        assertEquals(Variant("s", "com.aimdi.xtanative"), all["DesktopEntry"])
        assertNotNull(Mpris.answer(call(Mpris.PROPERTIES, "Get", "ss", Mpris.PLAYER, "Nope"), video, "X").error)
        assertNotNull(Mpris.answer(call(Mpris.PROPERTIES, "Set", "ssv", Mpris.PLAYER, "Volume", Variant("d", 0.5)), video, "X").error)
        assertEquals(Variant("x", 60_000_000L), Mpris.metadata(video)["mpris:length"])
        assertEquals(Variant("s", "A post"), Mpris.metadata(reading)["xesam:title"])
    }

    @Test
    fun onlyWhatControlsShowIsSignalled() {
        assertEquals(emptyMap(), Mpris.changed(video, video.copy(positionMs = 9_000)))
        assertEquals(setOf("PlaybackStatus"), Mpris.changed(video, video.copy(playing = false)).keys)
    }

    @Test
    fun aJumpIsWhatTheTimeSinceDoesNotExplain() {
        assertEquals(false, Mpris.jumped(video, elapsedMs = 1_000, positionMs = 6_000), "played on")
        assertEquals(false, Mpris.jumped(video, elapsedMs = 250, positionMs = 6_400), "a late report")
        assertEquals(true, Mpris.jumped(video, elapsedMs = 250, positionMs = 30_000), "the scrubber forward")
        assertEquals(true, Mpris.jumped(video, elapsedMs = 250, positionMs = 1_000), "back")
        assertEquals(true, Mpris.jumped(video.copy(positionMs = 59_900), elapsedMs = 200, positionMs = 0), "a loop to the start")
        assertEquals(false, Mpris.jumped(video.copy(playing = false), elapsedMs = 60_000, positionMs = 5_000), "paused, time passes")
        assertEquals(true, Mpris.jumped(video.copy(playing = false), elapsedMs = 60_000, positionMs = 20_000), "seeked while paused")
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

    @Test
    fun controlsOnABusReachWhatIsBound() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val pressed = mutableListOf<String>()
            val service = MprisService({ DBusConnection.connect(socket) }, onMain = { it() })
            val client = assertNotNull(DBusConnection.connect(socket))
            fun owned() = client.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "NameHasOwner", "s", listOf(Mpris.NAME))?.body?.single()
            assertEquals(false, owned())
            val binding = AudioSessionBinding(onPlay = { pressed += "play" }, onPause = { pressed += "pause" }, onStop = { pressed += "stop" }, onSeek = { pressed += "seek $it" })
            service.bind("video", title = "A video", artist = null, binding = binding)
            service.update("video", playing = true, positionMs = 1_000, durationMs = 10_000)
            service.settle()
            assertEquals(true, owned())
            val status = client.call(Mpris.NAME, Mpris.PATH, Mpris.PROPERTIES, "Get", "ss", listOf(Mpris.PLAYER, "PlaybackStatus"))
            assertEquals(listOf(Variant("s", "Playing")), status?.body)
            assertEquals(DBusType.METHOD_RETURN, client.call(Mpris.NAME, Mpris.PATH, Mpris.PLAYER, "PlayPause")?.type)
            assertEquals(DBusType.METHOD_RETURN, client.call(Mpris.NAME, Mpris.PATH, Mpris.PLAYER, "Seek", "x", listOf(2_000_000L))?.type)
            assertEquals(DBusType.ERROR, client.call(Mpris.NAME, Mpris.PATH, Mpris.ROOT, "Quit")?.type)
            assertEquals(listOf("pause", "seek 3000"), pressed)
            service.clear("someone else")
            service.settle()
            assertEquals(true, owned())
            service.clear("video")
            service.settle()
            assertEquals(false, owned())
            client.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun anInAppSeekIsSignalledButPlayingOnIsNot() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            var clock = 0L
            val service = MprisService({ DBusConnection.connect(socket) }, onMain = { it() }, clockMs = { clock })
            val client = assertNotNull(DBusConnection.connect(socket))
            val seeked = java.util.concurrent.LinkedBlockingQueue<Long>()
            client.onSignal = { if (it.member == "Seeked") seeked += it.body.single() as Long }
            client.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf("type='signal',interface='${Mpris.PLAYER}'"))
            val binding = AudioSessionBinding(onPause = {}, onPlay = {}, onStop = {}, onSeek = {})
            service.bind("video", title = "A video", artist = null, binding = binding)
            service.update("video", playing = true, positionMs = 1_000, durationMs = 60_000)
            service.settle()
            clock += 500
            service.update("video", playing = true, positionMs = 1_500, durationMs = 60_000)
            clock += 500
            service.update("video", playing = true, positionMs = 40_000, durationMs = 60_000)
            service.settle()
            assertEquals(40_000_000L, seeked.poll(3, TimeUnit.SECONDS), "the scrubber's seek")
            clock += 500
            service.update("video", playing = true, positionMs = 0, durationMs = 60_000)
            service.settle()
            assertEquals(0L, seeked.poll(3, TimeUnit.SECONDS), "the loop back to the start")
            // A seek the controls ask for is signalled once, though the player reports the old place for a moment.
            client.call(Mpris.NAME, Mpris.PATH, Mpris.PLAYER, "SetPosition", "ox", listOf(Mpris.trackId(MprisNow(track = 1)), 20_000_000L))
            assertEquals(20_000_000L, seeked.poll(3, TimeUnit.SECONDS))
            clock += 200
            service.update("video", playing = true, positionMs = 600, durationMs = 60_000)
            clock += 200
            service.update("video", playing = true, positionMs = 20_000, durationMs = 60_000)
            clock += 900
            service.update("video", playing = true, positionMs = 20_900, durationMs = 60_000)
            service.settle()
            assertNull(seeked.poll(500, TimeUnit.MILLISECONDS), "nothing more: ${seeked.toList()}")
            client.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun eachClearsOnlyWhatItBound() {
        val service = MprisService({ null }, onMain = { it() })
        val video = Any()
        val reading = Any()
        service.bind(video, "", null, AudioSessionBinding(onStop = {}))
        service.bind(reading, "A post", null, AudioSessionBinding(onStop = {}))
        service.update(video, playing = false, positionMs = 0, durationMs = null)
        service.clear(video)
        assertEquals(true, service.holds(reading), "the reading bound last keeps the keys")
        service.clear(reading)
        assertEquals(false, service.holds(reading))
    }

    @Test
    fun aVideosArtworkIsAFileOnDisk() {
        val art = MprisNow(track = 3, title = "Async validation", artist = "Kamil", artUrl = "file:/tmp/a.jpg")
        assertEquals(Variant("s", "file:/tmp/a.jpg"), Mpris.metadata(art)["mpris:artUrl"])
        assertEquals(Variant("as", listOf("Kamil")), Mpris.metadata(art)["xesam:artist"])
        assertNull(Mpris.metadata(reading)["mpris:artUrl"])
        assertNull(CachedArt.fileUrl(null))
    }

    @Test
    fun theTrayFollowsTheSessionAndPlaysOrPausesIt() {
        val service = MprisService({ null }, onMain = { it() })
        val asked = mutableListOf<String>()
        assertNull(service.session.value)
        service.playPause()
        service.bind("A reading", null, AudioSessionBinding(onPlay = { asked += "play" }, onPause = { asked += "pause" }, onStop = { asked += "stop" }))
        service.update(true, 0, null)
        assertEquals(true, service.session.value?.playing)
        service.playPause()
        service.update(false, 0, null)
        service.playPause()
        assertEquals(listOf("pause", "play"), asked)
        service.clear()
        assertNull(service.session.value)
    }
}
