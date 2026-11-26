package com.aimdi.xtanative.core.net

import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * The proxy every request of the app takes, switched at run time ([use]).
 * Every OkHttp client the app builds derives from [client] (XHttp's
 * `defaultClient`, which plugins derive theirs from, the media and picture
 * downloads, WebDAV and crash reports), so one switch moves all of them.
 *
 * Until something is [use]d, requests go where the platform's own
 * [ProxySelector] says, as a bare OkHttp client's do. Loopback (localhost,
 * 127.0.0.0/8, ::1) always goes straight: a proxy elsewhere cannot reach
 * this machine's own servers. A proxy's login is only ever sent to that
 * proxy: a 407 from it is answered ([ProxyLogin]), a SOCKS5 proxy is
 * answered through [socksAuthenticator], and a Proxy-Authorization header
 * never reaches a host the request went to straight ([ProxyLoginGuard]).
 */
object AppProxy {
    /** Requests to the platform's own [ProxySelector], as OkHttp's default does. */
    val PLATFORM = ProxyPolicy { scheme, host, port -> platformRoute(scheme, host, port) }

    @Volatile
    var policy: ProxyPolicy = PLATFORM
        private set

    /** The logins of the proxies [policy] has routed through, by "KIND host:port". */
    private val logins = ConcurrentHashMap<String, ProxyCredentials>()

    /** A host no route leads to: where an [ProxyRoute.Unusable] request is sent to fail. */
    internal const val UNUSABLE_HOST = "unusable-proxy.invalid"

    /** The client every other derives from; `--verbose` records its requests ([RequestTrace]). */
    val client: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(Selector)
        .proxyAuthenticator(ProxyLogin)
        .dns(UnusableDns)
        .addInterceptor(RequestTrace)
        .addNetworkInterceptor(ProxyLoginGuard)
        .build()

    /**
     * Routes every request by [policy] from now on. Idle pooled connections
     * go, so the next request opens its own by the new route; one already
     * under way finishes on the route it had.
     */
    fun use(policy: ProxyPolicy) {
        this.policy = policy
        logins.clear()
        client.connectionPool.evictAll()
    }

    /** The route a request to [url] takes; a URL that is no http(s) URL (a file, say) goes straight. */
    fun route(url: String): ProxyRoute = url.toHttpUrlOrNull()?.let(::route) ?: ProxyRoute.Direct

    fun route(url: HttpUrl): ProxyRoute = route(url.scheme, url.host, url.port)

    /** The route for [host]: straight for loopback, else [policy]'s, Unusable when the policy fails. */
    fun route(scheme: String, host: String, port: Int): ProxyRoute {
        if (isLoopback(host)) return ProxyRoute.Direct
        val route = runCatching { policy.route(scheme, host, port) }.getOrElse { ProxyRoute.Unusable }
        if (route is ProxyRoute.Via && route.credentials != null) logins[key(route.kind, NoProxyRules.normalized(route.host), route.port)] = route.credentials
        return route
    }

    /** The login for the proxy at [host]:[port], once a route through it was taken. */
    fun login(kind: ProxyKind, host: String, port: Int): ProxyCredentials? = logins[key(kind, NoProxyRules.normalized(host), port)]

    private fun key(kind: ProxyKind, host: String, port: Int) = "$kind $host:$port"

    /** Whether [host] is this machine: localhost, a name under .localhost, 127.0.0.0/8, ::1. */
    fun isLoopback(host: String): Boolean {
        val name = NoProxyRules.normalized(host)
        if (name == "localhost" || name.endsWith(".localhost")) return true
        return NoProxyRules.literal(name)?.let { InetAddress.getByAddress(it).isLoopbackAddress } ?: false
    }

    /** [route] as a [Proxy] for OkHttp; an unusable route points at [UNUSABLE_HOST], which no lookup finds. */
    internal fun proxyOf(route: ProxyRoute): Proxy = when (route) {
        ProxyRoute.Direct -> Proxy.NO_PROXY
        is ProxyRoute.Via -> Proxy(if (route.kind == ProxyKind.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(route.host, route.port))
        ProxyRoute.Unusable -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(UNUSABLE_HOST, 9))
    }

    private fun platformRoute(scheme: String, host: String, port: Int): ProxyRoute {
        val uri = runCatching { URI(scheme, null, host, port, "/", null, null) }.getOrNull() ?: return ProxyRoute.Direct
        val proxy = ProxySelector.getDefault()?.select(uri)?.firstOrNull() ?: return ProxyRoute.Direct
        val address = proxy.address() as? InetSocketAddress ?: return ProxyRoute.Direct
        return when (proxy.type()) {
            Proxy.Type.HTTP -> ProxyRoute.Via(ProxyKind.HTTP, address.hostString, address.port)
            Proxy.Type.SOCKS -> ProxyRoute.Via(ProxyKind.SOCKS, address.hostString, address.port)
            else -> ProxyRoute.Direct
        }
    }

    private object Selector : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = listOf(proxyOf(route(uri.scheme.orEmpty(), uri.host.orEmpty(), uri.port)))

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private object UnusableDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname == UNUSABLE_HOST) throw UnknownHostException("The proxy that was set cannot be used") else Dns.SYSTEM.lookup(hostname)
    }

    /** Answers an HTTP proxy's 407 with its own login, once; never another proxy's or a server's. */
    private object ProxyLogin : okhttp3.Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val proxy = route?.proxy?.takeIf { it.type() == Proxy.Type.HTTP } ?: return null
            val address = proxy.address() as? InetSocketAddress ?: return null
            val login = login(ProxyKind.HTTP, address.hostString, address.port) ?: return null
            val preemptive = response.header("Proxy-Authenticate").equals("OkHttp-Preemptive", ignoreCase = true)
            if (!preemptive && response.request.header("Proxy-Authorization") != null) return null
            if (!preemptive && response.challenges().none { it.scheme.equals("Basic", ignoreCase = true) }) return null
            return response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(login.user, login.password, Charsets.UTF_8)).build()
        }
    }

    /** Takes a Proxy-Authorization header off a request that is not going through that header's own HTTP proxy (a redirect sent straight, say). */
    internal object ProxyLoginGuard : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val header = request.header("Proxy-Authorization") ?: return chain.proceed(request)
            val proxy = chain.connection()?.route()?.proxy
            val address = proxy?.takeIf { it.type() == Proxy.Type.HTTP }?.address() as? InetSocketAddress
            val login = address?.let { login(ProxyKind.HTTP, it.hostString, it.port) }
            val own = login != null && header == Credentials.basic(login.user, login.password, Charsets.UTF_8)
            return chain.proceed(if (own) request else request.newBuilder().removeHeader("Proxy-Authorization").build())
        }
    }

    /**
     * The JDK's SOCKS5 login, which it asks the default [Authenticator] for
     * (install it with `Authenticator.setDefault`): the proxy's own login,
     * else an empty one, as the JDK would otherwise send this user's account
     * name. Anything but a SOCKS proxy gets nothing from it.
     */
    val socksAuthenticator: Authenticator = object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingProtocol?.startsWith("SOCKS", ignoreCase = true) != true) return null
            val login = requestingHost?.let { login(ProxyKind.SOCKS, it, requestingPort) }
            return PasswordAuthentication(login?.user.orEmpty(), login?.password.orEmpty().toCharArray())
        }
    }
}
