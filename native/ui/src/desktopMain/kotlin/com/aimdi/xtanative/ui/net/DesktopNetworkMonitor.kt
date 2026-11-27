package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * Whether the desktop has a connection, as the network portal says
 * (org.freedesktop.portal.NetworkMonitor, which every desktop's portal
 * answers and signals `changed` on); where there is no portal, whether the
 * kernel has a default route, looked at every [pollMs]. Online until told
 * otherwise: where nothing can tell, the app reads as it always did. The
 * portal also says whether the connection is [metered] (a phone's tether),
 * which the app's data saver follows, as Android's Data Saver.
 *
 * The portal's `changed` comes on the connection's reader thread, which also
 * delivers the answers to calls: asking there would wait for an answer only
 * that thread can bring, so the asking is handed to a thread of its own.
 */
class DesktopNetworkMonitor(
    private val connect: () -> DBusConnection?,
    private val routes: () -> Boolean? = RouteTable::read,
    private val pollMs: Long = POLL_MS,
) {
    private val mutable = MutableStateFlow(true)

    /** Whether there is a connection, undebounced; true where nothing says. */
    val online: StateFlow<Boolean> = mutable.asStateFlow()
    private val meteredNow = MutableStateFlow(false)

    /** Whether the connection is metered, as the portal says; false where it does not say, or there is no portal. */
    val metered: StateFlow<Boolean> = meteredNow.asStateFlow()
    private val started = AtomicBoolean(false)
    private val polling = AtomicBoolean(false)
    private val asking = Executors.newSingleThreadExecutor { Thread(it, "xta-native-network").apply { isDaemon = true } }

    @Volatile
    private var connection: DBusConnection? = null

    @Volatile
    private var closed = false

    /** Starts following on a thread of its own, once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        asking.execute(::follow)
    }

    /** [online], debounced ([debouncedOffline]), following from its first collection. */
    fun connection(): Flow<Boolean> = online.onStart { start() }.debouncedOffline()

    /** Follows now, on the caller's thread (tests). */
    fun followNow() {
        started.set(true)
        follow()
    }

    private fun follow() {
        val bus = connect()
        if (bus == null || !followPortal(bus)) {
            bus?.close()
            poll()
        }
    }

    /** Whether the portal answers; from then on its `changed` asks again. */
    private fun followPortal(bus: DBusConnection): Boolean {
        connection = bus
        bus.onSignal = { signal -> if (PortalNetwork.isChanged(signal)) runCatching { asking.execute { ask(bus) } } }
        bus.onClosed = { if (!closed) runCatching { asking.execute(::poll) } }
        bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(PortalNetwork.MATCH))
        return ask(bus)
    }

    /** Asks the portal and takes its word; false where it did not answer GetAvailable. */
    private fun ask(bus: DBusConnection): Boolean {
        val available = bus.call(PortalNetwork.PORTAL, PortalNetwork.PATH, PortalNetwork.IFACE, "GetAvailable")
            ?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull() as? Boolean ?: return false
        val connectivity = bus.call(PortalNetwork.PORTAL, PortalNetwork.PATH, PortalNetwork.IFACE, "GetConnectivity")
            ?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull()
        mutable.value = PortalNetwork.online(available, PortalNetwork.number(connectivity))
        val metered = bus.call(PortalNetwork.PORTAL, PortalNetwork.PATH, PortalNetwork.IFACE, "GetMetered")
            ?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull()
        meteredNow.value = PortalNetwork.metered(metered)
        return true
    }

    /** Looks at the route table every [pollMs] until closed; an unreadable table counts as online. */
    private fun poll() {
        if (!polling.compareAndSet(false, true)) return
        val look = Runnable {
            try {
                while (!closed) {
                    mutable.value = routes() ?: true
                    Thread.sleep(pollMs)
                }
            } catch (_: InterruptedException) {
                // Stopped.
            }
        }
        Thread(look, "xta-native-routes").apply { isDaemon = true }.start()
    }

    /** Stops following (tests). */
    fun close() {
        closed = true
        connection?.close()
        asking.shutdownNow()
    }

    companion object {
        /** How often the route table is looked at where there is no portal. */
        const val POLL_MS = 15_000L

        /** The desktop's own, on its session bus. */
        val desktop: DesktopNetworkMonitor by lazy { DesktopNetworkMonitor(DBusConnection::session) }
    }
}

