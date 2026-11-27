package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.core.net.ProxyPolicy
import com.aimdi.xtanative.core.net.ProxyRoute
import com.aimdi.xtanative.core.net.ProxyUrl
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The desktop's proxy settings, as its portal (org.freedesktop.portal.ProxyResolver)
 * answers them. It is only told "scheme://host:port", never a path. Its
 * answers are kept [cacheMs]; so is "there is no portal on this bus", which
 * goes straight, as it always did. Of the proxies it lists, the first the
 * app can use is taken ("direct://" too); a list of none it can use is
 * [ProxyRoute.Unusable].
 *
 * A portal that does not answer in [timeoutMs] (still starting at login, or
 * stuck) is asked again after [retryMs], and meanwhile its last answer for
 * that address (else for any) stands. When it has never answered, requests
 * fail ([ProxyRoute.Unusable]) for the first [graceMs] rather than slip past
 * a proxy the desktop may set, then go straight. A bus that cannot be
 * reached is tried again later, ever less often, never given up on.
 *
 * [warm] asks once at start, off every request's thread, waiting the whole
 * grace: while it waits, a request (never one on the window's thread, which
 * is never held up by the portal) waits for its answer instead of failing,
 * so a portal slow at login only delays the first requests.
 */
class PortalProxyPolicy(
    private val connect: () -> DBusConnection?,
    private val cacheMs: Long = CACHE_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = RETRY_MS,
    private val graceMs: Long = GRACE_MS,
) : ProxyPolicy {
    private data class Kept(val route: ProxyRoute, val until: Long)

    private val kept = ConcurrentHashMap<String, Kept>()
    private val answered = ConcurrentHashMap<String, ProxyRoute>()
    private val connecting = Any()

    @Volatile
    private var bus: DBusConnection? = null

    @Volatile
    private var lastAnswer: ProxyRoute? = null

    @Volatile
    private var firstMiss: Long? = null

    private val warming = AtomicBoolean(false)
    private val firstAnswer = CompletableFuture<ProxyRoute>()

    private var nextConnect = 0L
    private var connectWait = retryMs

    override fun route(scheme: String, host: String, port: Int): ProxyRoute {
        val key = "${scheme.lowercase()}://${if (':' in host) "[$host]" else host}:$port"
        val now = clock()
        kept[key]?.takeIf { now < it.until }?.let { return it.route }
        if (EventQueue.isDispatchThread()) return answered[key] ?: lastAnswer ?: unknown(key, now)
        val (route, keepMs) = lookup(key, now)?.let { answer(key, it) to cacheMs } ?: (unknown(key, now) to retryMs)
        if (kept.size > MAX_KEPT) kept.clear()
        kept[key] = Kept(route, now + keepMs)
        return route
    }

    /**
     * Asks the portal for X's address on a thread of its own, waiting up to
     * the grace for its answer, so the first requests wait for it rather
     * than fail. Once only, while nothing has answered yet.
     */
    fun warm(key: String = "https://x.com:443") {
        if (lastAnswer != null || !warming.compareAndSet(false, true)) return
        thread(isDaemon = true, name = "xta-proxy-portal") {
            try {
                lookup(key, clock(), graceMs)?.let { answer(key, it) }
            } finally {
                warming.set(false)
                firstAnswer.complete(lastAnswer ?: ProxyRoute.Unusable)
            }
        }
    }

    /** The portal's route for [uri]; null when it gave no answer to go by. */
    private fun lookup(uri: String, now: Long, waitMs: Long = timeoutMs): ProxyRoute? {
        val connection = connection(now) ?: return ProxyRoute.Direct
        val reply = connection.call(PORTAL, PATH, IFACE, "Lookup", "s", listOf(uri), waitMs) ?: return null
        return routeOf(reply)
    }

    private fun answer(key: String, route: ProxyRoute): ProxyRoute {
        if (answered.size > MAX_KEPT) answered.clear()
        answered[key] = route
        lastAnswer = route
        firstAnswer.complete(route)
        return route
    }

    private fun unknown(key: String, now: Long): ProxyRoute {
        answered[key]?.let { return it }
        lastAnswer?.let { return it }
        val since = firstMiss ?: now.also { firstMiss = it }
        if (now - since >= graceMs) return ProxyRoute.Direct
        if (warming.get() && !EventQueue.isDispatchThread()) {
            runCatching { firstAnswer.get(graceMs - (now - since), TimeUnit.MILLISECONDS) }
            lastAnswer?.let { return it }
        }
        return ProxyRoute.Unusable
    }

    /** No bus in reach goes straight; it is tried again after a wait that doubles up to [cacheMs]. */
    private fun connection(now: Long): DBusConnection? = synchronized(connecting) {
        bus?.takeIf { it.open }?.let { return it }
        if (now < nextConnect) return null
        val fresh = connect()
        bus = fresh
        if (fresh == null) {
            nextConnect = now + connectWait
            connectWait = (connectWait * 2).coerceAtMost(cacheMs)
        } else {
            connectWait = retryMs
        }
        fresh
    }

    companion object {
        const val PORTAL = "org.freedesktop.portal.Desktop"
        const val PATH = "/org/freedesktop/portal/desktop"
        const val IFACE = "org.freedesktop.portal.ProxyResolver"
        const val CACHE_MS = 5 * 60_000L
        const val TIMEOUT_MS = 1_000L
        const val RETRY_MS = 5_000L
        const val GRACE_MS = 30_000L
        private const val MAX_KEPT = 512

        /** Errors that mean this bus has no proxy portal at all, which is an answer: straight. */
        private val NO_PORTAL = setOf(
            "org.freedesktop.DBus.Error.ServiceUnknown",
            "org.freedesktop.DBus.Error.NameHasNoOwner",
            "org.freedesktop.DBus.Error.UnknownMethod",
            "org.freedesktop.DBus.Error.UnknownInterface",
            "org.freedesktop.DBus.Error.UnknownObject",
        )

        /** The desktop's own, on its session bus. */
        val desktop: PortalProxyPolicy by lazy { PortalProxyPolicy(DBusConnection::session) }

        /** The route [reply] gives; null for an error that says nothing of the desktop's proxy. */
        fun routeOf(reply: DBusMessage): ProxyRoute? = when (reply.type) {
            DBusType.METHOD_RETURN -> (reply.body.firstOrNull() as? List<*>)?.let { first(it.filterIsInstance<String>()) }
            DBusType.ERROR -> ProxyRoute.Direct.takeIf { reply.errorName in NO_PORTAL }
            else -> null
        }

        /** The first of the portal's [proxies] the app can use; nothing listed goes straight. */
        fun first(proxies: List<String>): ProxyRoute {
            if (proxies.isEmpty()) return ProxyRoute.Direct
            return proxies.firstNotNullOfOrNull { proxy ->
                if (proxy.trim().lowercase().startsWith("direct://")) ProxyRoute.Direct else ProxyUrl.parse(proxy)?.takeIf { it !is ProxyRoute.Unusable }
            } ?: ProxyRoute.Unusable
        }
    }
}
