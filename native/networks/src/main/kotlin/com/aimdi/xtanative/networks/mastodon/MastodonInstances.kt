package com.aimdi.xtanative.networks.mastodon

/**
 * Which instances to ask, and how their addresses are written, as XTA's
 * mastodon_models.dart decides it. Account ids are local to each instance,
 * so every read names the instance it is made on.
 */
object MastodonInstances {
    /**
     * Instances that answer without a login, asked when nothing else does:
     * large, long-lived, open general instances, the first two run by
     * Mastodon gGmbH. They are the fallback, not the strategy: an account's
     * own instance is asked first, being the only one sure to hold all of it.
     */
    val defaults: List<String> = listOf(
        "https://mastodon.social",
        "https://mastodon.online",
        "https://mstdn.social",
        "https://mas.to",
        "https://mastodon.world",
    )

    /**
     * `scheme://host[:port]` for what a reader typed (`mastodon.social`,
     * `https://mastodon.social/`); null when it is no http(s) host. A host
     * that has to be escaped (spaces, non-ASCII) is refused, as XTA does.
     */
    fun normalise(input: String): String? {
        val value = input.trim().let { if (it.isEmpty() || "://" in it) it else "https://$it" }
        val url = WebUrl.parse(value) ?: return null
        if (url.scheme != "https" && url.scheme != "http") return null
        if (url.host.isEmpty() || '%' in url.host || url.host.any { it.isWhitespace() }) return null
        return url.origin
    }

    /** The host of an instance address, lower case; null for anything without one. */
    fun domain(instance: String): String? = WebUrl.parse(instance)?.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /**
     * Every instance worth asking about [acct], best first: the account's own
     * instance, then the reader's [configured] ones in their order, then the
     * [defaults]. Each is asked once however it was written; junk is dropped.
     */
    fun candidates(acct: String, configured: List<String> = emptyList()): List<String> {
        val address = MastodonAcct.normalise(acct) ?: acct.trim()
        val at = address.indexOf('@')
        val origin = if (at > 0) address.substring(at + 1).trim().lowercase() else ""
        val ordered = listOfNotNull(origin.takeIf { it.isNotEmpty() }?.let { "https://$it" }) + configured + defaults
        return ordered.mapNotNull(::normalise).distinct()
    }

    /**
     * [candidates] for filling a Following page: at most one of the
     * [defaults] stays, so a slow or Misskey origin costs one more try per
     * account and not five, which is what froze the rest of XTA.
     */
    fun feedCandidates(acct: String, configured: List<String> = emptyList()): List<String> {
        val builtIn = defaults.mapNotNull(::normalise).toSet()
        val all = candidates(acct, configured)
        val kept = all.firstOrNull { it in builtIn }
        return all.filter { it !in builtIn || it == kept }
    }

    /** The reader's instances, then the [defaults]: where Explore, Local, Federated and search are read. */
    fun discovery(configured: List<String>): List<String> = (configured + defaults).mapNotNull(::normalise).distinct()

    /** The host names of [instances], for telling which links open in XTA Native. */
    fun hosts(instances: Iterable<String>): Set<String> = instances.mapNotNull(::domain).toSet()
}

/** Account addresses (`acct`), as a reader types them and as instances write them. */
object MastodonAcct {
    private val localPart = Regex("^[a-z0-9_]+([a-z0-9_.-]*[a-z0-9_])?$")

    /**
     * What to look up for `user`, `@user`, `user@domain` or a profile address
     * (`https://domain/@user`, `/users/user`). A bare name stays bare, for the
     * instance asked to resolve; null when it is no address.
     */
    fun normalise(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        WebUrl.parse(value)?.takeIf { it.host.isNotEmpty() }?.let { return fromProfileUrl(it) }
        val lower = value.replaceFirst(Regex("^@+"), "").trim().lowercase()
        return when {
            lower.isEmpty() -> null
            '@' in lower -> remote(lower)
            else -> lower.takeIf(localPart::matches)
        }
    }

    /** `user@domain` for storing and comparing: [homeDomain] is added to a bare name. */
    fun canonical(acct: String, homeDomain: String?): String {
        val trimmed = acct.trim().lowercase()
        val domain = homeDomain?.trim()?.lowercase()
        return if ('@' in trimmed || domain.isNullOrEmpty()) trimmed else "$trimmed@$domain"
    }

    private fun fromProfileUrl(url: WebUrl): String? {
        val segments = url.segments.filter { it.isNotEmpty() }
        val first = segments.firstOrNull()?.removePrefix("@") ?: return null
        val name = if (first.lowercase() == "users" && segments.size >= 2) segments[1] else first
        val user = name.replaceFirst(Regex("^@+"), "").trim()
        return if (user.isEmpty()) null else "${user.lowercase()}@${url.host}"
    }

    private fun remote(lower: String): String? {
        val parts = lower.split('@')
        val valid = parts.size == 2 && parts.none { it.isEmpty() } && '.' in parts[1] && localPart.matches(parts[0])
        return lower.takeIf { valid }
    }
}
