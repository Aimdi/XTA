package com.aimdi.xtanative.core.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The desktop's `--verbose` record of the app's requests: each one's method,
 * where it went, its answer's status and how long it took, handed to [sink]
 * (the log) while one is set; without one a request passes untouched. Every
 * client derives from [AppProxy.client], which carries it, so the record
 * covers all of them. What it shows of a URL is [shown]: no query, no
 * fragment, no login, and no path piece long enough to be a key or token;
 * no header and no body is ever looked at.
 */
object RequestTrace : Interceptor {
    /** Where the lines go; null (the default) records nothing. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val out = sink ?: return chain.proceed(chain.request())
        val request = chain.request()
        val where = shown(request.url)
        val started = System.nanoTime()
        val took = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) }
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            out(line(request.method, where, "failed (${e.javaClass.simpleName})", took()))
            throw e
        }
        out(line(request.method, where, response.code.toString(), took()))
        return response
    }

    /** One request's line: `GET https://x.com/i/api/graphql/… 200 in 312 ms`. */
    fun line(method: String, where: String, outcome: String, millis: Long): String = "XTA Native: http $method $where $outcome in $millis ms"

    /** [url] as the record shows it: scheme, host, a port that is not the scheme's own, and the path with every long piece hidden. */
    fun shown(url: HttpUrl): String {
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        val path = url.pathSegments.joinToString("/") { if (it.length >= LONG_PIECE) HIDDEN else it }
        return "${url.scheme}://${url.host}$port/$path"
    }

    /** A path piece this long may be a key or a token (X's query ids are 22): hidden. */
    private const val LONG_PIECE = 32
    private const val HIDDEN = "…"
}
