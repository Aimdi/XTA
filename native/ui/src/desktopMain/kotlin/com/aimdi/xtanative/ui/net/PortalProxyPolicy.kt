package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.core.net.ProxyPolicy
import com.aimdi.xtanative.core.net.ProxyRoute
import com.aimdi.xtanative.core.net.ProxyUrl
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusType
import java.util.concurrent.ConcurrentHashMap

/**
 * The desktop's proxy settings, as its portal (org.freedesktop.portal.ProxyResolver)
 * answers them. It is only told "scheme://host:port", never a path, and its
 * answer for each is kept [cacheMs]; one that does not come in [timeoutMs],
 * or a desktop without the portal, goes straight, as it always did. Of the
 * proxies it lists, the first the app can use is taken ("direct://" too);
 * a list of none it can use is [ProxyRoute.Unusable].
 */
class PortalProxyPolicy(
    private val connect: () -> DBusConnection?,
    private val cacheMs: Long = CACHE_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProxyPolicy {
    private data class Kept(val route: ProxyRoute, val at: Long)

    private val kept = ConcurrentHashMap<String, Kept>()
    private val connecting = Any()

    @Volatile
    private var bus: DBusConnection? = null

    @Volatile
    private var absent = false

    override fun route(scheme: String, host: String, port: Int): ProxyRoute {
        val key = "${scheme.lowercase()}://${if (':' in host) "[$host]" else host}:$port"
        val now = clock()
        kept[key]?.takeIf { now - it.at < cacheMs }?.let { return it.route }
        val route = lookup(key)
        if (kept.size > MAX_KEPT) kept.clear()
        kept[key] = Kept(route, now)
        return route
    }

    private fun lookup(uri: String): ProxyRoute {
        val connection = connection() ?: return ProxyRoute.Direct
        val answer = connection.call(PORTAL, PATH, IFACE, "Lookup", "s", listOf(uri), timeoutMs)
            ?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull() as? List<*> ?: return ProxyRoute.Direct
        return first(answer.filterIsInstance<String>())
    }

    private fun connection(): DBusConnection? = synchronized(connecting) {
        bus?.takeIf { it.open } ?: if (absent) null else connect().also { bus = it; absent = it == null }
    }

    companion object {
        const val PORTAL = "org.freedesktop.portal.Desktop"
        const val PATH = "/org/freedesktop/portal/desktop"
        const val IFACE = "org.freedesktop.portal.ProxyResolver"
        const val CACHE_MS = 5 * 60_000L
        const val TIMEOUT_MS = 1_000L
        private const val MAX_KEPT = 512

        /** The desktop's own, on its session bus. */
        val desktop: PortalProxyPolicy by lazy { PortalProxyPolicy(DBusConnection::session) }

        /** The first of the portal's [proxies] the app can use; nothing listed goes straight. */
        fun first(proxies: List<String>): ProxyRoute {
            if (proxies.isEmpty()) return ProxyRoute.Direct
            return proxies.firstNotNullOfOrNull { proxy ->
                if (proxy.trim().lowercase().startsWith("direct://")) ProxyRoute.Direct else ProxyUrl.parse(proxy)?.takeIf { it !is ProxyRoute.Unusable }
            } ?: ProxyRoute.Unusable
        }
    }
}