/** The network portal's answers, as plain decisions. */
object PortalNetwork {
    const val PORTAL = "org.freedesktop.portal.Desktop"
    const val PATH = "/org/freedesktop/portal/desktop"
    const val IFACE = "org.freedesktop.portal.NetworkMonitor"
    const val MATCH = "type='signal',interface='$IFACE',member='changed',path='$PATH'"

    /** GetConnectivity's "local only": a network, but nothing beyond it. */
    private const val LOCAL_ONLY = 1L

    /**
     * Online unless the portal says there is no network ([available] false)
     * or only the local one; limited (2) and a captive portal (3) still count,
     * as a read may get through, and so does an unknown [connectivity].
     */
    fun online(available: Boolean, connectivity: Long?): Boolean = available && connectivity != LOCAL_ONLY

    /** GetMetered's answer: metered only when it says so plainly. */
    tailrec fun metered(value: Any?): Boolean = when (value) {
        is Variant -> metered(value.value)
        else -> value == true
    }

    /** Whether [signal] is the portal's `changed`. */
    fun isChanged(signal: DBusMessage): Boolean = signal.iface == IFACE && signal.member == "changed"

    /** A 'u' answer as a number, unwrapped; null for anything else. */
    tailrec fun number(value: Any?): Long? = when (value) {
        is Variant -> number(value.value)
        is Number -> value.toLong()
        else -> null
    }
}

/** The kernel's route tables (/proc/net/route, /proc/net/ipv6_route), as plain decisions. */
object RouteTable {
    private const val UP = 0x1L

    /** Whether either table has a default route that is up, off loopback; null where neither can be read. */
    fun read(): Boolean? {
        val v4 = runCatching { File("/proc/net/route").readText() }.getOrNull()
        val v6 = runCatching { File("/proc/net/ipv6_route").readText() }.getOrNull()
        if (v4 == null && v6 == null) return null
        return hasDefaultV4(v4.orEmpty()) || hasDefaultV6(v6.orEmpty())
    }

    /** /proc/net/route: a header, then Iface Destination Gateway Flags … Mask, in hex. */
    fun hasDefaultV4(table: String): Boolean = table.lineSequence().drop(1).map { it.trim().split(Regex("\\s+")) }.any { f ->
        f.size >= 8 && f[0] != "lo" && f[1] == "00000000" && f[7] == "00000000" && up(f[3])
    }

    /** /proc/net/ipv6_route: destination, its prefix length, … flags, device; no header. */
    fun hasDefaultV6(table: String): Boolean = table.lineSequence().map { it.trim().split(Regex("\\s+")) }.any { f ->
        f.size >= 10 && f[9] != "lo" && f[0].all { it == '0' } && f[0].length == 32 && f[1] == "00" && up(f[8])
    }

    private fun up(flags: String): Boolean = (flags.toLongOrNull(16) ?: 0L) and UP != 0L
}

/**
 * Online at once, offline only once it has lasted [offlineMs]: a moment's
 * drop (a Wi-Fi roam, a VPN reconnecting) leaves the screens alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.debouncedOffline(offlineMs: Long = OFFLINE_MS): Flow<Boolean> = distinctUntilChanged().transformLatest { online ->
    if (!online) delay(offlineMs)
    emit(online)
}.distinctUntilChanged()

/** How long the connection must be gone before the app says so. */
const val OFFLINE_MS = 2_000L
