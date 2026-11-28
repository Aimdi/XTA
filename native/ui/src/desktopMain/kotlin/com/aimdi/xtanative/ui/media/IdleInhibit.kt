package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.ObjectPath
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.awt.Frame
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** An inhibition the desktop holds for the app, until it is let go ([InhibitBus.release]). */
internal sealed interface HeldInhibit {
    /** The portal's: its request object, which Close ends. */
    data class Portal(val handle: String) : HeldInhibit

    /** org.freedesktop.ScreenSaver's: the cookie UnInhibit takes back. */
    data class ScreenSaver(val cookie: Int) : HeldInhibit
}

/** What asks the desktop to keep the screen on, and lets it go: the session bus ([DBusInhibitBus]), or a test's. */
internal interface InhibitBus {
    /** The screen kept on; null when nothing on the bus would. */
    fun inhibit(reason: String): HeldInhibit?

    fun release(held: HeldInhibit)

    /** Whether [held] still stands: the connection it was asked on has not gone, ending it with it. */
    fun holds(held: HeldInhibit): Boolean = true
}

/**
 * Keeps the screensaver and the desktop's idle blanking away while a video
 * is heard playing in a window that shows ([playing], [check]): libmpv draws
 * through its software renderer, where mpv's own stop-screensaver does
 * nothing. A video paused, muted, stopped or let go, or every window hidden
 * (minimised, closed to the tray), lets the screen sleep again. What the bus
 * held goes with the app's connection to it, so a crash leaves nothing held.
 * Not thread-safe by itself: [IdleInhibit] runs it on one thread.
 */
internal class IdleInhibitor(private val bus: InhibitBus, private val shown: () -> Boolean, private val reason: String = REASON) {
    private val players = HashSet<Any>()

    /** The players let go ([letGo]): a word of theirs that comes after is too late. */
    private val gone: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
    private var held: HeldInhibit? = null

    /** Whether the screen is kept on now. */
    val holding: Boolean get() = held != null

    /** Whether anything heard is playing, so the windows are looked at again from time to time ([check]). */
    val anyPlaying: Boolean get() = players.isNotEmpty()

    /** [player] heard playing ([now]) or no longer; the screen kept on or let go to match. */
    fun playing(player: Any, now: Boolean) {
        if (now && player !in gone) players.add(player) else players.remove(player)
        check()
    }

    /** [player] let go for good: it never keeps the screen on again. */
    fun letGo(player: Any) {
        gone.add(player)
        playing(player, false)
    }

    /** Matches what is held to what plays and what shows: a window hidden or shown again since. */
    fun check() {
        held?.let { if (!bus.holds(it)) held = null }
        val playing = players.isNotEmpty()
        val wanted = IdleInhibitRules.wanted(playing, playing && shown())
        when (IdleInhibitRules.step(wanted, held != null)) {
            InhibitStep.TAKE -> held = bus.inhibit(reason)
            InhibitStep.LET_GO -> held?.let(bus::release).also { held = null }
            InhibitStep.KEEP -> Unit
        }
    }

    companion object {
        const val REASON = "Playing a video"
    }
}

internal enum class InhibitStep { TAKE, LET_GO, KEEP }

/** When the screen is kept on ([IdleInhibitor]), apart from the bus. */
internal object IdleInhibitRules {
    /** Kept on while something heard plays in a window that [shows]. */
    fun wanted(playing: Boolean, shows: Boolean): Boolean = playing && shows

    fun step(wanted: Boolean, holding: Boolean): InhibitStep = when {
        wanted && !holding -> InhibitStep.TAKE
        !wanted && holding -> InhibitStep.LET_GO
        else -> InhibitStep.KEEP
    }

    /** A video keeps the screen on while it is heard (not a GIF, its sound on, as the media keys hear it) and plays. */
    fun keepsAwake(heard: Boolean, playing: Boolean): Boolean = heard && playing
}

/**
 * The session bus's two ways of keeping the screen on: the desktop portal's
 * Inhibit for idle (flag 8), which a sandbox may use too, else
 * org.freedesktop.ScreenSaver's Inhibit, which idle daemons without a portal
 * backend for it (hypridle, xfce4-screensaver) read. Its own connection,
 * opened at the first video and kept: what it holds ends with it.
 */
