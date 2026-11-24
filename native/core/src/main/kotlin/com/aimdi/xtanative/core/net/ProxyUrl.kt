package com.aimdi.xtanative.core.net

import java.net.URI
import java.net.URLDecoder

/**
 * A proxy written as a URL, the way curl and the desktop write them:
 * "http://host:port", "socks5://user:password@host:port", or a bare
 * "host:port", which is an HTTP proxy. "socks5h" and "socks" are SOCKS5 too
 * (names always go to the proxy to look up, never to this machine's DNS).
 * An https:// proxy, SOCKS4 or anything else is [ProxyRoute.Unusable]: the
 * app cannot speak it, and must not leave without it.
 */
object ProxyUrl {
    /** curl's port for a proxy whose address names none. */
    const val DEFAULT_PORT = 1080

    /** [text] as a route; null when it is blank (no proxy). */
    fun parse(text: String?): ProxyRoute? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val full = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = runCatching { URI(full) }.getOrNull() ?: return ProxyRoute.Unusable
        val kind = when (uri.scheme?.lowercase()) {
            "http" -> ProxyKind.HTTP
            "socks", "socks5", "socks5h" -> ProxyKind.SOCKS
            else -> return ProxyRoute.Unusable
        }
        val host = uri.host?.let(NoProxyRules::normalized)?.takeIf { it.isNotEmpty() } ?: return ProxyRoute.Unusable
        val port = uri.port.takeIf { it > 0 } ?: if (uri.port == -1) DEFAULT_PORT else return ProxyRoute.Unusable
        if (port > 65535) return ProxyRoute.Unusable
        return ProxyRoute.Via(kind, host, port, credentials(uri.rawUserInfo))
    }

    private fun credentials(userInfo: String?): ProxyCredentials? {
        if (userInfo.isNullOrEmpty()) return null
        val user = decoded(userInfo.substringBefore(':'))
        val password = decoded(userInfo.substringAfter(':', ""))
        return ProxyCredentials(user, password)
    }

    // A '+' in a password stays a '+': only %xx escapes are undone.
    private fun decoded(text: String): String = runCatching { URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(text)
}

/**
 * The proxy the environment names, read as curl reads it: https_proxy (or
 * HTTPS_PROXY) for https, http_proxy for http (never HTTP_PROXY, which a CGI
 * request's "Proxy:" header can set), all_proxy (or ALL_PROXY) for either
 * otherwise; no_proxy (or NO_PROXY) lists the hosts that go straight
 * ([NoProxyRules]). Lower case wins over upper case; a blank value is no value.
 */
class EnvProxyPolicy(private val env: Map<String, String>) : ProxyPolicy {
    private fun value(vararg names: String): String? = names.firstNotNullOfOrNull { env[it]?.takeIf(String::isNotBlank) }

    private val all = value("all_proxy", "ALL_PROXY")
    private val http = ProxyUrl.parse(value("http_proxy") ?: all)
    private val https = ProxyUrl.parse(value("https_proxy", "HTTPS_PROXY") ?: all)
    private val bypass = NoProxyRules.parse(value("no_proxy", "NO_PROXY"))

    /** Whether the environment names any proxy. */
    val isSet: Boolean get() = http != null || https != null

    override fun route(scheme: String, host: String, port: Int): ProxyRoute {
        val proxy = when (scheme.lowercase()) {
            "https", "wss" -> https
            else -> http
        } ?: return ProxyRoute.Direct
        return if (bypass.matches(host)) ProxyRoute.Direct else proxy
    }
}
