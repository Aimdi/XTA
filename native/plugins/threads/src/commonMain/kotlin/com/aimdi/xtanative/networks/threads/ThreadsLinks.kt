package com.aimdi.xtanative.networks.threads

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A Threads web link XTA opens on its own screen. */
sealed interface ThreadsLink {
    data class Profile(val handle: String) : ThreadsLink

    /** A post; [handle] is empty for a short `/t/` link, whose author is known once its page is read. */
    data class Post(val url: String, val handle: String) : ThreadsLink

    /** What Threads' search page shows: posts for words, or with a tag's `serp_type` the posts filed under it. */
    data class Search(val terms: ThreadsSearchTerms) : ThreadsLink
}

/** Handles, post links and addresses on threads.com, read as XTA reads them. */
object ThreadsLinks {
    /** Where every read goes: `threads.net` and `m.` hosts each cost a redirect. */
    const val WEB = "https://www.threads.com"

    private val handleShape = Regex("[a-z0-9._]+")
    private val atHandleShape = Regex("[A-Za-z0-9._]+")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /**
     * A handle as a read wants it: no `@`, no URL around it, lower case. Readers
     * paste `@zuck`, `zuck` and `https://www.threads.com/@zuck` alike; null when
     * nothing usable is left.
     */
    fun normaliseHandle(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val link = trimmed.toHttpUrlOrNull()?.takeIf { it.host.contains("threads.") }
        val value = (if (link != null) segments(link).firstOrNull().orEmpty() else trimmed)
            .trimStart('@').trim().lowercase()
        return value.takeIf { it.isNotEmpty() && handleShape.matches(it) }
    }

    /** The short code in a post link, `/@x/post/CODE` or `/t/CODE`, on either host and whatever its query. */
    fun shortcode(url: String?): String? {
        val link = url?.trim()?.toHttpUrlOrNull()?.takeIf { it.host.contains("threads.") } ?: return null
        return segments(link).zipWithNext().firstOrNull { (segment, _) -> segment == "post" || segment == "t" }?.second
    }

    /** A post link as `www.threads.com` serves it, without the share sheet's query; null when it is not one. */
    fun canonicalPostUrl(postUrl: String): HttpUrl? {
        val link = postUrl.trim().toHttpUrlOrNull() ?: return null
        if (!link.host.contains("threads.") || segments(link).isEmpty()) return null
        return "$WEB${link.encodedPath}".toHttpUrlOrNull()
    }

    /** The destination behind Threads' `l.threads.com/?u=` outbound redirect, or [url] as it was. */
    fun unwrapOutbound(url: String): String {
        val link = url.toHttpUrlOrNull() ?: return url
        if (link.host != "l.threads.com" && link.host != "l.threads.net") return url
        return link.queryParameter("u")?.trim()?.ifEmpty { null } ?: url
    }

    /**
     * What a threads.net or threads.com link opens: a profile, a post, or a
     * search or topic page; null for anything else, such as Threads' own
     * pages (`/login`, `/settings`), which are no one's profile.
     */
    fun parse(url: String): ThreadsLink? {
        val link = webUrl(url)?.takeIf { hostIs(it.host, "threads.net", "threads.com") } ?: return null
        val parts = segments(link)
        val first = parts.firstOrNull() ?: return null
        // Share sheets hand out `/t/CODE`; Threads redirects it to the post.
        if (first.lowercase() == "t") {
            return parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { ThreadsLink.Post("$WEB/t/$it", "") }
        }
        if (first.lowercase() == "search") return searchLink(link)
        if (!first.startsWith("@") && first.lowercase() in SITE_PAGES) return null
        val handle = atHandle(first) ?: return null
        return if (parts.size >= 3 && parts[1] == "post" && parts[2].isNotEmpty()) {
            ThreadsLink.Post(link.toString(), handle)
        } else {
            ThreadsLink.Profile(handle)
        }
    }

    /**
     * Threads' public search page for [query]: its top posts, the most recent
     * with [recent], and with [tag] the posts filed under that topic.
     */
    fun searchUrl(query: String, tag: Boolean = false, recent: Boolean = false): HttpUrl = web(
        "/search",
        listOfNotNull("q" to query, "serp_type" to (if (tag) "tags" else "default"), ("filter" to "recent").takeIf { recent }),
    )

    /** A search page's link as the terms it runs: `serp_type=tags`, or a query starting with `#`, is a topic. */
    private fun searchLink(link: HttpUrl): ThreadsLink.Search? {
        val query = link.queryParameter("q")?.trim().orEmpty()
        val tag = link.queryParameter("serp_type") == "tags" || query.startsWith("#")
        val words = (if (tag) query.removePrefix("#").trim() else query).ifEmpty { return null }
        return ThreadsLink.Search(ThreadsSearchTerms(words, tag))
    }

    /** First path segments that are Threads' own pages, never an account written without its `@`. */
    private val SITE_PAGES = setOf("login", "logout", "settings", "activity", "saved", "liked", "insights", "about", "legal", "privacy", "terms", "intent", "accounts", "explore")

    /** [path] on threads.com with [query], encoded as XTA's requests encode it. */
    internal fun web(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl = address(WEB, path, query)

    /** [base] + [path], with [query] encoded the way Dart's `Uri` does: unreserved kept, a space as `+`. */
    internal fun address(base: String, path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl =
        "$base$path".toHttpUrl().newBuilder()
            .encodedQuery(query.joinToString("&") { (name, value) -> "${formEncode(name)}=${formEncode(value)}" }.ifEmpty { null })
            .build()

    /** A form body or query component as Dart's `Uri.encodeQueryComponent` writes it. */
    internal fun formEncode(text: String): String = percentEncode(text, unreservedQuery, spaceAsPlus = true)

    /** A path component as Dart's `Uri.encodeComponent` writes it. */
    internal fun componentEncode(text: String): String = percentEncode(text, unreservedComponent, spaceAsPlus = false)

    /** Whether [text] starts with a URI scheme, as Dart's `Uri.hasScheme` tells. */
    internal fun hasScheme(text: String): Boolean = scheme.containsMatchIn(text)

    /** [input] as a web address, with `https://` assumed when it names no scheme; null for any other scheme. */
    private fun webUrl(input: String): HttpUrl? {
        val text = input.trim()
        if (text.isEmpty()) return null
        text.toHttpUrlOrNull()?.let { return it }
        return if (hasScheme(text)) null else "https://$text".toHttpUrlOrNull()
    }

    private fun segments(url: HttpUrl): List<String> = url.pathSegments.filter { it.isNotEmpty() }

    private fun hostIs(host: String, vararg names: String): Boolean {
        val normalised = host.lowercase()
        return normalised in names || normalised.removePrefix("www.") in names
    }

    private fun atHandle(segment: String): String? =
        segment.removePrefix("@").takeIf { it.isNotEmpty() && atHandleShape.matches(it) }?.lowercase()

    private const val alphanumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val unreservedQuery = "$alphanumeric-._~"
    private const val unreservedComponent = "$alphanumeric-_.!~*'()"

    private fun percentEncode(text: String, unreserved: String, spaceAsPlus: Boolean): String =
        text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val char = (byte.toInt() and 0xff).toChar()
            when {
                char in unreserved -> char.toString()
                char == ' ' && spaceAsPlus -> "+"
                else -> "%%%02X".format(byte.toInt() and 0xff)
            }
        }
}
