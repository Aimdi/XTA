package com.aimdi.xtanative.networks.substack

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One post across the copies Substack hands out, as XTA's post identity rule
 * (BUG-02): the API names a post by number and the RSS fallback by slug, but
 * both carry its canonical URL.
 */
object SubstackPosts {
    /**
     * The canonical URL without scheme, `www.`, query, fragment or trailing
     * slash; else the publication's host and the slug; else the id.
     */
    fun keyOf(post: SubstackPost): String {
        val url = post.canonicalUrl?.trim()?.toHttpUrlOrNull()
        val path = url?.encodedPath?.trimEnd('/').orEmpty()
        if (url != null && path.isNotEmpty()) return "${SubstackHosts.withoutWww(url.host)}$path"
        val host = SubstackHosts.hostId(post.publicationBaseUrl)
        return if (host.isNotEmpty() && post.slug.isNotEmpty()) "$host/p/${post.slug}" else post.id
    }

    /** Whether [a] and [b] are copies of one post. */
    fun same(a: SubstackPost, b: SubstackPost): Boolean = a.key == b.key || (a.hasApiId && a.id == b.id)

    /** Whether [post] is among [readIds]: by its key, or by its id as reads were kept before posts had keys. */
    fun isRead(readIds: Set<String>, post: SubstackPost): Boolean = post.key in readIds || post.id in readIds

    /** [posts] with one copy of each, in first-seen order. An RSS copy never replaces the API copy, which has the number and the counts. */
    fun mergeCopies(posts: Iterable<SubstackPost>): List<SubstackPost> {
        val unique = LinkedHashMap<String, SubstackPost>()
        for (post in posts) {
            val kept = unique[post.key]
            if (kept == null || !kept.hasApiId || post.hasApiId) unique[post.key] = post
        }
        return unique.values.toList()
    }
}

/**
 * Which follow a publication is, as XTA's follow identity rule (BUG-06,
 * BUG-16). Following happens in XTA Native alone; this only says which stored
 * follow a publication found on the network matches, so nothing is
 * overwritten by another publication that shares a guessed id.
 */
object SubstackFollows {
    /**
     * The follow [publication] is: one on the same host (the one with the same
     * id first), else one with the same id when neither id is a guess. A
     * publication built from a post only guesses its id from its host, and that
     * guess can be another publication's subdomain.
     */
    fun followOf(follows: Iterable<SubstackPublication>, publication: SubstackPublication): SubstackPublication? {
        val host = SubstackHosts.hostOf(publication.baseUrl)
        val sameHost = if (host.isEmpty()) emptyList() else follows.filter { SubstackHosts.same(SubstackHosts.hostOf(it.baseUrl), host) }
        if (sameHost.isNotEmpty()) return sameHost.firstOrNull { it.id == publication.id } ?: sameHost.first()
        if (guessedId(publication)) return null
        return follows.firstOrNull { it.id == publication.id && !guessedId(it) }
    }

    /**
     * Where [publication] goes among [follows]: over the follow it already is,
     * under that follow's id; under its own host when another publication
     * holds its id; else as it is.
     */
    fun place(follows: Iterable<SubstackPublication>, publication: SubstackPublication): SubstackPublication {
        val follow = followOf(follows, publication)
        if (follow != null) return if (follow.subdomain.isEmpty()) publication else publication.copy(subdomain = follow.subdomain)
        val host = SubstackHosts.hostId(publication.baseUrl)
        if (host.isEmpty() || follows.none { it.id == publication.id }) return publication
        return publication.copy(subdomain = host)
    }

    private fun guessedId(publication: SubstackPublication): Boolean {
        val id = publication.id
        if (SubstackHosts.unknownSubdomain(id)) return true
        val host = SubstackHosts.hostOf(publication.baseUrl)
        return SubstackHosts.isDeepCustomHost(host) && id == SubstackHosts.registrableLabel(host)
    }
}
