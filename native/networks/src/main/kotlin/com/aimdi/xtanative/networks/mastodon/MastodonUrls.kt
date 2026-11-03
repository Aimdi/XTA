package com.aimdi.xtanative.networks.mastodon

/**
 * What XTA reads out of status and profile addresses: the id a status has on
 * its origin, whether two addresses are one post, and which links open in the
 * app rather than a browser.
 */
object MastodonUrls {
    private val digits = Regex("[0-9]+")
    private val handle = Regex("[A-Za-z0-9._]+")

    /** [value] when it is a plain http(s) address with a host and no credentials in it, else null. */
    fun web(value: String?): String? = WebUrl.parse(value?.trim().orEmpty())?.takeIf { it.isWeb }?.toString()

    /**
     * The id in a public status address, `/@user/123` or
     * `/users/user/statuses/123`: on the origin it is the id the API knows the
     * status by, whichever instance's id a federated copy carries.
     */
    fun statusId(url: String): String? {
        val parsed = WebUrl.parse(url.trim())?.takeIf { it.host.isNotEmpty() } ?: return null
        val segments = parsed.segments.filter { it.isNotEmpty() }
        if (segments.size >= 4 && segments[0].lowercase() == "users" && segments[2].lowercase() == "statuses") {
            return segments[3].takeIf(digits::matches)
        }
        if (segments.size < 2) return null
        val user = segments[0].removePrefix("@")
        return segments[1].takeIf { user.isNotEmpty() && digits.matches(it) }
    }

    /** Whether two status addresses name the same post, whatever their trailing slashes or case. */
    fun sameStatus(a: String, b: String): Boolean {
        fun key(raw: String): String? = WebUrl.parse(raw.trim())?.takeIf { it.host.isNotEmpty() }
            ?.let { "${it.host}${it.path.trimEnd('/')}".lowercase() }
        val left = key(a)
        return left != null && left == key(b)
    }

    /**
     * One key per post wherever it was read: its address without scheme,
     * query, fragment or trailing slash, the path's case kept. A post with no
     * usable address is keyed by author and id.
     */
    fun postKey(post: MastodonPost): String {
        val url = WebUrl.parse(post.url.trim())?.takeIf { it.host.isNotEmpty() } ?: return "${post.acct}:${post.id}"
        return url.host + (url.port?.let { ":$it" } ?: "") + url.path.trimEnd('/')
    }

    /**
     * The instance and id behind a pasted status address, exactly
     * `/@user/123` or `/users/user/statuses/123` on an http(s) origin
     * without credentials, so search can read it from where it lives.
     */
    fun searchTarget(input: String): StatusTarget? {
        val url = WebUrl.parse(input.trim())?.takeIf { it.isWeb } ?: return null
        val segments = url.segments.let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        val short = segments.size == 2 && segments[0].startsWith("@") && segments[0].length > 1
        val long = segments.size == 4 && segments[0] == "users" && segments[1].isNotEmpty() && segments[2] == "statuses"
        val id = segments.lastOrNull()?.takeIf { (short || long) && digits.matches(it) } ?: return null
        return StatusTarget(url.origin, id)
    }

    /** A status on its origin: [instance] is `scheme://host[:port]`. */
    data class StatusTarget(val instance: String, val id: String)

    /**
     * A profile or status address on one of [knownHosts] (host names, not
     * URLs: see [MastodonInstances.hosts]) as a link XTA Native opens itself.
     */
    fun link(url: String, knownHosts: Set<String>): MastodonLink? {
        if (knownHosts.isEmpty()) return null
        val parsed = httpUrl(url) ?: return null
        if (parsed.host !in knownHosts) return null
        val segments = parsed.segments.filter { it.isNotEmpty() }
        return if (segments.isEmpty()) null else linkOf(parsed, segments)
    }

    private fun linkOf(url: WebUrl, segments: List<String>): MastodonLink? {
        val host = url.host
        if (segments.size >= 4 && segments[0].lowercase() == "users" && segments[2].lowercase() == "statuses" && digits.matches(segments[3])) {
            return MastodonLink.Status("${segments[1]}@$host", segments[3], url.toString())
        }
        if (segments.size >= 2 && segments[0].lowercase() == "users") return MastodonLink.Profile("${segments[1]}@$host")
        val user = segments[0].takeIf { it.startsWith("@") }?.removePrefix("@")?.takeIf(handle::matches)?.lowercase() ?: return null
        if (segments.size >= 2 && digits.matches(segments[1])) return MastodonLink.Status("$user@$host", segments[1], url.toString())
        return MastodonLink.Profile("$user@$host")
    }

    /** An http(s) address, reading a bare `host/path` as https, as XTA's plugin links do. */
    private fun httpUrl(input: String): WebUrl? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val parsed = WebUrl.parse(text)
        return when {
            parsed != null && (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host.isNotEmpty() -> parsed
            parsed != null -> null
            else -> WebUrl.parse("https://$text")?.takeIf { it.host.isNotEmpty() }
        }
    }
}

/** A Mastodon address XTA Native opens on its own screen instead of a browser. */
sealed interface MastodonLink {
    data class Profile(val acct: String) : MastodonLink

    data class Status(val acct: String, val statusId: String, val url: String) : MastodonLink {
        /** Enough of the post to open its conversation, which reads the rest. */
        fun seed(): MastodonPost = MastodonPost(id = statusId, acct = acct, authorName = acct.substringBefore('@'), text = "", url = url)
    }
}
