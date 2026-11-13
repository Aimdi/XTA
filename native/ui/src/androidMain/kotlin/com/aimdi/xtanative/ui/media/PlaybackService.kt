package com.aimdi.xtanative.ui.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent

/** The framework's drawings the media notification uses, so the app carries none of its own. */
internal object PlaybackIcons {
    const val SMALL = android.R.drawable.ic_media_play
    const val PLAY = android.R.drawable.ic_media_play
    const val PAUSE = android.R.drawable.ic_media_pause
    const val STOP = android.R.drawable.ic_menu_close_clear_cancel

    fun of(control: PlaybackControl): Int = when (control) {
        PlaybackControl.PLAY -> PLAY
        PlaybackControl.PAUSE -> PAUSE
        PlaybackControl.STOP -> STOP
    }
}

/**
 * The media notification (audio_service's on Android): what plays and its
 * controls, on the lock screen too. While it plays, [PlaybackService] posts
 * it in the foreground, which keeps the process reading or playing when the
 * app is left; paused, it stays but can be swiped away, which stops
 * (androidStopForegroundOnPause, as XTA sets it). On the main thread only.
 */
internal class PlaybackNotifications(private val context: Context, private val session: MediaSession) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    /** Tapping the notification opens the app; asked for once something plays, not as the app starts. */
    val openApp: PendingIntent? by lazy {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private var shown: PlaybackNotice? = null
    private var notification: Notification? = null
    private var channel = false

    /** Shows [notice]; the same one twice is posted once, as a podcast reports its place twice a second. */
    fun show(notice: PlaybackNotice, labels: PlaybackLabels?) {
        if (notice == shown) return
        shown = notice
        val built = build(notice, labels).also { notification = it }
        val service = PlaybackService.running
        when {
            !notice.playing -> {
                service?.detach()
                post(built)
            }
            service != null -> if (!service.foreground(built)) post(built)
            else -> start(built)
        }
    }

    /** Takes the notification away and lets the service end. */
    fun hide() {
        shown = null
        notification = null
        PlaybackService.running?.end()
        runCatching { manager?.cancel(ID) }
    }

    /**
     * The service has started: in the foreground with what is shown, as
     * Android demands of a service started so, then as that stands: ended
     * when nothing is shown any more, or Android refused the foreground.
     */
    fun started(service: PlaybackService) {
        val current = notification
        val foreground = service.foreground(current ?: build(PlaybackNotice("", null, playing = false, controls = emptyList()), null))
        when {
            current == null -> service.end()
            !foreground -> {
                service.end()
                post(current)
            }
            shown?.playing != true -> service.detach()
        }
    }

    private fun start(built: Notification) {
        try {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
        } catch (_: RuntimeException) {
            // Android 12 lets an app in the background start no foreground service: the notification alone.
            post(built)
        }
    }

    private fun post(built: Notification) {
        runCatching { manager?.notify(ID, built) }
    }

    private fun build(notice: PlaybackNotice, labels: PlaybackLabels?): Notification {
        ensureChannel()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(PlaybackIcons.SMALL)
            .setContentTitle(notice.title)
            .setContentText(notice.artist)
            .setContentIntent(openApp)
            .setDeleteIntent(PlaybackButtons.intent(context, PlaybackControl.STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(notice.playing)
        notice.controls.forEach { control ->
            val icon = Icon.createWithResource(context, PlaybackIcons.of(control))
            builder.addAction(Notification.Action.Builder(icon, labels?.of(control).orEmpty(), PlaybackButtons.intent(context, control)).build())
        }
        val style = Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*IntArray(notice.controls.size) { it })
        return builder.setStyle(style).build()
    }

    /** The channel, named after the app, which is the one word that needs no translation (as XTA names its own). */
    private fun ensureChannel() {
        if (channel) return
        channel = true
        val name = context.applicationInfo.loadLabel(context.packageManager)
        runCatching { manager?.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }) }
    }

    companion object {
        const val ID = 4_201
        const val CHANNEL = "com.aimdi.xtanative.playback"
    }
}

/**
 * Keeps the process while something plays, as audio_service's AudioService
 * does: in the foreground with the media notification and a partial wake
 * lock while it plays, out of it when paused, ended when nothing is bound.
 */
class PlaybackService : Service() {
    private val wake: PowerManager.WakeLock? by lazy {
        getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XtaNative:playback")?.apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        frameworkSessions(this).notifications.started(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        runCatching { wake?.release() }
        super.onDestroy()
    }

    /** In the foreground with [notification]; false when Android refused it, the app being in the background. */
    internal fun foreground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PlaybackNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(PlaybackNotifications.ID, notification)
        }
        runCatching { wake?.acquire() }
        true
    } catch (_: RuntimeException) {
        false
    }

    /** Out of the foreground, its notification left where it is. */
    internal fun detach() {
        stopForeground(STOP_FOREGROUND_DETACH)
        runCatching { wake?.release() }
    }

    internal fun end() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        runCatching { wake?.release() }
        stopSelf()
    }

    internal companion object {
        @Volatile
        var running: PlaybackService? = null
    }
}

/** The media notification's buttons and its swipe, and a headset's buttons: each presses its control on what is bound. */
class PlaybackButtons : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            frameworkSessions(context).mediaButton(event)
            return
        }
        val control = intent.action?.removePrefix(PREFIX)?.let { name -> PlaybackControl.entries.firstOrNull { it.name == name } } ?: return
        frameworkSessions(context).press(control)
    }

    internal companion object {
        private const val PREFIX = "com.aimdi.xtanative.playback."

        fun intent(context: Context, control: PlaybackControl): PendingIntent = PendingIntent.getBroadcast(
            context,
            control.ordinal,
            Intent(context, PlaybackButtons::class.java).setAction(PREFIX + control.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
