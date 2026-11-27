package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The launcher's count on the app's icon, as plain decisions: the
 * com.canonical.Unity.LauncherEntry `Update` signal, which KDE's task
 * manager, Dash to Dock, Dash to Panel and Plank read, naming the app by its
 * desktop file. A count only, never what the posts say.
 */
object LauncherEntry {
    const val IFACE = "com.canonical.Unity.LauncherEntry"
    const val PATH = "/com/aimdi/xtanative/LauncherEntry"
    const val SIGNATURE = "sa{sv}"

    /** The app as the launchers know it: its desktop file's id. */
    const val APP_URI = "application://com.aimdi.xtanative.desktop"

    /** `Update`'s arguments for [count]: the number, shown only above zero. */
    fun update(count: Int): List<Any?> = listOf(
        APP_URI,
        mapOf("count" to Variant("x", count.coerceAtLeast(0).toLong()), "count-visible" to Variant("b", count > 0)),
    )
}

/**
 * Tells the launchers on the session bus how many posts are unread
 * ([LauncherEntry]), each change once; the bus is joined only when there is
 * a count to show, and its work runs on a thread of its own.
 */
class LauncherBadge(private val connect: () -> DBusConnection?) {
    private val bus: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-launcher").apply { isDaemon = true } }
    private var connection: DBusConnection? = null
    private var told: Int? = null

    /** Shows [count] on the launcher's icon; zero takes it away. */
    fun show(count: Int) {
        runCatching { bus.execute { tell(count) } }
    }

    private fun tell(count: Int) {
        val last = told ?: 0
        if (count == last && told != null || count == 0 && told == null) return
        val open = connection?.takeIf { it.open } ?: connect()?.also { connection = it } ?: return
        open.signal(LauncherEntry.PATH, LauncherEntry.IFACE, "Update", LauncherEntry.SIGNATURE, LauncherEntry.update(count))
        told = count
    }

    /** Waits (a few seconds at most) for the signals asked so far; for tests. */
    fun settle() {
        bus.submit {}.get(5, TimeUnit.SECONDS)
    }

    companion object {
        /** The app's one badge, on the desktop's session bus. */
        val desktop: LauncherBadge by lazy { LauncherBadge(DBusConnection::session) }
    }
}