internal class DBusInhibitBus(private val connect: () -> DBusConnection? = DBusConnection::session) : InhibitBus {
    private var connection: DBusConnection? = null

    private fun bus(): DBusConnection? = connection?.takeIf { it.open } ?: connect().also { connection = it }

    override fun inhibit(reason: String): HeldInhibit? {
        val bus = bus() ?: return null
        val portal = bus.call(PORTAL, PORTAL_PATH, INHIBIT, "Inhibit", "sua{sv}", listOf("", IDLE, mapOf("reason" to Variant("s", reason))))
        (portal?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull() as? ObjectPath)?.let { return HeldInhibit.Portal(it.path) }
        val saver = bus.call(SCREENSAVER, SCREENSAVER_PATH, SCREENSAVER, "Inhibit", "ss", listOf(APP, reason))
        return (saver?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull() as? Number)?.let { HeldInhibit.ScreenSaver(it.toInt()) }
    }

    override fun release(held: HeldInhibit) {
        val bus = connection?.takeIf { it.open } ?: return
        when (held) {
            is HeldInhibit.Portal -> bus.call(PORTAL, held.handle, REQUEST, "Close")
            is HeldInhibit.ScreenSaver -> bus.call(SCREENSAVER, SCREENSAVER_PATH, SCREENSAVER, "UnInhibit", "u", listOf(held.cookie))
        }
    }

    override fun holds(held: HeldInhibit): Boolean = connection?.open == true

    companion object {
        const val PORTAL = "org.freedesktop.portal.Desktop"
        const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        const val INHIBIT = "org.freedesktop.portal.Inhibit"
        const val REQUEST = "org.freedesktop.portal.Request"
        const val SCREENSAVER = "org.freedesktop.ScreenSaver"
        const val SCREENSAVER_PATH = "/org/freedesktop/ScreenSaver"
        const val APP = "XTA Native"

        /** The portal's flag for idle: the screensaver and blanking, not logout, user switching or suspend. */
        const val IDLE = 8
    }
}

/** The app's [IdleInhibitor], on a thread of its own: the bus is never waited for on the window's or mpv's thread. */
internal object IdleInhibit {
    private val thread = Executors.newSingleThreadScheduledExecutor { Thread(it, "xta-native-inhibit").apply { isDaemon = true } }
    private val inhibitor = IdleInhibitor(DBusInhibitBus(), ::windowShown)
    private var looking: ScheduledFuture<*>? = null

    /** [player] (a video's core) heard playing or no longer ([IdleInhibitRules.keepsAwake]). */
    fun playing(player: Any, now: Boolean) {
        onThread {
            inhibitor.playing(player, now)
            lookWhilePlaying()
        }
    }

    /** [player] let go: what its threads still say after is ignored, so it never keeps the screen on again. */
    fun letGo(player: Any) {
        onThread {
            inhibitor.letGo(player)
            lookWhilePlaying()
        }
    }

    /** [work] on the inhibitor's thread; a bus that fails is tried again at the next word or look. */
    private fun onThread(work: () -> Unit) {
        thread.execute { runCatching(work) }
    }

    /** While something plays, the windows are looked at again every few seconds: one minimised or closed to the tray lets the screen sleep. */
    private fun lookWhilePlaying() {
        if (inhibitor.anyPlaying && looking == null) {
            // A look that fails must not end the looks: a scheduled task that throws never runs again.
            looking = thread.scheduleWithFixedDelay({ runCatching { inhibitor.check() } }, LOOK_S, LOOK_S, TimeUnit.SECONDS)
        } else if (!inhibitor.anyPlaying) {
            looking?.cancel(false)
            looking = null
        }
    }

    /** Whether one of the app's windows shows on the screen: shown and not minimised. */
    private fun windowShown(): Boolean = Frame.getFrames().any { it.isShowing && (it.extendedState and Frame.ICONIFIED) == 0 }

    private const val LOOK_S = 5L
}
