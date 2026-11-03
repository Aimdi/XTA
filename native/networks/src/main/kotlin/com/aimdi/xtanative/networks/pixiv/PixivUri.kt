package com.aimdi.xtanative.networks.pixiv

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * An address of any scheme read as its parts, for the links and redirects
 * Pixiv's pages carry: `pixiv://illusts/1` as much as `https://`. The scheme
 * is kept apart and the rest read as a web address, so the host and path
 * mean the same whatever the scheme.
 */
internal class PixivUri private constructor(val scheme: String, private val url: HttpUrl) {
    val host: String get() = url.host

    /** The path's segments, decoded, without the empty ones a doubled or trailing slash leaves. */
    val segments: List<String> get() = url.pathSegments.filter { it.isNotEmpty() }

    /** The path's segments as written, empty ones included. */
    val allSegments: List<String> get() = url.pathSegments

    val path: String get() = url.encodedPath

    fun query(name: String): String? = url.queryParameter(name)

    companion object {
        private val schemeShape = Regex("[a-z][a-z0-9+.-]*")

        fun parse(text: String): PixivUri? {
            val at = text.indexOf("://")
            if (at <= 0) return null
            val scheme = text.substring(0, at).lowercase()
            if (!schemeShape.matches(scheme)) return null
            return "https://${text.substring(at + 3)}".toHttpUrlOrNull()?.let { PixivUri(scheme, it) }
        }
    }
}
