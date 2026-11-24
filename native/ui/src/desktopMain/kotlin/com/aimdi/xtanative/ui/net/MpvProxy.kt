package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.core.net.AppProxy
import com.aimdi.xtanative.core.net.ProxyKind
import com.aimdi.xtanative.core.net.ProxyRoute
import java.net.URLEncoder

/**
 * mpv's http-proxy for a source, from [AppProxy]'s route: libmpv fetches
 * videos through FFmpeg, not OkHttp, so each source is told its proxy before
 * it opens. FFmpeg only speaks HTTP proxies; straight is [NONE], which
 * FFmpeg takes as no proxy rather than falling back to $http_proxy, so None
 * means none for videos too. A SOCKS5 proxy (until videos have a bridge to
 * one) and an unusable proxy are [BLOCKED]: the video fails rather than
 * leaving without the proxy. FFmpeg still reads $no_proxy for hosts to send
 * straight past an HTTP proxy.
 */
object MpvProxy {
    /** No proxy: anything not starting with http:// is none to FFmpeg, and it is set, so $http_proxy is not read. */
    const val NONE = "none://"

    /** A proxy on this machine's discard port, which leads nowhere. */
    const val BLOCKED = "http://127.0.0.1:9"

    /** The http-proxy for [url], a source's address or a file's path. */
    fun option(url: String): String = option(AppProxy.route(url))

    fun option(route: ProxyRoute): String = when (route) {
        ProxyRoute.Direct -> NONE
        ProxyRoute.Unusable -> BLOCKED
        is ProxyRoute.Via -> if (route.kind != ProxyKind.HTTP) BLOCKED else buildString {
            append("http://")
            route.credentials?.let { append(escaped(it.user)).append(':').append(escaped(it.password)).append('@') }
            append(if (':' in route.host) "[${route.host}]" else route.host).append(':').append(route.port)
        }
    }

    private fun escaped(text: String) = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
