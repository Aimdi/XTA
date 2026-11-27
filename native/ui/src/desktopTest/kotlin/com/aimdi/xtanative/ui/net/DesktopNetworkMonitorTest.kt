package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopNetworkMonitorTest {
    @Test
    fun thePortalsAnswersSayOnlineOrNot() {
        assertFalse(PortalNetwork.online(available = false, connectivity = 4), "no network at all")
        assertFalse(PortalNetwork.online(available = true, connectivity = 1), "the local network only")
        assertTrue(PortalNetwork.online(available = true, connectivity = 2), "limited: a read may get through")
        assertTrue(PortalNetwork.online(available = true, connectivity = 3), "a captive portal")
        assertTrue(PortalNetwork.online(available = true, connectivity = 4))
        assertTrue(PortalNetwork.online(available = true, connectivity = null), "GetConnectivity refused")
        assertEquals(4L, PortalNetwork.number(Variant("u", 4L)))
        assertEquals(4L, PortalNetwork.number(4))
        assertNull(PortalNetwork.number("full"))
    }

    @Test
    fun onlyThePortalsChangedCounts() {
        assertTrue(PortalNetwork.isChanged(DBusMessage(DBusType.SIGNAL, iface = PortalNetwork.IFACE, member = "changed")))
        assertFalse(PortalNetwork.isChanged(DBusMessage(DBusType.SIGNAL, iface = "org.freedesktop.portal.Settings", member = "changed")))
        assertFalse(PortalNetwork.isChanged(DBusMessage(DBusType.SIGNAL, iface = PortalNetwork.IFACE, member = "SettingChanged")))
    }

    @Test
    fun aDefaultRouteOffLoopbackIsAConnection() {
        val header = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n"
        val withDefault = header +
            "eno1\t00000000\t01B2A8C0\t0003\t0\t0\t900\t00000000\t0\t0\t0\n" +
            "eno1\t00B2A8C0\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n"
        val localOnly = header + "eno1\t00B2A8C0\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n"
        val down = header + "eno1\t00000000\t01B2A8C0\t0002\t0\t0\t900\t00000000\t0\t0\t0\n"
        assertTrue(RouteTable.hasDefaultV4(withDefault))
        assertFalse(RouteTable.hasDefaultV4(localOnly))
        assertFalse(RouteTable.hasDefaultV4(down), "a route that is not up")
        assertFalse(RouteTable.hasDefaultV4(header))
        assertFalse(RouteTable.hasDefaultV4(""))

        val zero = "0".repeat(32)
        // The kernel's unreachable default on lo is there on every machine.
        val unreachable = "$zero 00 $zero 00 $zero ffffffff 00000001 00000000 00200200       lo\n"
        val linkLocal = "fe800000000000000000000000000000 40 $zero 00 $zero 00000400 00000007 00000000 00000001     eno1\n"
        val viaRouter = "$zero 00 $zero 00 fe8000000000000006b4fefffed31825 00000400 00000001 00000000 00000003    wlan0\n"
        assertFalse(RouteTable.hasDefaultV6(unreachable + linkLocal))
        assertTrue(RouteTable.hasDefaultV6(unreachable + linkLocal + viaRouter))
    }

    @Test
    fun offlineWaitsOnlineDoesNot() = runBlocking {
        val seen = mutableListOf<Boolean>()
        // A drop shorter than the wait, then one that lasts.
        val source = flow {
            emit(true)
            emit(false)
            delay(100)
            emit(true)
            delay(50)
            emit(false)
            delay(600)
            emit(true)
        }
        source.debouncedOffline(offlineMs = 300).collect { seen += it }
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun backOnlineIsAtOnce() = runBlocking {
        val source = MutableStateFlow(false)
        val values = async { source.debouncedOffline(offlineMs = 100).take(2).toList() }
        delay(300)
        val turned = System.nanoTime()
        source.value = true
        assertEquals(listOf(false, true), withTimeout(5_000) { values.await() })
        assertTrue(System.nanoTime() - turned < TimeUnit.MILLISECONDS.toNanos(90), "online came without the offline wait")
    }

    @Test
    fun withoutAPortalTheRouteTableSays() {
        val looks = AtomicInteger()
        val monitor = DesktopNetworkMonitor(connect = { null }, routes = { looks.incrementAndGet(); false }, pollMs = 50)
        monitor.followNow()
        runBlocking { withTimeout(5_000) { monitor.online.first { !it } } }
        monitor.close()
        assertTrue(looks.get() >= 1)
        val unreadable = DesktopNetworkMonitor(connect = { null }, routes = { null }, pollMs = 50)
        unreadable.followNow()
        Thread.sleep(150)
        assertTrue(unreadable.online.value, "a table that cannot be read leaves the app online")
        unreadable.close()
    }

    /** A bus of the test's own, never the desktop's (no servicedir: nothing real is started); null without dbus-daemon. */
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

    @Test
    fun theMonitorFollowsThePortalsChanged() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            // A portal of the test's own, its network there until it says otherwise.
            val available = AtomicBoolean(true)
            val portal = assertNotNull(DBusConnection.connect(socket))
            portal.onCall = { call ->
                when (call.member) {
                    "GetAvailable" -> portal.reply(call, "b", listOf(available.get()))
                    "GetConnectivity" -> portal.reply(call, "u", listOf(if (available.get()) 4L else 1L))
                    else -> portal.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
                }
            }
            portal.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalNetwork.PORTAL, 4L))
            val polled = AtomicInteger()
            val monitor = DesktopNetworkMonitor({ DBusConnection.connect(socket) }, routes = { polled.incrementAndGet(); true })
            monitor.followNow()
            assertTrue(monitor.online.value)
            available.set(false)
            // changed carries no arguments; the monitor asks again, off the connection's own thread.
            portal.signal(PortalNetwork.PATH, PortalNetwork.IFACE, "changed", "", emptyList())
            runBlocking { withTimeout(5_000) { monitor.online.first { !it } } }
            available.set(true)
            portal.signal(PortalNetwork.PATH, PortalNetwork.IFACE, "changed", "", emptyList())
            runBlocking { withTimeout(5_000) { monitor.online.first { it } } }
            assertEquals(0, polled.get(), "with a portal, the route table is not looked at")
            monitor.close()
            portal.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun meteredIsThePortalsWordAndNothingElse() {
        assertTrue(PortalNetwork.metered(true))
        assertTrue(PortalNetwork.metered(Variant("b", true)))
        assertFalse(PortalNetwork.metered(false))
        assertFalse(PortalNetwork.metered(null), "GetMetered refused: not metered")
        assertFalse(PortalNetwork.metered("yes"))
    }

    @Test
    fun theMonitorFollowsThePortalsMetered() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val metered = AtomicBoolean(false)
            val portal = assertNotNull(DBusConnection.connect(socket))
            portal.onCall = { call ->
                when (call.member) {
                    "GetAvailable" -> portal.reply(call, "b", listOf(true))
                    "GetConnectivity" -> portal.reply(call, "u", listOf(4L))
                    "GetMetered" -> portal.reply(call, "b", listOf(metered.get()))
                    else -> portal.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
                }
            }
            portal.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalNetwork.PORTAL, 4L))
            val monitor = DesktopNetworkMonitor({ DBusConnection.connect(socket) }, routes = { true })
            monitor.followNow()
            assertFalse(monitor.metered.value)
            metered.set(true)
            portal.signal(PortalNetwork.PATH, PortalNetwork.IFACE, "changed", "", emptyList())
            runBlocking { withTimeout(5_000) { monitor.metered.first { it } } }
            assertTrue(monitor.online.value, "a metered connection is still one")
            monitor.close()
            portal.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aBusWithoutThePortalFallsBackToTheRouteTable() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val monitor = DesktopNetworkMonitor({ DBusConnection.connect(socket) }, routes = { false }, pollMs = 50)
            monitor.followNow()
            runBlocking { withTimeout(10_000) { monitor.online.first { !it } } }
            monitor.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
