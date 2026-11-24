package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopColorSchemeTest {
    @Test
    fun thePortalsValueSaysDarkOrLight() {
        assertEquals(true, PortalColors.dark(Variant("u", 1L)))
        assertEquals(false, PortalColors.dark(Variant("u", 2L)))
        assertNull(PortalColors.dark(Variant("u", 0L)), "no preference")
        assertEquals(true, PortalColors.dark(Variant("v", Variant("u", 1L))), "Read, before ReadOne, wraps it once more")
        assertNull(PortalColors.dark("dark"))
        assertEquals(true, PortalColors.fromGtkTheme("Adwaita:dark"))
        assertNull(PortalColors.fromGtkTheme("Adwaita"))
        assertNull(PortalColors.fromGtkTheme(null))
    }

    @Test
    fun onlyTheColorSchemesChangeCounts() {
        fun signal(namespace: String, key: String, value: Long) = DBusMessage(
            DBusType.SIGNAL, iface = PortalColors.SETTINGS, member = "SettingChanged", signature = "ssv",
            body = listOf(namespace, key, Variant("u", value)),
        )
        assertEquals(false, PortalColors.changed(signal(PortalColors.NAMESPACE, PortalColors.KEY, 2)))
        assertNull(PortalColors.changed(signal(PortalColors.NAMESPACE, "accent-color", 1)))
        assertNull(PortalColors.changed(signal("org.gnome.desktop.interface", PortalColors.KEY, 1)))
    }

    @Test
    fun theDesktopsAnimationsSwitchReducesMotion() {
        assertEquals(true, PortalMotion.off(Variant("b", false)))
        assertEquals(false, PortalMotion.off(Variant("b", true)))
        assertNull(PortalMotion.off(Variant("u", 1L)))
        fun signal(namespace: String, key: String, on: Boolean) = DBusMessage(
            DBusType.SIGNAL, iface = PortalColors.SETTINGS, member = "SettingChanged", signature = "ssv",
            body = listOf(namespace, key, Variant("b", on)),
        )
        assertEquals(true, PortalMotion.changed(signal(PortalMotion.NAMESPACE, PortalMotion.KEY, false)))
        assertNull(PortalMotion.changed(signal(PortalColors.NAMESPACE, PortalMotion.KEY, false)))
        // Neither reads the other's setting.
        assertNull(PortalColors.changed(signal(PortalMotion.NAMESPACE, PortalMotion.KEY, false)))
    }

    @Test
    fun thePortalsAccentColourIsReadAsArgb() {
        fun rgb(r: Double, g: Double, b: Double) = Variant("(ddd)", listOf(r, g, b))
        assertEquals(0xFF3584E4, PortalAccent.argb(rgb(0x35 / 255.0, 0x84 / 255.0, 0xE4 / 255.0)))
        assertEquals(0xFF000000, PortalAccent.argb(rgb(0.0, 0.0, 0.0)))
        assertEquals(0xFFFFFFFF, PortalAccent.argb(rgb(1.0, 1.0, 1.0)))
        assertEquals(0xFFFF0000, PortalAccent.argb(Variant("v", rgb(1.0, 0.0, 0.0))), "Read, before ReadOne, wraps it once more")
        // Out of range is how a portal says "no accent".
        assertNull(PortalAccent.argb(rgb(-1.0, -1.0, -1.0)))
        assertNull(PortalAccent.argb(rgb(0.5, 1.5, 0.5)))
        assertNull(PortalAccent.argb(Variant("(dd)", listOf(0.5, 0.5))))
        assertNull(PortalAccent.argb(Variant("u", 1L)))
        assertNull(PortalAccent.argb(null))
    }

    @Test
    fun onlyTheAccentColoursChangeIsAnAccentChange() {
        fun signal(namespace: String, key: String) = DBusMessage(
            DBusType.SIGNAL, iface = PortalColors.SETTINGS, member = "SettingChanged", signature = "ssv",
            body = listOf(namespace, key, Variant("(ddd)", listOf(1.0, 0.0, 0.0))),
        )
        assertTrue(PortalAccent.isChange(signal(PortalAccent.NAMESPACE, PortalAccent.KEY)))
        assertFalse(PortalAccent.isChange(signal(PortalAccent.NAMESPACE, PortalColors.KEY)))
        assertFalse(PortalAccent.isChange(signal(PortalMotion.NAMESPACE, PortalAccent.KEY)))
    }

    /** A bus of the test's own, never the desktop's; null without dbus-daemon. */
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
    fun theSchemeFollowsThePortal() {
        val (daemon, socket) = privateBus() ?: return println("no dbus-daemon: skipped")
        try {
            // A portal of the test's own: dark until it says otherwise.
            val portal = assertNotNull(DBusConnection.connect(socket))
            portal.onCall = { call ->
                when {
                    call.member == "ReadOne" && call.body == listOf(PortalColors.NAMESPACE, PortalColors.KEY) -> portal.reply(call, "v", listOf(Variant("u", 1L)))
                    call.member == "ReadOne" && call.body == listOf(PortalAccent.NAMESPACE, PortalAccent.KEY) -> portal.reply(call, "v", listOf(Variant("(ddd)", listOf(0.0, 0.0, 1.0))))
                    else -> portal.fail(call, DBusConnection.UNKNOWN_METHOD, "no")
                }
            }
            portal.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(PortalColors.PORTAL, 4L))
            val scheme = DesktopColorScheme({ DBusConnection.connect(socket) }, gtkTheme = null)
            assertNull(scheme.dark.value)
            scheme.followNow()
            assertEquals(true, scheme.dark.value)
            portal.signal(PortalColors.PATH, PortalColors.SETTINGS, "SettingChanged", "ssv", listOf(PortalColors.NAMESPACE, PortalColors.KEY, Variant("u", 2L)))
            runBlocking { withTimeout(5_000) { scheme.dark.first { it == false } } }
            assertEquals(0xFF0000FF, scheme.accent.value)
            portal.signal(PortalColors.PATH, PortalColors.SETTINGS, "SettingChanged", "ssv", listOf(PortalAccent.NAMESPACE, PortalAccent.KEY, Variant("(ddd)", listOf(0.0, 1.0, 0.0))))
            runBlocking { withTimeout(5_000) { scheme.accent.first { it == 0xFF00FF00 } } }
            // The desktop dropping its accent: back to none, so the app's default.
            portal.signal(PortalColors.PATH, PortalColors.SETTINGS, "SettingChanged", "ssv", listOf(PortalAccent.NAMESPACE, PortalAccent.KEY, Variant("(ddd)", listOf(-1.0, -1.0, -1.0))))
            runBlocking { withTimeout(5_000) { scheme.accent.first { it == null } } }
            scheme.close()
            portal.close()
        } finally {
            daemon.destroy()
            daemon.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
