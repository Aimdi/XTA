package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the desktop prefers dark, as its settings portal says
 * (org.freedesktop.appearance color-scheme, which GNOME, KDE and the
 * portals of other desktops answer, and which changes as the reader
 * switches): true for dark, false for light, null where nothing says, as
 * Java itself cannot tell on Linux. Read once at the start, then followed.
 * On the same connection, whether the desktop has its animations off
 * ([animationsOff], GNOME's enable-animations, which the portals pass on),
 * and its accent colour ([accent], org.freedesktop.appearance accent-color).
 */
class DesktopColorScheme(
    private val connect: () -> DBusConnection?,
    private val gtkTheme: String? = System.getenv("GTK_THEME"),
) {
    private val mutable = MutableStateFlow(PortalColors.fromGtkTheme(gtkTheme))
    val dark: StateFlow<Boolean?> = mutable.asStateFlow()
    private val stillness = MutableStateFlow(false)

    /** Whether the desktop's settings turn animations off; false where nothing says. */
    val animationsOff: StateFlow<Boolean> = stillness.asStateFlow()
    private val tint = MutableStateFlow<Long?>(null)

    /** The desktop's accent colour as opaque ARGB; null where it gives none or one out of range. */
    val accent: StateFlow<Long?> = tint.asStateFlow()
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
        bus.onSignal = { signal ->
            PortalColors.changed(signal)?.let { mutable.value = it }
            PortalMotion.changed(signal)?.let { stillness.value = it }
            if (PortalAccent.isChange(signal)) tint.value = PortalAccent.argb(signal.body[2])
        }
        bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(PortalColors.MATCH))
        read(bus, PortalColors.NAMESPACE, PortalColors.KEY)?.let { PortalColors.dark(it) }?.let { mutable.value = it }
        read(bus, PortalMotion.NAMESPACE, PortalMotion.KEY)?.let { PortalMotion.off(it) }?.let { stillness.value = it }
        read(bus, PortalAccent.NAMESPACE, PortalAccent.KEY)?.let { PortalAccent.argb(it) }?.let { tint.value = it }
    }

    /**
     * One setting's value from the portal; null where it has none. Portals
     * before version 2 have only Read, whose value comes wrapped once more
     * (each reader unwraps it).
     */
    private fun read(bus: DBusConnection, namespace: String, key: String): Any? =
        (
            bus.call(PortalColors.PORTAL, PortalColors.PATH, PortalColors.SETTINGS, "ReadOne", "ss", listOf(namespace, key))
                ?.takeIf { it.type == DBusType.METHOD_RETURN }
                ?: bus.call(PortalColors.PORTAL, PortalColors.PATH, PortalColors.SETTINGS, "Read", "ss", listOf(namespace, key))
                    ?.takeIf { it.type == DBusType.METHOD_RETURN }
            )?.body?.firstOrNull()

    /** Stops following (tests). */
    fun close() {
        connection?.close()
    }

    companion object {
        /** The desktop's own, on its session bus. */
        val desktop: DesktopColorScheme by lazy { DesktopColorScheme(DBusConnection::session) }
    }
}

/** The desktop's animations setting through the settings portal (GNOME's org.gnome.desktop.interface enable-animations), as plain decisions. */
object PortalMotion {
    const val NAMESPACE = "org.gnome.desktop.interface"
    const val KEY = "enable-animations"

    /** Whether the portal's value turns animations off: true for false; null for no answer of that kind. */
    fun off(value: Any?): Boolean? = (unwrapped(value) as? Boolean)?.not()

    /** What a SettingChanged signal says of the animations; null for any other signal or setting. */
    fun changed(signal: DBusMessage): Boolean? {
        if (signal.iface != PortalColors.SETTINGS || signal.member != "SettingChanged" || signal.body.size < 3) return null
        if (signal.body[0] != NAMESPACE || signal.body[1] != KEY) return null
        return off(signal.body[2])
    }

    private tailrec fun unwrapped(value: Any?): Any? = if (value is Variant) unwrapped(value.value) else value
}

/**
 * The desktop's accent colour through the settings portal
 * (org.freedesktop.appearance accent-color: red, green and blue, each 0 to
 * 1; a value out of that range means the desktop has none), as plain decisions.
 */
object PortalAccent {
    const val NAMESPACE = PortalColors.NAMESPACE
    const val KEY = "accent-color"

    /** The portal's value as opaque ARGB; null for no colour, a channel out of 0..1 or a value of another kind. */
    fun argb(value: Any?): Long? {
        val channels = (unwrapped(value) as? List<*>)?.takeIf { it.size == 3 } ?: return null
        val levels = channels.map { (it as? Number)?.toDouble()?.takeIf { level -> level in 0.0..1.0 } ?: return null }
        return Contrast.argb(255, byte(levels[0]), byte(levels[1]), byte(levels[2]))
    }

    /** Whether [signal] is the portal saying the accent colour changed (to [argb] of its value, which may be none). */
    fun isChange(signal: DBusMessage): Boolean =
        signal.iface == PortalColors.SETTINGS && signal.member == "SettingChanged" && signal.body.size >= 3 &&
            signal.body[0] == NAMESPACE && signal.body[1] == KEY

    private fun byte(level: Double): Int = (level * 255).roundToInt()

    private tailrec fun unwrapped(value: Any?): Any? = if (value is Variant) unwrapped(value.value) else value
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
