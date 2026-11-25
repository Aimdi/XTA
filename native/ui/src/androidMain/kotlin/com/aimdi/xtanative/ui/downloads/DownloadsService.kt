package com.aimdi.xtanative.ui.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.graphics.drawable.Icon
import com.aimdi.xtanative.store.downloads.DownloadQueue
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.links.LauncherShortcut
import com.aimdi.xtanative.ui.generated.resources.downloads_downloading
import com.aimdi.xtanative.ui.generated.resources.downloads_title
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * The downloads' notification on Android: while a download runs or waits,
 * [DownloadsService] is in the foreground with it, so leaving the app does
 * not stall the files still arriving (Android freezes a process nobody
 * sees), and the notification says how far they are. It goes when the last
 * one ends, or its Cancel stops them all. Where Android shows no notification (13 and later, until the
 * reader allows them) the service still keeps the downloads going.
 */
class DownloadNotifications(private val context: Context, private val queue: DownloadQueue) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var channel = false

    /** Tapping the notification opens the downloads list. */
    private val openApp: PendingIntent? by lazy {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            // The app's own activity, told to show the downloads list ([LauncherShortcut.DOWNLOADS]).
            val downloads = it.setAction(LauncherShortcut.PREFIX + LauncherShortcut.DOWNLOADS.name)
            PendingIntent.getActivity(context, REQUEST, downloads, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    /** Follows the downloads from now on; a new notice at most every [UPDATE_MILLIS], the last one always shown. */
    fun start() {
        cancelAll = { scope.launch { queue.state.value.entries.filter { it.canCancel }.forEach { queue.cancel(it.id) } } }
        scope.launch {
            queue.state.map(DownloadNotices::of).distinctUntilChanged().conflate().collect { notice ->
                try {
                    if (notice == null) hide() else show(notice)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A notification that cannot be posted costs the downloads nothing.
                }
                delay(UPDATE_MILLIS)
            }
        }
    }

    private suspend fun show(notice: DownloadNotice) {
        val built = build(notice).also { latest = it }
        val service = DownloadsService.running
        when {
            service != null -> post(built)
            else -> try {
                context.startForegroundService(Intent(context, DownloadsService::class.java))
            } catch (_: RuntimeException) {
                // Android 12 lets an app in the background start no foreground service: the notification alone.
                post(built)
            }
        }
    }

    private fun hide() {
        latest = null
        DownloadsService.running?.end()
        runCatching { manager?.cancel(ID) }
    }

    private fun post(built: Notification) {
        runCatching { manager?.notify(ID, built) }
    }

    private suspend fun build(notice: DownloadNotice): Notification {
        val list = getString(Res.string.downloads_title)
        ensureChannel(list)
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (notice.single) notice.fileName else list)
            .setContentText(if (notice.single) getString(Res.string.downloads_downloading) else notice.fileName)
            .setNumber(notice.count)
            .setProgress(100, notice.percent ?: 0, notice.percent == null)
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel), getString(Res.string.cancel), DownloadsCancel.intent(context)).build())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    /** The channel, under the downloads list's name in the reader's language. */
    private fun ensureChannel(name: String) {
        if (channel) return
        channel = true
        runCatching { manager?.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }) }
    }

    internal companion object {
        const val ID = 4_202
        const val CHANNEL = "com.aimdi.xtanative.downloads"
        private const val REQUEST = 4_202
        private const val UPDATE_MILLIS = 700L

        /** What the service goes into the foreground with as it starts; null once nothing downloads. */
        @Volatile
        var latest: Notification? = null

        /** Stops every download that can be stopped; set once the downloads are followed. */
        @Volatile
        var cancelAll: (() -> Unit)? = null
    }
}

/** The notification's Cancel: every download running or waiting is stopped, as its own Cancel in the list stops one. */
class DownloadsCancel : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DownloadNotifications.cancelAll?.invoke()
    }

    internal companion object {
        fun intent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, DownloadsCancel::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Keeps the process while files download, in the foreground with
 * [DownloadNotifications]' notification and a partial wake lock, and ends
 * with the last of them or when Android's time for such work is up.
 */
class DownloadsService : Service() {
    private val wake: PowerManager.WakeLock? by lazy {
        getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XtaNative:downloads")?.apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    /** In the foreground at once, as Android demands of a service started so; ended when the downloads already have. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = DownloadNotifications.latest
        val foreground = foreground(current ?: placeholder())
        if (current == null || !foreground) end()
        return START_NOT_STICKY
    }

    /** Android 15 gives this kind of work six hours a day: the service steps back, and the downloads go on while the app is seen. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        end()
    }

    override fun onDestroy() {
        if (running === this) running = null
        runCatching { wake?.release() }
        super.onDestroy()
    }

    private fun placeholder(): Notification =
        Notification.Builder(this, DownloadNotifications.CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).build()

    private fun foreground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DownloadNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(DownloadNotifications.ID, notification)
        }
        runCatching { wake?.acquire(WAKE_MILLIS) }
        true
    } catch (_: RuntimeException) {
        false
    }

    internal fun end() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        runCatching { wake?.release() }
        stopSelf()
    }

    internal companion object {
        /** No download holds the phone awake longer than this without the service being started again. */
        private const val WAKE_MILLIS = 60 * 60 * 1000L

        @Volatile
        var running: DownloadsService? = null
    }
}
