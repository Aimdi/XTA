package com.aimdi.xtanative.networks.hackernews

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Hacker News addresses the app opens on its own screens, as XTA's
 * parseHackerNewsLink reads them: a story (`/item?id=8863`) or a user
 * (`/user?id=pg`) on news.ycombinator.com, with or without `www.`.
 */
object HnLinks {
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /** What [url] points at; null for the front page, other pages and other sites. */
    fun parse(url: String): HnLink? {
        val address = httpUrl(url) ?: return null
        if (address.host.lowercase().removePrefix("www.") != "news.ycombinator.com") return null
        val first = address.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        val id = address.queryParameter("id")?.trim().orEmpty().ifEmpty { null } ?: return null
        return when (first) {
            "item" -> id.toLongOrNull()?.let { HnLink.Story(it) }
            "user" -> HnLink.User(id)
            else -> null
        }
    }

    /** [input] as a web address; one without a scheme is taken as HTTPS, one with another scheme is none. */
    private fun httpUrl(input: String): HttpUrl? {
        val text = input.trim().ifEmpty { null } ?: return null
        return text.toHttpUrlOrNull() ?: if (scheme.containsMatchIn(text)) null else "https://$text".toHttpUrlOrNull()
    }
}
