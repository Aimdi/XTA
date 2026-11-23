package com.aimdi.xtanative.ui.chrome.tray

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's icon in the desktop's tray (StatusNotifierItem, [StatusNotifier]):
 * registered with the bar's org.kde.StatusNotifierWatcher on the session
 * bus, again whenever the bar comes back. [shown] says whether a tray shows
 * it now, so a window closed "to the tray" can never be lost: with no tray,
 * closing quits. Clicks reach [onAction] on [onMain]; the bus's work runs
 * on a thread of its own.
 */
class TrayService(
    private val connect: () -> DBusConnection?,
    private val onMain: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    private val iconName: String = installedIconName(),
    private val pixmaps: List<List<Any>> = TrayIcon.pixmaps(),
    private val pid: Long = ProcessHandle.current().pid(),
) {
    /** What a click in the tray asks. */
    @Volatile
    var onAction: ((TrayAction) -> Unit)? = null

    private val _shown = MutableStateFlow(false)

    /** Whether a tray took the icon (a watcher registered it and a host shows its items). */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    private val lock = Any()
    private var now = TrayNow()
    private val bus: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-tray").apply { isDaemon = true } }
    private var connection: DBusConnection? = null
    private var started = false

    /** Joins the bus and the tray, once; nothing where there is no session bus. */
    fun start() {
        runCatching { bus.execute(::join) }
    }

    /** The words the icon and its menu say, in the reader's language. */
    fun words(words: TrayWords) = change { it.copy(words = words) }

    /** Whether the window is on screen, which the menu's Show/Hide follows. */
    fun windowShown(shown: Boolean) = change { it.copy(windowShown = shown) }

    private fun change(how: (TrayNow) -> TrayNow) {
        val (before, after) = synchronized(lock) {
            val before = now
            val changed = how(before)
            now = if (changed == before) before else changed.copy(revision = before.revision + 1)
            before to now
        }
        if (before == after) return
        runCatching {
            bus.execute {
                val bus = connection?.takeIf { it.open } ?: return@execute
                bus.signal(StatusNotifier.MENU_PATH, StatusNotifier.MENU, "LayoutUpdated", "ui", listOf(after.revision, StatusNotifier.ROOT_ID))
                if (before.words.title != after.words.title) {
                    bus.signal(StatusNotifier.ITEM_PATH, StatusNotifier.ITEM, "NewTitle", "", emptyList())
                    bus.signal(StatusNotifier.ITEM_PATH, StatusNotifier.ITEM, "NewToolTip", "", emptyList())
                }
            }
        }
    }

    /** Waits (a few seconds at most) for the bus's work asked so far; for tests. */
    fun settle() {
        bus.submit {}.get(5, TimeUnit.SECONDS)
    }

    /** Leaves the bus; for tests. */
    fun stop() {
        runCatching { bus.submit { connection?.close() }.get(5, TimeUnit.SECONDS) }
        _shown.value = false
    }

    /** On the bus's thread: the name taken, the watcher watched, the icon registered. */
    private fun join() {
        if (started) return
        started = true
        val bus = connect() ?: return
        connection = bus
        bus.onCall = ::answer
        bus.onSignal = { signal -> runCatching { this.bus.execute { signalled(signal) } } }
        // With the connection gone, no icon is left to bring a hidden window back: the window shows again.
        bus.onClosed = { _shown.value = false }
        bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(StatusNotifier.busName(pid), DO_NOT_QUEUE))
        listOf(WATCHER_APPEARS, WATCHER_SAYS).forEach { rule -> bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(rule)) }
        register()
    }

    /** On the bus's thread: the icon handed to the watcher there is, and whether a host shows it. */
    private fun register() {
        val bus = connection?.takeIf { it.open } ?: return run { _shown.value = false }
        val answer = bus.call(StatusNotifier.WATCHER, StatusNotifier.WATCHER_PATH, StatusNotifier.WATCHER, "RegisterStatusNotifierItem", "s", listOf(StatusNotifier.busName(pid)))
        _shown.value = answer?.type == DBusType.METHOD_RETURN && hostShows(bus)
    }

    /** Whether a host (a bar) shows the watcher's items; a watcher that does not say is taken at its word. */
    private fun hostShows(bus: DBusConnection): Boolean {
        val answer = bus.call(StatusNotifier.WATCHER, StatusNotifier.WATCHER_PATH, StatusNotifier.PROPERTIES, "Get", "ss", listOf(StatusNotifier.WATCHER, "IsStatusNotifierHostRegistered"))
        if (answer?.type != DBusType.METHOD_RETURN) return answer?.type == DBusType.ERROR
        return (answer.body.firstOrNull() as? Variant)?.value != false
    }

    /** On the bus's thread: the watcher came, went or its hosts changed. */
    private fun signalled(signal: DBusMessage) {
        when (signal.member) {
            "NameOwnerChanged" -> {
                if (signal.body.getOrNull(0) != StatusNotifier.WATCHER) return
                if ((signal.body.getOrNull(2) as? String).isNullOrEmpty()) _shown.value = false else register()
            }
            "StatusNotifierHostRegistered", "StatusNotifierHostUnregistered" -> register()
        }
    }

    /** On the connection's thread: one call from the tray answered, and what it asks done on [onMain]. */
    private fun answer(call: DBusMessage) {
        val bus = connection ?: return
        val state = synchronized(lock) { now }
        val answer = StatusNotifier.answer(call, state, iconName, pixmaps)
        answer.error?.let { return bus.fail(call, it, call.member.orEmpty()) }
        bus.reply(call, answer.signature, answer.body)
        answer.action?.let { action -> onAction?.let { act -> onMain { act(action) } } }
    }

    companion object {
        /** RequestName's DBUS_NAME_FLAG_DO_NOT_QUEUE. */
        private const val DO_NOT_QUEUE = 4L
        private const val WATCHER_APPEARS =
            "type='signal',sender='org.freedesktop.DBus',interface='org.freedesktop.DBus',member='NameOwnerChanged',arg0='${StatusNotifier.WATCHER}'"
        private const val WATCHER_SAYS = "type='signal',interface='${StatusNotifier.WATCHER}'"

        /** The app's one tray icon, on the desktop's session bus. */
        val desktop: TrayService by lazy { TrayService(DBusConnection::session) }

        /**
         * The icon theme's name for the app's icon where the package
         * installed it (share/icons/hicolor of a data folder), else none:
         * the tray then draws [TrayIcon.pixmaps].
         */
        fun installedIconName(dataDirs: String? = System.getenv("XDG_DATA_DIRS")): String {
            val folders = dataDirs?.split(':')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf("/usr/local/share", "/usr/share")
            val found = folders.any { File(it, "icons/hicolor/scalable/apps/${StatusNotifier.ICON_NAME}.svg").isFile }
            return if (found) StatusNotifier.ICON_NAME else ""
        }
    }
}

