package com.aimdi.xtanative.networks.instagram

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * An instagram.com link XTA opens in the app: a profile, by its username.
 * Posts, reels and Instagram's own pages are not one and open on the site,
 * as XTA's plugin_url.dart decides.
 */
data class InstagramLink(val handle: String) {
    companion object {
        private val notProfiles = setOf(
            "p", "reel", "reels", "stories", "explore", "accounts", "about", "legal", "direct",
            "tv", "share", "emails", "web", "lite", "developer", "directory", "privacy", "terms",
        )
        private val handlePattern = Regex("^[A-Za-z0-9._]{1,30}$")
        private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /** The profile [url] opens, or null when it is not a link to an Instagram profile. */
        fun parse(url: String): InstagramLink? {
            val link = webUrl(url)?.takeIf { it.host.removePrefix("www.") == "instagram.com" } ?: return null
            val first = link.pathSegments.firstOrNull { it.isNotEmpty() }?.lowercase() ?: return null
            return InstagramLink(first).takeIf { first !in notProfiles && handlePattern.matches(first) }
        }

        /** [input] as an http(s) URL, a bare "host/path" read as https; null for another scheme or no host. */
        private fun webUrl(input: String): HttpUrl? {
            val text = input.trim()
            if (text.isEmpty()) return null
            return text.toHttpUrlOrNull() ?: if (scheme.containsMatchIn(text)) null else "https://$text".toHttpUrlOrNull()
        }
    }
}
