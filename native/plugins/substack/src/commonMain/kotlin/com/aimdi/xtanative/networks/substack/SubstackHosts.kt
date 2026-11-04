package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a pasted post lives: its publication's address and its slug. */
data class SubstackPostRef(val base: HttpUrl, val slug: String)

/**
 * Which hosts are a publication, as XTA's substack_models.dart decides it.
 *
 * A publication may live at `{subdomain}.substack.com` or on its own domain.
 * One that left Substack can leave its old `{subdomain}.substack.com` twin
 * behind; one found without Substack's JSON only guesses its id from its
 * host, and on a host like `newsletter.example.com` that guess is a prefix
 * many sites share, so such a host is its own id there.
 */
object SubstackHosts {
    /** Hosts that are Substack itself, not a publication. */
    val SERVICE_HOSTS = setOf("substack.com", "www.substack.com", "open.substack.com")

    private val NOT_SUBSTACK = setOf(
        "medium.com", "www.medium.com", "x.com", "www.x.com", "twitter.com", "www.twitter.com",
        "ghost.org", "www.ghost.org", "beehiiv.com", "www.beehiiv.com", "buttondown.com", "www.buttondown.com",
    )
    private val NOT_SUBSTACK_SUFFIXES = listOf(".medium.com", ".beehiiv.com", ".ghost.io", ".buttondown.com")
    private val handle = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

    /** Sites that look like a newsletter but are never Substack's. */
    fun isObviousNonSubstack(host: String): Boolean {
        val h = host.lowercase()
        return h in NOT_SUBSTACK || NOT_SUBSTACK_SUFFIXES.any { h.endsWith(it) }
    }

    /** A `{subdomain}.substack.com` host, not one of Substack's own. */
    fun isPublicationHost(host: String): Boolean {
        val h = host.lowercase()
        return h.endsWith(".substack.com") && h !in SERVICE_HOSTS
    }

    /** Substack itself: `substack.com`, `www.substack.com`, `open.substack.com`. */
    fun isServiceHost(host: String): Boolean = host.lowercase() in SERVICE_HOSTS

    /** Hosts that differ only by `www.` are one publication. */
    fun same(a: String, b: String): Boolean = withoutWww(a.lowercase()) == withoutWww(b.lowercase())

    /** A follow id no other site can hold: the host without `www.`. */
    fun hostId(baseUrl: String): String = withoutWww(hostOf(baseUrl))

    /** A custom domain with more than two labels without `www.`, like `newsletter.example.com`: its first label is a prefix. */
    fun isDeepCustomHost(host: String): Boolean {
        if (isPublicationHost(host) || isServiceHost(host)) return false
        return withoutWww(host.lowercase()).split('.').count { it.isNotEmpty() } > 2
    }

    /**
     * The id of a publication found without Substack's JSON: on a deep host the
     * whole host, on an apex domain its first label, which is usually the
     * Substack subdomain.
     */
    fun guessedId(base: HttpUrl): String = if (isDeepCustomHost(base.host)) withoutWww(base.host) else subdomainOf(base.host)

    /** The first label of a custom domain: `www.platformer.news` is `platformer`. */
    fun registrableLabel(host: String): String? {
        val parts = withoutWww(host.lowercase()).split('.').filter { it.isNotEmpty() }
        return if (parts.size < 2) null else parts.first()
    }

    /** A leftover `www` or host label, never a publication's title. */
    fun nameLooksGeneric(name: String): Boolean = name.trim().lowercase().let { it.isEmpty() || it == "www" || it == "www2" }

    /** A publication's subdomain from its host: the Substack subdomain, else a custom domain's first label. */
    fun subdomainOf(host: String): String {
        val h = host.lowercase()
        if (h.endsWith(".substack.com")) return withoutWww(h.removeSuffix(".substack.com"))
        return registrableLabel(h) ?: h.substringBefore('.')
    }

