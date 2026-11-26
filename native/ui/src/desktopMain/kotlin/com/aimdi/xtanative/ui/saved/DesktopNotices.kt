package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.ui.downloads.FileManagerShow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.downloads_show_in_folder
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The end of a download or an offline save, said by the desktop's own
 * notifications (org.freedesktop.Notifications) while the window is not the
 * one in use, as a browser tells of a finished download: the line the
 * window's foot says, under the app's name. Clicking it brings the window
 * forward; a finished download's has "Show in folder" too, which opens the
 * file manager on it. Nothing is asked of any network; the bus's work runs on a thread
 * of its own.
 */
class DesktopNotifications(
    private val connect: () -> DBusConnection?,
    private val onMain: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) {
    /** Brings the window forward, when a notification is clicked. */
    @Volatile
    var onActivate: (() -> Unit)? = null

    /** Shows a download in its folder, when a notification's "Show in folder" is clicked; given its saved address. */
    @Volatile
    var onShowInFolder: ((String) -> Unit)? = null

    /** The saved address of each notification that offers "Show in folder", by its id. */
    private val folders = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private val bus: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-notify").apply { isDaemon = true } }
    private var connection: DBusConnection? = null
    private var tried = false

    /** Whether the desktop reads bodies as markup ("body-markup"), so a body is escaped. */
    private var markup = false

    /** This app's notifications still on the desktop, oldest first; gone when closed or clicked. */
    private val sent = Collections.synchronizedSet(LinkedHashSet<Long>())

    /**
     * Shows [body] under [summary], the app's name; with [folder] (a
     * download's saved address and the button's [folderLabel]) it offers
     * "Show in folder" too. The notification's id once the desktop took it,
     * through [shown] (tests).
     */
    fun notify(summary: String, body: String, folder: String? = null, folderLabel: String = "", shown: (Long?) -> Unit = {}) {
        runCatching {
            bus.execute {
                val id = connected()?.call(
                    NAME, PATH, NAME, "Notify", "susssasa{sv}i",
                    listOf(summary, 0L, ICON, summary, bodyFor(body, markup), actionsFor(folder, folderLabel), mapOf("desktop-entry" to Variant("s", ICON)), EXPIRE_DEFAULT),
                )?.body?.firstOrNull() as? Long
                id?.let { kept(it, folder) }
                shown(id)
            }
        }
    }

    private fun kept(id: Long, folder: String?) {
        synchronized(sent) {
            sent.add(id)
            if (sent.size > MAX_KEPT) sent.first().let { oldest -> sent.remove(oldest); folders.remove(oldest) }
        }
        if (folder != null) folders[id] = folder
    }

    private fun forget(id: Long) {
        sent.remove(id)
        folders.remove(id)
    }

    /** How many notifications are remembered (tests). */
    internal val remembered: Int get() = sent.size + folders.size

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
            markup = (bus.call(NAME, PATH, NAME, "GetCapabilities")?.body?.firstOrNull() as? List<*>)?.contains(BODY_MARKUP) == true
            connection = bus
        }
    }

    /**
     * One of this app's notifications clicked: the window comes forward; its
     * "Show in folder", the file manager on the download. Clicked or closed,
     * it is forgotten.
     */
    private fun signalled(signal: DBusMessage) {
        val id = signal.body.firstOrNull() as? Long ?: return
        if (id !in sent) return
        when (signal.member) {
            "NotificationClosed" -> forget(id)
            "ActionInvoked" -> {
                val folder = folders[id]
                forget(id)
                when (signal.body.getOrNull(1)) {
                    DEFAULT -> onActivate?.let(onMain)
                    SHOW_IN_FOLDER -> folder?.let { saved -> onShowInFolder?.let { show -> onMain { show(saved) } } }
                }
            }
        }
    }

    companion object {
        const val NAME = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        private const val ICON = "com.aimdi.xtanative"
        private const val DEFAULT = "default"
        private const val SHOW_IN_FOLDER = "show-in-folder"
        private const val EXPIRE_DEFAULT = -1
        private const val MATCH = "type='signal',interface='$NAME',path='$PATH'"
        private const val BODY_MARKUP = "body-markup"
        private const val MAX_KEPT = 64

        /** The desktop's own, on its session bus. */
        val desktop: DesktopNotifications by lazy { DesktopNotifications(DBusConnection::session) }

        /** [body] as Notify sends it: with `&`, `<` and `>` escaped where the desktop reads [markup], so a file named `<a> & b` shows as named. */
        fun bodyFor(body: String, markup: Boolean): String =
            if (!markup) body else body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /** The Notify actions: clicking the notification, and "Show in folder" where there is a [folder]. */
        fun actionsFor(folder: String?, folderLabel: String): List<String> =
            listOf(DEFAULT, "") + (if (folder != null) listOf(SHOW_IN_FOLDER, folderLabel) else emptyList())

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
 * the window is not the active one ([windowActive]) and Settings › General
 * › Desktop notifications is on ([enabled]); the window's own foot says it
 * as ever. [appName] heads it.
 */
@Composable
fun DesktopNotices(appName: String, enabled: Boolean, windowActive: () -> Boolean) {
    // Before the early return: a notification's "Show in folder" is clicked long after the window's own notice went.
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        DesktopNotifications.desktop.onShowInFolder = { saved -> scope.launch { FileManagerShow.show(saved) } }
        onDispose { DesktopNotifications.desktop.onShowInFolder = null }
    }
    val shown by KeepNotices.shown.collectAsState()
    val current = shown ?: return
    val text = noticeText(current.notice)
    val folderLabel = stringResource(Res.string.downloads_show_in_folder)
    val active by rememberUpdatedState(windowActive)
    val on by rememberUpdatedState(enabled)
    LaunchedEffect(current.serial) {
        val folder = (current.notice as? KeepNotice.SavedTo)?.savedUri
        if (on && DesktopNotifications.tells(current.notice) && !active()) DesktopNotifications.desktop.notify(appName, text, folder, folderLabel)
    }
}