/** The launcher icon (an X of two strokes on black, as AppIcon.kt draws it) as the tray's ARGB32 pixmaps. */
object TrayIcon {
    private val SIZES = listOf(16, 22, 24, 32, 48, 64)

    /** One (width, height, pixels) per size; pixels ARGB32 in network byte order, as the specification asks. */
    fun pixmaps(): List<List<Any>> = SIZES.map { size -> listOf(size, size, argb(draw(size))) }

    fun draw(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.scale(size / 72.0, size / 72.0)
            g.color = Color.BLACK
            g.fill(RoundRectangle2D.Double(0.0, 0.0, 72.0, 72.0, 32.0, 32.0))
            g.color = Color.WHITE
            g.fill(stroke(20.0, 18.0, 28.0, 18.0, 52.0, 54.0, 44.0, 54.0))
            g.color = Color(0x1D, 0x9B, 0xF0)
            g.fill(stroke(44.0, 18.0, 52.0, 18.0, 28.0, 54.0, 20.0, 54.0))
        } finally {
            g.dispose()
        }
        return image
    }

    private fun stroke(vararg points: Double) = Path2D.Double().apply {
        moveTo(points[0], points[1])
        for (at in 2 until points.size step 2) lineTo(points[at], points[at + 1])
        closePath()
    }

    fun argb(image: BufferedImage): ByteArray {
        val out = ByteArray(image.width * image.height * 4)
        var at = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                out[at++] = (pixel ushr 24).toByte()
                out[at++] = (pixel ushr 16).toByte()
                out[at++] = (pixel ushr 8).toByte()
                out[at++] = pixel.toByte()
            }
        }
        return out
    }
}
