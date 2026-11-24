package com.aimdi.xtanative.core.net

/** Which kind of proxy a request goes through. */
enum class ProxyKind { HTTP, SOCKS }

/** A proxy's login; [toString] keeps the password out of logs. */
data class ProxyCredentials(val user: String, val password: String) {
    override fun toString(): String = "ProxyCredentials($user, ***)"
}

/** Where one request goes: straight to its host, through a proxy, or nowhere. */
sealed interface ProxyRoute {
    /** Straight to the host. */
    data object Direct : ProxyRoute

    /** Through the proxy at [host]:[port], logging in with [credentials] when it asks. */
    data class Via(val kind: ProxyKind, val host: String, val port: Int, val credentials: ProxyCredentials? = null) : ProxyRoute

    /**
     * Nowhere: a proxy was asked for that the app cannot use (an https://
     * or SOCKS4 proxy, an address that does not parse). The request fails
     * rather than leaving without the proxy the reader set.
     */
    data object Unusable : ProxyRoute
}

/** How requests are routed: the route for a request to [host]:[port] over [scheme] ("http", "https"). */
fun interface ProxyPolicy {
    fun route(scheme: String, host: String, port: Int): ProxyRoute

    companion object {
        /** Every request straight to its host. */
        val DIRECT = ProxyPolicy { _, _, _ -> ProxyRoute.Direct }
    }
}

/** Every request through [via], except the hosts [bypass] names. */
class ManualProxyPolicy(private val via: ProxyRoute, private val bypass: NoProxyRules = NoProxyRules.NONE) : ProxyPolicy {
    override fun route(scheme: String, host: String, port: Int): ProxyRoute = if (bypass.matches(host)) ProxyRoute.Direct else via
}
