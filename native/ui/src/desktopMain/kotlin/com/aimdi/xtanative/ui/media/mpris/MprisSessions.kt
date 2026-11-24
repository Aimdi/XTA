package com.aimdi.xtanative.ui.media.mpris

import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.AudioSessions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desktop's media session: while something is bound (a reading, a video
 * that is heard), the app is an MPRIS player on the session bus, so media
 * keys, `playerctl` and the desktop's media widgets reach it; with nothing
 * bound it leaves the bus's player list. [connect] reaches the bus; the
 * bus's work happens on a thread of its own, the bound player's on [onMain].
 */
class MprisService(
    private val connect: () -> DBusConnection?,
    private val onMain: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : AudioSessions {
    /** The app's name, as the controls show it. */
    @Volatile
    var identity: String = "XTA Native"

    /** Brings the window forward, when a control asks to. */
    @Volatile
    var onRaise: (() -> Unit)? = null

    override val pressedInVoiceCheck: Boolean = false

    private val lock = Any()
    private var owner: Any? = null
    private var binding: AudioSessionBinding? = null
    private var now = MprisNow()
    private var tracks = 0L

    /** When the last position came ([clockMs]); null until the bound thing reports one. */
    private var reportedAt: Long? = null

    /** Jumps are not told until then: a seek the controls asked for is on its way. */
    private var quietUntil = 0L

    /** Where the position jumped to (an in-app seek, a loop back to the start), for a Seeked signal still to send. */
    private var seekedTo: Long? = null

    private val bus: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-mpris").apply { isDaemon = true } }
    private var connection: DBusConnection? = null
    private var tried = false
    private var named = false
    private var told: MprisNow? = null

    private val sessionNow = MutableStateFlow<MprisNow?>(null)

    /** What is bound now, as the controls are told it; null while nothing is (the tray's Play/Pause follows it). */
    val session: StateFlow<MprisNow?> = sessionNow.asStateFlow()

    /** Plays or pauses what is bound, as a control's PlayPause does; nothing while nothing is bound. */
    fun playPause() {
        val (state, held) = synchronized(lock) { now to binding }
        held ?: return
        val act = when (Mpris.playPause(state)) {
            MprisAction.Play -> held.onPlay
            MprisAction.Pause -> held.onPause
            else -> held.onStop
        }
        act?.let(onMain)
    }

    override fun bind(title: String, artist: String?, binding: AudioSessionBinding) = bind(null, title, artist, binding)

    /** Binds [binding] for [owner], whose later [update] and [clear] reach it while nothing else was bound since. */
    fun bind(owner: Any?, title: String, artist: String?, binding: AudioSessionBinding, artUrl: String? = null) {
        synchronized(lock) {
            this.owner = owner
            this.binding = binding
            now = Mpris.offered(MprisNow(track = ++tracks, title = title, artist = artist, artUrl = artUrl), binding)
            reportedAt = null
            seekedTo = null
        }
        schedule()
    }

    override fun update(playing: Boolean, positionMs: Long, durationMs: Long?) = update(null, playing, positionMs, durationMs)

    /** What [owner]'s playing stands at; nothing when another is bound. */
    fun update(owner: Any?, playing: Boolean, positionMs: Long, durationMs: Long?) {
        val tell = synchronized(lock) {
            if (binding == null || (owner != null && this.owner !== owner)) return
            val before = now
            val at = clockMs()
            now = now.copy(playing = playing, positionMs = positionMs.coerceAtLeast(0), durationMs = durationMs)
            val jumped = reportedAt?.let { last -> at >= quietUntil && Mpris.jumped(before, at - last, now.positionMs) } == true
            reportedAt = at
            if (jumped) seekedTo = now.positionMs
            jumped || Mpris.changed(before, now).isNotEmpty()
        }
        if (tell) schedule()
    }

    /** The artwork [artUrl] for what [owner] bound, once found; nothing when another is bound since. */
    fun artFor(owner: Any, artUrl: String) {
        val tell = synchronized(lock) {
            if (this.owner !== owner || binding == null) return
            val before = now
            now = now.copy(artUrl = artUrl)
            before != now
        }
        if (tell) schedule()
    }

    override fun clear() = clear(null)

    /** Unbinds [owner] (anything for null); nothing when another is bound. */
    fun clear(owner: Any?) {
        synchronized(lock) {
            if (owner != null && this.owner !== owner) return
            this.owner = null
            binding = null
            now = MprisNow()
        }
        schedule()
    }

    /** Whether [owner] is what the controls reach now. */
    fun holds(owner: Any): Boolean = synchronized(lock) { this.owner === owner && binding != null }

    /** Waits (a few seconds at most) for the bus's work asked so far; for tests. */
    fun settle() {
        bus.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun schedule() {
        sessionNow.value = synchronized(lock) { now.takeIf { binding != null } }
        runCatching { bus.execute(::reconcile) }
    }

    /** On the bus's thread: the bus told what is bound now: the name taken or given back, and what changed. */
    private fun reconcile() {
        val (bound, state, seeked) = synchronized(lock) { Triple(binding != null, now, seekedTo).also { seekedTo = null } }
        if (!bound && !named) return
        val bus = connected() ?: return
        when {
            bound && !named -> {
                val answer = bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "RequestName", "su", listOf(Mpris.NAME, DO_NOT_QUEUE))
                named = (answer?.body?.firstOrNull() as? Long).let { it == PRIMARY_OWNER || it == ALREADY_OWNER }
                told = state
            }
            !bound -> {
                bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "ReleaseName", "s", listOf(Mpris.NAME))
                named = false
                told = null
            }
            else -> {
                val changed = Mpris.changed(told ?: MprisNow(), state)
                told = state
                if (changed.isNotEmpty()) bus.signal(Mpris.PATH, Mpris.PROPERTIES, "PropertiesChanged", "sa{sv}as", listOf(Mpris.PLAYER, changed, emptyList<String>()))
                seeked?.let { bus.signal(Mpris.PATH, Mpris.PLAYER, "Seeked", "x", listOf(it * 1000)) }
            }
        }
    }

    /** The bus, connected on first need; tried once again after it went away. */
    private fun connected(): DBusConnection? {
        connection?.takeIf { it.open }?.let { return it }
        if (connection != null) {
            connection = null
            named = false
        } else if (tried) {
            return null
        }
        tried = true
        return connect()?.also { bus ->
            bus.onCall = ::answer
            connection = bus
        }
    }

    /** On the connection's thread: one call from the controls answered, and what it asks done on [onMain]. */
    private fun answer(call: DBusMessage) {
        val bus = connection ?: return
        if (call.path != Mpris.PATH) return bus.fail(call, DBusConnection.UNKNOWN_METHOD, "No object at ${call.path}")
        val (state, bound) = synchronized(lock) { now to binding }
        val answer = Mpris.answer(call, state, identity)
        answer.error?.let { return bus.fail(call, it, call.member.orEmpty()) }
        bus.reply(call, answer.signature, answer.body)
        when (val action = answer.action) {
            null -> Unit
            MprisAction.Raise -> onRaise?.let(onMain)
            MprisAction.Play -> bound?.onPlay?.let(onMain)
            MprisAction.Pause -> bound?.onPause?.let(onMain)
            MprisAction.Stop -> bound?.onStop?.let(onMain)
            is MprisAction.Seek -> {
                val seek = bound?.onSeek ?: return
                synchronized(lock) {
                    if (binding === bound) {
                        now = now.copy(positionMs = action.positionMs)
                        // The player still reports where it was for a moment: that is no jump of its own.
                        quietUntil = clockMs() + SEEK_SETTLE_MS
                        reportedAt = null
                    }
                }
                onMain { seek(action.positionMs) }
                bus.signal(Mpris.PATH, Mpris.PLAYER, "Seeked", "x", listOf(action.positionMs * 1000))
            }
        }
    }

    private companion object {
        /** RequestName's DBUS_NAME_FLAG_DO_NOT_QUEUE, and its answers that mean the name is this connection's. */
        const val DO_NOT_QUEUE = 4L
        const val PRIMARY_OWNER = 1L
        const val ALREADY_OWNER = 4L

        /** How long after a seek the controls asked for the player's reports are not taken for jumps. */
        const val SEEK_SETTLE_MS = 1_000L
    }
}

/**
 * The picture at [url] as a file, where the app's image cache has it on
 * disk already (a video's poster its tile showed): a media widget reads
 * files, and nothing is fetched for it.
 */
internal object CachedArt {
    private val looking = java.util.concurrent.Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-art").apply { isDaemon = true } }

    /** [fileUrl] of [url] on a thread of its own, handed to [found] when there is one. */
    fun lookUp(url: String, found: (String) -> Unit) {
        runCatching { looking.execute { fileUrl(url)?.let(found) } }
    }

    fun fileUrl(url: String?): String? {
        url ?: return null
        return runCatching {
            val cache = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache ?: return null
            cache.openSnapshot(url)?.use { snapshot -> snapshot.data.toFile().takeIf { it.isFile }?.toURI()?.toString() }
        }.getOrNull()
    }
}

/** The app's one media session on the desktop's session bus. */
object MprisSessions {
    val service: MprisService by lazy { MprisService(DBusConnection::session) }
}
