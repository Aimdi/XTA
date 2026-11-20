package com.aimdi.xtanative.core.x.http

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps a request's credentials (its cookies, tokens and authorization) to
 * the site it was sent to while a redirect is followed: a hop to another
 * site, or from https down to http, goes without them. OkHttp itself only
 * takes away `Authorization`, and a network's session lives in `Cookie`.
 */
object CredentialGuard {
    /** The headers that say who the reader is somewhere, compared without case. */
    private val credentials = setOf("cookie", "authorization", "x-csrf-token", "x-guest-token", "x-twitter-auth-type", "x-ig-www-claim")

    /** Where a call began: its host, and whether it was https. */
    private data class Origin(val host: String, val https: Boolean)

    /** [client], with its redirects kept from carrying credentials elsewhere. */
    fun on(client: OkHttpClient): OkHttpClient {
        if (client.networkInterceptors.any { it is Strip }) return client
        return client.newBuilder().addInterceptor(Mark).addNetworkInterceptor(Strip).build()
    }

    /** Whether a hop to [host] (https or not) stays with the site [origin] began on: the same host or one under its domain. */
    internal fun sameSite(originHost: String, originHttps: Boolean, host: String, https: Boolean): Boolean {
        if (originHttps && !https) return false
        if (host == originHost) return true
        val domain = originHost.split('.').takeLast(2).joinToString(".")
        return host == domain || host.endsWith(".$domain")
    }

    /** Notes where each call began; a redirect's follow-up keeps the note. */
    private object Mark : Interceptor {
        override fun intercept(chain: Interceptor.Chain) = chain.request().let { request ->
            val marked = if (request.tag(Origin::class.java) != null) request
            else request.newBuilder().tag(Origin::class.java, Origin(request.url.host, request.isHttps)).build()
            chain.proceed(marked)
        }
    }

    /** Each hop sent: without the credentials when it leaves the site the call began on. */
    private object Strip : Interceptor {
        override fun intercept(chain: Interceptor.Chain) = chain.request().let { request ->
            val origin = request.tag(Origin::class.java)
            chain.proceed(if (origin == null || sameSite(origin.host, origin.https, request.url.host, request.isHttps)) request else stripped(request))
        }

        private fun stripped(request: Request): Request = request.newBuilder().apply {
            request.headers.names().filter { it.lowercase() in credentials }.forEach { removeHeader(it) }
        }.build()
    }
}
