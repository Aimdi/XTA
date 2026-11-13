package com.aimdi.xtanative.networks.immich

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The user's own Immich: the base its API hangs under and the API key it
 * takes. Only [of] makes one, so a client never holds what XTA would have
 * called "not configured".
 */
class ImmichServer private constructor(val baseUrl: HttpUrl, val apiKey: String) {
    /** [segments] under the instance's `/api`. */
    internal fun endpoint(vararg segments: String): HttpUrl =
        baseUrl.newBuilder().addPathSegment("api").apply { segments.forEach(::addPathSegment) }.build()

    companion object {
        /** The server at [address] with [apiKey], as the user typed them; null when either is unusable, so nothing is sent. */
        fun of(address: String, apiKey: String): ImmichServer? {
            val base = immichBaseUrl(address) ?: return null
            return if (apiKey.isBlank()) null else ImmichServer(base, apiKey)
        }
    }
}

private val HOST = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")
private val IP_ADDRESS = Regex("\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?")

/**
 * Whatever the user typed, as the base of their instance. Immich is nearly
 * always reached by address and port on a home network, and its own UI shows
 * the address with `/api` on the end, so `192.168.1.10:2283`, a trailing
 * slash and `https://photos.example.org/api` all mean the same thing. A path
 * prefix is kept: plenty of people put Immich behind a reverse proxy under one.
 */
fun immichBaseUrl(raw: String): HttpUrl? {
    val text = raw.trim().ifEmpty { return null }
    val url = (if ("://" in text) text else "${guessedScheme(text)}://$text").toHttpUrlOrNull() ?: return null
    if (!HOST.matches(url.host)) return null
    val segments = url.pathSegments.filter { it.isNotEmpty() }.dropLastWhile { it == "api" }
    return HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).apply { segments.forEach(::addPathSegment) }.build()
}

/**
 * The scheme of an address typed without one: a bare IP is somebody's own
 * network, where Immich is served over http; a name is assumed to be
 * reachable properly. XTA looked at the whole text, so `192.168.1.10:2283/api`
 * or a trailing slash made it https; only the host and port count here.
 */
private fun guessedScheme(text: String) = if (IP_ADDRESS.matches(text.substringBefore('/'))) "http" else "https"
