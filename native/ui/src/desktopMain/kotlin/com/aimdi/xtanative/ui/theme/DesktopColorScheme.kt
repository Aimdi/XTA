package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the desktop prefers dark, as its settings portal says
 * (org.freedesktop.appearance color-scheme, which GNOME, KDE and the
 * portals of other desktops answer, and which changes as the reader
 * switches): true for dark, false for light, null where nothing says, as
 * Java itself cannot tell on Linux. Read once at the start, then followed.
 */
class DesktopColorScheme(
    private val connect: () -> DBusConnection?,
    private val gtkTheme: String? = System.getenv("GTK_THEME"),
) {
    private val mutable = MutableStateFlow(PortalColors.fromGtkTheme(gtkTheme))
    val dark: StateFlow<Boolean?> = mutable.asStateFlow()
    private val started = AtomicBoolean(false)

    @Volatile
    private var connection: DBusConnection? = null

    /** Asks the portal on a thread of its own, once; [dark] follows from then on. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread(::follow, "xta-native-color-scheme").apply { isDaemon = true }.start()
    }

    /** Asks now, on the caller's thread (tests). */
    fun followNow() {
        started.set(true)
        follow()
    }

    private fun follow() {
        val bus = connect() ?: return
        connection = bus
        bus.onSignal = { signal -> PortalColors.changed(signal)?.let { mutable.value = it } }
        bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(PortalColors.MATCH))
        val answer = bus.call(PortalColors.PORTAL, PortalColors.PATH, PortalColors.SETTINGS, "ReadOne", "ss", listOf(PortalColors.NAMESPACE, PortalColors.KEY))
            ?.takeIf { it.type == DBusType.METHOD_RETURN }
            // Portals before version 2 have only Read, whose value comes wrapped once more.
            ?: bus.call(PortalColors.PORTAL, PortalColors.PATH, PortalColors.SETTINGS, "Read", "ss", listOf(PortalColors.NAMESPACE, PortalColors.KEY))
        answer?.takeIf { it.type == DBusType.METHOD_RETURN }?.let { reply -> PortalColors.dark(reply.body.firstOrNull())?.let { mutable.value = it } }
    }

    /** Stops following (tests). */
    fun close() {
        connection?.close()
    }

    companion object {
        /** The desktop's own, on its session bus. */
        val desktop: DesktopColorScheme by lazy { DesktopColorScheme(DBusConnection::session) }
    }
}

/** The settings portal's color-scheme, as plain decisions. */
object PortalColors {
    const val PORTAL = "org.freedesktop.portal.Desktop"
    const val PATH = "/org/freedesktop/portal/desktop"
    const val SETTINGS = "org.freedesktop.portal.Settings"
    const val NAMESPACE = "org.freedesktop.appearance"
    const val KEY = "color-scheme"
    const val MATCH = "type='signal',interface='$SETTINGS',member='SettingChanged',path='$PATH'"

    /** The portal's value: 1 prefers dark, 2 prefers light, 0 (no preference) and anything else says nothing. */
    fun dark(value: Any?): Boolean? = when (unwrapped(value)) {
        1L -> true
        2L -> false
        else -> null
    }

    /** What a SettingChanged signal says of the color scheme; null for any other signal or setting. */
    fun changed(signal: DBusMessage): Boolean? {
        if (signal.iface != SETTINGS || signal.member != "SettingChanged" || signal.body.size < 3) return null
        if (signal.body[0] != NAMESPACE || signal.body[1] != KEY) return null
        return dark(signal.body[2])
    }

    /** GTK_THEME's ":dark" variant (Adwaita:dark), a desktop's way to ask for dark without a portal. */
    fun fromGtkTheme(theme: String?): Boolean? = if (theme?.endsWith(":dark", ignoreCase = true) == true) true else null

    private tailrec fun unwrapped(value: Any?): Any? = when (value) {
        is Variant -> unwrapped(value.value)
        is Int -> value.toLong()
        else -> value
    }
}