    /** `@casey` or `substack.com/@casey`: a profile, not a publication host. */
    fun profileHandle(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("@") && '/' !in trimmed.substring(1) && '.' !in trimmed.substring(1)) {
            return trimmed.substring(1).trim().takeIf(::isHandle)
        }
        val url = userUrl(trimmed) ?: return null
        if (!isServiceHost(url.host) || url.host == "open.substack.com") return null
        val first = segments(url).firstOrNull() ?: return null
        return first.takeIf { it.startsWith("@") }?.substring(1)?.takeIf(::isHandle)
    }

    /**
     * The publication a typed handle or pasted address names. Share links and
     * profiles become `{name}.substack.com`; Substack's own pages are none; a
     * custom domain is kept as it is, for the client to find out whether it
     * still is Substack's.
     */
    fun base(input: String): HttpUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        profileHandle(trimmed)?.let { return https("$it.substack.com") }
        val url = userUrl(trimmed) ?: return null
        if (isObviousNonSubstack(url.host)) return null
        val parts = segments(url)
        return when {
            url.host == "open.substack.com" ->
                if (parts.size >= 2 && parts[0] == "pub" && isHandle(parts[1])) https("${parts[1]}.substack.com") else null
            isServiceHost(url.host) -> null
            else -> https(url.host)
        }
    }

    /** A pasted post: `{host}/p/{slug}`, or the share host's `open.substack.com/pub/{name}/p/{slug}`. */
    fun postRef(input: String): SubstackPostRef? {
        val url = userUrl(input.trim()) ?: return null
        val parts = segments(url)
        if (url.host == "open.substack.com") {
            val shared = parts.size >= 4 && parts[0] == "pub" && parts[2] == "p" && isHandle(parts[1]) && isPostSlug(parts[3])
            return if (shared) https("${parts[1]}.substack.com")?.let { SubstackPostRef(it, parts[3]) } else null
        }
        if (isServiceHost(url.host) || isObviousNonSubstack(url.host)) return null
        if (parts.size >= 2 && parts[0] == "p" && isPostSlug(parts[1])) return https(url.host)?.let { SubstackPostRef(it, parts[1]) }
        return null
    }

    /**
     * The hosts to read a followed publication from: its saved host first (a
     * living custom domain still serves JSON there), then the `www` twin, then
     * `{subdomain}.substack.com`, so a custom domain that went dark, or a
     * follow saved as `www`, still reaches its archive on Substack.
     */
    fun fetchBases(publication: SubstackPublication): List<HttpUrl> = hinted(publication)?.let { candidates(it, publication.subdomain) }.orEmpty()

    /** The host [publication] was followed at, else its Substack subdomain. */
    internal fun hinted(publication: SubstackPublication): HttpUrl? =
        publication.baseUrl.toHttpUrlOrNull() ?: publication.subdomain.takeIf { it.isNotEmpty() }?.let { https("$it.substack.com") }

    /** The hosts that are the pasted or followed site itself, not a `{label}.substack.com` twin. */
    fun requested(base: HttpUrl, subdomainHint: String? = null): List<HttpUrl> =
        candidates(base, subdomainHint).filter { !isLeftoverTwin(it.host, base.host) }

    /** The `{label}.substack.com` twins of a custom domain that used to be on Substack. */
    fun leftover(base: HttpUrl, subdomainHint: String? = null): List<HttpUrl> =
        candidates(base, subdomainHint).filter { isLeftoverTwin(it.host, base.host) }

    /** Whether [candidateHost] is a Substack twin asked for a publication followed on a custom domain. */
    fun isLeftoverTwin(candidateHost: String, followedHost: String): Boolean =
        isPublicationHost(candidateHost) && !isPublicationHost(followedHost)

    /**
     * The hosts to try for a pasted or followed address: it, without and with
     * `www.`, then the Substack twin. The stored subdomain names the twin; the
     * host's first label only guesses it, and only on an apex domain, since a
     * deep host's first label is a prefix that can name another publication.
     */
    fun candidates(base: HttpUrl, subdomainHint: String? = null): List<HttpUrl> {
        val out = LinkedHashMap<String, HttpUrl>()
        fun add(host: String) {
            val h = host.lowercase()
            if (h.isEmpty() || isServiceHost(h) || isObviousNonSubstack(h) || h in out) return
            https(h)?.let { out[h] = it }
        }
        fun addTwin(slug: String?) {
            if (slug.isNullOrEmpty() || !isHandle(slug) || slug.lowercase() == "www") return
            add("$slug.substack.com")
        }
        add(base.host)
        val bare = withoutWww(base.host)
        add(bare)
        add("www.$bare")
        val deep = isDeepCustomHost(base.host)
        val label = registrableLabel(base.host)
        if (!deep || subdomainHint?.trim()?.lowercase() != label) addTwin(subdomainHint)
        if (!deep && unknownSubdomain(subdomainHint)) addTwin(label)
        return out.values.toList()
    }

    /**
     * Whether a post a `{subdomain}.substack.com` twin served belongs to the
     * publication followed on its own domain: a twin that names another custom
     * domain, or another subdomain than the one stored, is someone else.
     */
    fun twinMatches(followed: SubstackPublication, post: J): Boolean {
        val publication = post["publishedBylines"][0]["publicationUsers"][0]["publication"]
        val custom = publication["custom_domain"].string?.trim()?.lowercase().orEmpty()
        if (custom.isNotEmpty() && !same("https://$custom".toHttpUrlOrNull()?.host ?: custom, hostOf(followed.baseUrl))) return false
        val subdomain = publication["subdomain"].string?.trim()?.lowercase().orEmpty()
        val stored = followed.subdomain.trim().lowercase()
        return subdomain.isEmpty() || unknownSubdomain(stored) || !isHandle(stored) || subdomain == stored
    }

    internal fun unknownSubdomain(subdomain: String?): Boolean = subdomain?.trim()?.lowercase().orEmpty().let { it.isEmpty() || it == "www" }

    internal fun isHandle(value: String): Boolean = handle.matches(value)

    private fun isPostSlug(value: String): Boolean = value.isNotEmpty() && !value.startsWith("@") && value != "p"

    internal fun withoutWww(host: String): String = host.removePrefix("www.")

    /** The lower-cased host of [url], or empty when it is no web address. */
    internal fun hostOf(url: String): String = url.trim().toHttpUrlOrNull()?.host.orEmpty()

    internal fun https(host: String): HttpUrl? = runCatching { HttpUrl.Builder().scheme("https").host(host).build() }.getOrNull()

    /** `scheme://host[:port]`, as Dart's `Uri.origin` gives it. */
    internal fun origin(url: HttpUrl): String =
        if (url.port == HttpUrl.defaultPort(url.scheme)) "${url.scheme}://${url.host}" else "${url.scheme}://${url.host}:${url.port}"

    /** The origin of [url] when it is a web address, else [url] as it is. */
    internal fun originOf(url: String): String = url.trim().toHttpUrlOrNull()?.let(::origin) ?: url

    internal fun segments(url: HttpUrl): List<String> = url.pathSegments.filter { it.isNotEmpty() }

    /** A typed address: a bare word is a Substack subdomain, a dotted one a host; only web addresses count. */
    private fun userUrl(input: String): HttpUrl? {
        var raw = input.trim()
        if (raw.isEmpty()) return null
        if ("://" !in raw) {
            if (raw.startsWith("@")) return null
            raw = if ('.' in raw) "https://$raw" else "https://$raw.substack.com"
        }
        return raw.toHttpUrlOrNull()
    }
}
