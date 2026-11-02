package com.aimdi.xtanative.networks.tiktok

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A tiktok.com link XTA opens in the app, as its plugin_url.dart decides: a
 * profile (`/@name`) or one of its videos (`/@name/video/id`). Short links
 * (vm., vt.) and TikTok's own pages open on the site.
 */
sealed interface TikTokLink {
    data class Profile(val handle: String) : TikTokLink

    data class Video(val id: String, val handle: String) : TikTokLink

    companion object {
        private val notProfiles = setOf(
            "foryou", "following", "live", "music", "tag", "search", "explore",
            "discover", "upload", "messages", "activity", "about", "legal",
        )
        private val handlePattern = Regex("^[A-Za-z0-9._]+$")
        private val digits = Regex("^\\d+$")
        private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /**
         * The profile or video [url] opens, or null when it is neither. An
         * account is named only with its "@": XTA also took a bare first
         * segment for one, so `www.tiktok.com/t/CODE` (a short link) and
         * `/embed/…` opened as accounts named "t" and "embed".
         */
        fun parse(url: String): TikTokLink? {
            val link = webUrl(url)?.takeIf { isTikTok(it.host) } ?: return null
            val segments = link.pathSegments.filter { it.isNotEmpty() }
            val handle = segments.firstOrNull()?.let(::atHandle)?.takeUnless { it in notProfiles } ?: return null
            val id = segments.getOrNull(2)?.takeIf { segments[1] == "video" && digits.matches(it) }
            return if (id != null) Video(id, handle) else Profile(handle)
        }

        private fun atHandle(segment: String): String? =
            segment.takeIf { it.startsWith("@") }?.drop(1)?.takeIf(handlePattern::matches)?.lowercase()

        private fun isTikTok(host: String): Boolean =
            host != "vm.tiktok.com" && host != "vt.tiktok.com" && (host == "tiktok.com" || host.endsWith(".tiktok.com"))

        /** [input] as an http(s) URL, a bare "host/path" read as https; null for another scheme or no host. */
        private fun webUrl(input: String): HttpUrl? {
            val text = input.trim()
            if (text.isEmpty()) return null
            return text.toHttpUrlOrNull() ?: if (scheme.containsMatchIn(text)) null else "https://$text".toHttpUrlOrNull()
        }
    }
}
