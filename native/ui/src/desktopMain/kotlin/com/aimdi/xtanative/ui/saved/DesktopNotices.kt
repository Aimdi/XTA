package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * The end of a download or an offline save, said by the desktop's own
 * notifications (org.freedesktop.Notifications) while the window is not the
 * one in use, as a browser tells of a finished download: the line the
 * window's foot says, under the app's name. Clicking it brings the window
 * forward. Nothing is asked of any network; the bus's work runs on a thread
 * of its own.
 */
class DesktopNotifications(
    private val connect: () -> DBusConnection?,
    private val onMain: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) {
    /** Brings the window forward, when a notification is clicked. */
    @Volatile
    var onActivate: (() -> Unit)? = null

    private val bus: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-notify").apply { isDaemon = true } }
    private var connection: DBusConnection? = null
    private var tried = false
    private val sent = Collections.synchronizedSet(mutableSetOf<Long>())

    /** Shows [body] under [summary], the app's name; the notification's id once the desktop took it, through [shown] (tests). */
    fun notify(summary: String, body: String, shown: (Long?) -> Unit = {}) {
        runCatching {
            bus.execute {
                val id = connected()?.call(
                    NAME, PATH, NAME, "Notify", "susssasa{sv}i",
                    listOf(summary, 0L, ICON, summary, body, listOf(DEFAULT, ""), mapOf("desktop-entry" to Variant("s", ICON)), EXPIRE_DEFAULT),
                )?.body?.firstOrNull() as? Long
                id?.let(sent::add)
                shown(id)
            }
        }
    }

    /** Waits (a few seconds at most) for the bus's work asked so far; for tests. */
    fun settle() {
        bus.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun connected(): DBusConnection? {
        connection?.takeIf { it.open }?.let { return it }
        if (connection == null && tried) return null
        tried = true
        return connect()?.also { bus ->
            bus.onSignal = ::signalled
            bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(MATCH))
            connection = bus
        }
    }

    /** One of this app's notifications clicked: the window comes forward. */
    private fun signalled(signal: DBusMessage) {
        if (signal.member != "ActionInvoked" || signal.body.getOrNull(1) != DEFAULT) return
        val id = signal.body.firstOrNull() as? Long ?: return
        if (id in sent) onActivate?.let(onMain)
    }

    companion object {
        const val NAME = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        private const val ICON = "com.aimdi.xtanative"
        private const val DEFAULT = "default"
        private const val EXPIRE_DEFAULT = -1
        private const val MATCH = "type='signal',interface='$NAME',member='ActionInvoked',path='$PATH'"

        /** The desktop's own, on its session bus. */
        val desktop: DesktopNotifications by lazy { DesktopNotifications(DBusConnection::session) }

        /** Whether [notice] ends work the reader may have left the window during: a download or an offline save, done or failed. */
        fun tells(notice: KeepNotice): Boolean = when (notice) {
            KeepNotice.MediaSaved, is KeepNotice.SavedTo, KeepNotice.DownloadFailed, is KeepNotice.Batch,
            is KeepNotice.OfflineKept, KeepNotice.OfflineFailed, is KeepNotice.MediaRefused,
            -> true
            else -> false
        }
    }
}

/**
 * Tells the desktop of each notice [DesktopNotifications.tells] of while
 * the window is not the active one ([windowActive]); the window's own foot
 * says it as ever. [appName] heads it.
 */
@Composable
fun DesktopNotices(appName: String, windowActive: () -> Boolean) {
    val shown by KeepNotices.shown.collectAsState()
    val current = shown ?: return
    val text = noticeText(current.notice)
    val active by rememberUpdatedState(windowActive)
    LaunchedEffect(current.serial) {
        if (DesktopNotifications.tells(current.notice) && !active()) DesktopNotifications.desktop.notify(appName, text)
    }
}
