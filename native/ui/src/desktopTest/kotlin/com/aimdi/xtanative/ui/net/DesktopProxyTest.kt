package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.store.settings.SettingsStore
import java.nio.file.attribute.PosixFilePermissions
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.core.net.AppProxy
import com.aimdi.xtanative.core.net.ProxyCredentials
import com.aimdi.xtanative.core.net.ProxyKind
import com.aimdi.xtanative.core.net.ProxyPolicy
import com.aimdi.xtanative.core.net.ProxyRoute
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.ProxyMode
import com.aimdi.xtanative.store.settings.ProxyType
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopProxyTest {
    private val env = mapOf("https_proxy" to "http://env.test:3128", "http_proxy" to "http://env.test:3128")
    private val portal = ProxyPolicy { _, _, _ -> ProxyRoute.Via(ProxyKind.HTTP, "portal.test", 8080) }
    private val manual = Settings().with(DesktopPrefs.proxyMode, ProxyMode.MANUAL).with(DesktopPrefs.proxyHost, " Proxy.LAN ").with(DesktopPrefs.proxyPort, 1080)

    private fun route(settings: Settings, env: Map<String, String> = this.env, host: String = "x.com") =
        DesktopProxy.policy(settings, env, portal).route("https", host, 443)

    @Test
    fun systemTakesTheEnvironmentFirstThenThePortal() {
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "env.test", 3128), route(Settings()))
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "portal.test", 8080), route(Settings(), env = emptyMap()))
        assertEquals(ProxyRoute.Direct, DesktopProxy.policy(Settings(), emptyMap(), null).route("https", "x.com", 443), "no portal: straight")
    }

    @Test
    fun noneGoesStraightWhateverTheEnvironmentSays() {
        assertEquals(ProxyRoute.Direct, route(Settings().with(DesktopPrefs.proxyMode, ProxyMode.NONE)))
    }

    @Test
    fun manualGoesThroughTheProxySetByHandExceptItsBypass() {
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "proxy.lan", 1080), route(manual))
        val socks = manual.with(DesktopPrefs.proxyType, ProxyType.SOCKS5).with(DesktopPrefs.proxyUser, "me").with(DesktopPrefs.proxyPassword, "pw")
        assertEquals(ProxyRoute.Via(ProxyKind.SOCKS, "proxy.lan", 1080, ProxyCredentials("me", "pw")), route(socks))
        assertEquals(ProxyRoute.Direct, route(manual.with(DesktopPrefs.proxyBypass, "x.com"), host = "api.x.com"))
    }

    @Test
    fun aManualProxyWithoutHostOrPortFailsClosed() {
        assertEquals(ProxyRoute.Unusable, route(manual.with(DesktopPrefs.proxyHost, "")))
        assertEquals(ProxyRoute.Unusable, route(manual.with(DesktopPrefs.proxyPort, 0)))
        assertEquals(ProxyRoute.Unusable, route(manual.with(DesktopPrefs.proxyHost, "http://p.lan/")))
    }

    @Test
    fun videosAreToldTheirProxyAndNoneIsNone() {
        assertEquals(MpvProxy.NONE, MpvProxy.option(ProxyRoute.Direct))
        assertEquals(MpvProxy.BLOCKED, MpvProxy.option(ProxyRoute.Unusable))
        assertEquals(MpvProxy.BLOCKED, MpvProxy.option(ProxyRoute.Via(ProxyKind.SOCKS, "s.lan", 1080)), "no SOCKS for videos yet: they fail closed")
        assertEquals("http://p.lan:3128", MpvProxy.option(ProxyRoute.Via(ProxyKind.HTTP, "p.lan", 3128)))
        assertEquals("http://m%40e:p%3Aw%20d@[fd00::1]:8080", MpvProxy.option(ProxyRoute.Via(ProxyKind.HTTP, "fd00::1", 8080, ProxyCredentials("m@e", "p:w d"))))
        try {
            AppProxy.use { _, _, _ -> ProxyRoute.Via(ProxyKind.HTTP, "p.lan", 3128) }
            assertEquals(MpvProxy.NONE, MpvProxy.option("http://127.0.0.1:8805/video.mp4"), "loopback straight, $" + "http_proxy or not")
            assertEquals(MpvProxy.NONE, MpvProxy.option("/home/me/Videos/a.mp4"))
            assertEquals("http://p.lan:3128", MpvProxy.option("https://video.twimg.com/a.mp4"))
        } finally {
            AppProxy.use(AppProxy.PLATFORM)
        }
    }

    @Test
    fun thePortalPicksTheFirstProxyTheAppCanUse() {
        assertEquals(ProxyRoute.Direct, PortalProxyPolicy.first(listOf("direct://")))
        assertEquals(ProxyRoute.Direct, PortalProxyPolicy.first(emptyList()))
        assertEquals(ProxyRoute.Via(ProxyKind.SOCKS, "s.lan", 1080), PortalProxyPolicy.first(listOf("https://tls.lan:443", "socks5://s.lan:1080", "socks4://s.lan:1080")))
        assertEquals(ProxyRoute.Unusable, PortalProxyPolicy.first(listOf("socks4://s.lan:1080")))
    }

    @Test
    fun thePortalIsAskedForSchemeHostAndPortOnceInAWhile() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val asked = mutableListOf<String>()
            val fake = assertNotNull(DBusConnection.connect(socket))
            fake.onCall = { call ->
                if (call.member == "Lookup") {
                    asked += call.body.first() as String
                    fake.reply(call, "as", listOf(listOf("http://portal.lan:3128", "direct://")))
                } else {
                    fake.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
                }
            }
            fake.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalProxyPolicy.PORTAL, 4L))
            var now = 0L
            val connects = AtomicInteger()
            val policy = PortalProxyPolicy({ connects.incrementAndGet(); DBusConnection.connect(socket) }, clock = { now })
            assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128), policy.route("https", "x.com", 443))
            assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128), policy.route("https", "x.com", 443))
            now += PortalProxyPolicy.CACHE_MS
            policy.route("https", "x.com", 443)
            policy.route("http", "fd00::1", 80)
            assertEquals(listOf("https://x.com:443", "https://x.com:443", "http://[fd00::1]:80"), asked)
            assertEquals(1, connects.get())
            fake.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aBusWithoutThePortalGoesStraightWithoutWaitingEachTime() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val policy = PortalProxyPolicy({ DBusConnection.connect(socket) }, timeoutMs = 500)
            assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
            val started = System.nanoTime()
            assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
            assert((System.nanoTime() - started) / 1_000_000 < 400) { "the answer is kept" }
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aPortalThatDoesNotAnswerIsAskedAgainAndNeverTakenForDirect() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val answers = AtomicBoolean(false)
            val asked = AtomicInteger()
            val fake = assertNotNull(DBusConnection.connect(socket))
            fake.onCall = { call ->
                if (call.member == "Lookup") {
                    asked.incrementAndGet()
                    if (answers.get()) fake.reply(call, "as", listOf(listOf("http://portal.lan:3128")))
                }
            }
            fake.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalProxyPolicy.PORTAL, 4L))
            var now = 0L
            val policy = PortalProxyPolicy({ DBusConnection.connect(socket) }, timeoutMs = 200, clock = { now })
            assertEquals(ProxyRoute.Unusable, policy.route("https", "x.com", 443), "still starting: fail, not straight")
            now += PortalProxyPolicy.RETRY_MS
            answers.set(true)
            val via = ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128)
            assertEquals(via, policy.route("https", "x.com", 443), "asked again soon")
            answers.set(false)
            now += PortalProxyPolicy.CACHE_MS
            assertEquals(via, policy.route("https", "x.com", 443), "a miss keeps the last answer")
            assertEquals(via, policy.route("https", "pbs.twimg.com", 443), "or the last for any address")
            assertEquals(4, asked.get())
            fake.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aPortalThatNeverAnswersGoesStraightOnlyAfterTheGrace() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val fake = assertNotNull(DBusConnection.connect(socket))
            fake.onCall = { }
            fake.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalProxyPolicy.PORTAL, 4L))
            var now = 0L
            val policy = PortalProxyPolicy({ DBusConnection.connect(socket) }, timeoutMs = 100, clock = { now })
            assertEquals(ProxyRoute.Unusable, policy.route("https", "x.com", 443))
            now += PortalProxyPolicy.GRACE_MS
            assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
            fake.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aSlowPortalWarmedAtStartDelaysTheFirstRequestInsteadOfFailingIt() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val fake = assertNotNull(DBusConnection.connect(socket))
            fake.onCall = { call ->
                if (call.member == "Lookup") Thread { Thread.sleep(400); fake.reply(call, "as", listOf(listOf("http://portal.lan:3128"))) }.start()
            }
            fake.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalProxyPolicy.PORTAL, 4L))
            val policy = PortalProxyPolicy({ DBusConnection.connect(socket) }, timeoutMs = 100, graceMs = 5_000)
            policy.warm()
            assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128), policy.route("https", "x.com", 443), "waited for the portal's answer")
            var onWindowThread: ProxyRoute? = null
            java.awt.EventQueue.invokeAndWait { onWindowThread = policy.route("https", "pbs.twimg.com", 443) }
            assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128), onWindowThread, "the window's thread takes the last answer, never asks")
            fake.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun aBusOutOfReachIsTriedAgainLaterNotGivenUpOn() {
        var now = 0L
        val connects = AtomicInteger()
        val policy = PortalProxyPolicy({ connects.incrementAndGet(); null }, clock = { now })
        assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
        assertEquals(ProxyRoute.Direct, policy.route("https", "a.com", 443))
        assertEquals(1, connects.get(), "not again at once")
        now += PortalProxyPolicy.CACHE_MS
        policy.route("https", "x.com", 443)
        assertEquals(2, connects.get())
    }

    @Test
    fun aBusNotYetUpAtLoginIsNoAnswerKeptForMinutes() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            val fake = assertNotNull(DBusConnection.connect(socket))
            fake.onCall = { call -> if (call.member == "Lookup") fake.reply(call, "as", listOf(listOf("http://portal.lan:3128"))) }
            fake.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalProxyPolicy.PORTAL, 4L))
            var now = 0L
            val up = AtomicBoolean(false)
            val policy = PortalProxyPolicy({ if (up.get()) DBusConnection.connect(socket) else null }, clock = { now })
            policy.warm()
            assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443), "no bus yet: straight, for now")
            var onWindowThread: ProxyRoute? = null
            java.awt.EventQueue.invokeAndWait { onWindowThread = policy.route("https", "pbs.twimg.com", 443) }
            assertEquals(ProxyRoute.Unusable, onWindowThread, "no bus is no answer the window's thread goes by")
            up.set(true)
            now += PortalProxyPolicy.RETRY_MS
            val via = ProxyRoute.Via(ProxyKind.HTTP, "portal.lan", 3128)
            assertEquals(via, policy.route("https", "x.com", 443), "the bus is asked again within seconds, not minutes")
            fake.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun noSessionBusAtAllIsAnAnswer() {
        var now = 0L
        val connects = AtomicInteger()
        val policy = PortalProxyPolicy({ connects.incrementAndGet(); null }, clock = { now }, hasBus = { false })
        assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
        now += PortalProxyPolicy.RETRY_MS
        assertEquals(ProxyRoute.Direct, policy.route("https", "x.com", 443))
        var onWindowThread: ProxyRoute? = null
        java.awt.EventQueue.invokeAndWait { onWindowThread = policy.route("https", "pbs.twimg.com", 443) }
        assertEquals(ProxyRoute.Direct, onWindowThread, "straight, as it always was")
        assertEquals(1, connects.get(), "kept as an answer")
    }

    @Test
    fun onlyTheNoPortalErrorsCountAsAnAnswer() {
        fun error(name: String) = DBusMessage(DBusType.ERROR, errorName = name)
        assertEquals(ProxyRoute.Direct, PortalProxyPolicy.routeOf(error("org.freedesktop.DBus.Error.ServiceUnknown")))
        assertEquals(null, PortalProxyPolicy.routeOf(error("org.freedesktop.DBus.Error.NoReply")))
        assertEquals(null, PortalProxyPolicy.routeOf(error("org.freedesktop.portal.Error.Failed")))
    }

    /** A bus of the test's own, with no service folders: nothing on it starts by itself. */
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
    fun settingsThatCannotBeReadSendNothingAroundTheProxyTheyMayName() {
        val root = Files.createTempDirectory("proxy-settings").toFile()
        val file = File(root, "settings.json").apply { writeText("{}") }
        // Left behind by a run as root: the reader cannot open it.
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("---------"))
        try {
            if (file.canRead()) return
            DesktopProxy.follow(SettingsStore(StoreDir(root)), env = emptyMap(), portal = null)
            assertEquals(ProxyRoute.Unusable, AppProxy.policy.route("https", "x.com", 443))
        } finally {
            AppProxy.use(AppProxy.PLATFORM)
            root.deleteRecursively()
        }
    }
}
