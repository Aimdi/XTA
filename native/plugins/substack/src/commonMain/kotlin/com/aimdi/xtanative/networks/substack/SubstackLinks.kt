package com.aimdi.xtanative.networks.substack

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A part of a note's or comment's text: plain, or a web link to open. */
data class SubstackTextPart(val text: String, val url: String? = null)

/**
 * Which links are Substack posts XTA reads itself, as its substack_links.dart
 * decides, and the web links inside notes and comments.
 */
object SubstackLinks {
    /** Path segments that are pages of a publication, never a post. */
    private val NOT_POSTS = setOf("archive", "about", "subscribe", "notes", "note", "podcast", "people", "s", "i")

    /** Hosts whose `/p/` links are known not to be a publication's posts. */
    private val NOT_PUBLICATIONS = setOf("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am")

    private val webLink = Regex("https?://[^\\s<>\\[\\]\"\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = Regex("[.,!?;:]+$")

    /**
     * The post [url] links to: `{name}.substack.com/p/{slug}`, the share host's
     * `open.substack.com/pub/{name}/p/{slug}`, or `/p/{slug}` on a custom domain
     * among [knownBaseUrls], the publications followed. Anything else, home
     * pages, archives and notes included, is null, for the browser to open.
     */
    fun postLink(url: String, knownBaseUrls: Iterable<String> = emptyList()): SubstackPostLink? {
        val link = url.trim().toHttpUrlOrNull() ?: return null
        val parts = SubstackHosts.segments(link)
        if (link.host == "open.substack.com") {
            val shared = parts.size >= 4 && parts[0] == "pub" && parts[2] == "p" && isSlug(parts[3]) && isSubdomain(parts[1])
            return if (shared) SubstackPostLink("https://${parts[1]}.substack.com", parts[3]) else null
        }
        val known = knownBaseUrls.map(SubstackHosts::hostOf).filter { it.isNotEmpty() }.toSet()
        if (!SubstackHosts.isPublicationHost(link.host) && link.host !in known) return null
        return if (parts.size >= 2 && parts[0] == "p" && isSlug(parts[1])) SubstackPostLink("https://${link.host}", parts[1]) else null
    }

    /**
     * A `/p/{slug}` link on a host that is neither Substack's nor followed: it
     * may be a publication on its own domain, which only asking it can tell
     * ([SubstackClient.probeCustomDomainPost]).
     */
    fun customDomainCandidate(url: String, knownBaseUrls: Iterable<String> = emptyList()): SubstackPostLink? {
        val link = url.trim().toHttpUrlOrNull() ?: return null
        val host = link.host
        if (host == "substack.com" || host.endsWith(".substack.com") || host in NOT_PUBLICATIONS) return null
        if (postLink(url, knownBaseUrls) != null) return null
        val parts = SubstackHosts.segments(link)
        return if (parts.size >= 2 && parts[0] == "p" && isSlug(parts[1])) SubstackPostLink("https://$host", parts[1]) else null
    }

    /**
     * The least of a post the reader needs to load the real one from its
     * publication and slug. Until then the publication's name stands in for the
     * title, so the reader never opens with an empty title bar.
     */
    fun stub(link: SubstackPostLink, publicationName: String? = null): SubstackPost {
        val name = publicationName ?: SubstackHosts.subdomainOf(SubstackHosts.hostOf(link.publicationBase))
        return SubstackPost(
            id = link.slug,
            title = name,
            slug = link.slug,
            publicationBaseUrl = link.publicationBase,
            publicationName = name,
            canonicalUrl = "${link.publicationBase}/p/${link.slug}",
        )
    }

    /** [value] when it is a web address with a host; anything else, `javascript:` included, is no link. */
    fun webUrl(value: String?): String? = value?.trim()?.takeIf { it.toHttpUrlOrNull() != null }

    /**
     * [text] cut into plain parts and web links, every character kept. A link
     * gives up trailing punctuation, and a closing parenthesis it never opened.
     */
    fun textParts(text: String): List<SubstackTextPart> {
        val parts = ArrayList<SubstackTextPart>()
        var offset = 0
        for (match in webLink.findAll(text)) {
            var candidate = match.value.replace(trailingPunctuation, "")
            while (candidate.endsWith(")") && candidate.count { it == ')' } > candidate.count { it == '(' }) candidate = candidate.dropLast(1)
            val url = webUrl(candidate) ?: continue
            if (match.range.first > offset) parts += SubstackTextPart(text.substring(offset, match.range.first))
            parts += SubstackTextPart(candidate, url)
            offset = match.range.first + candidate.length
        }
        if (offset < text.length) parts += SubstackTextPart(text.substring(offset))
        return parts
    }

    private fun isSlug(value: String): Boolean = value.isNotEmpty() && value.lowercase() !in NOT_POSTS && !value.startsWith("@")

    private fun isSubdomain(value: String): Boolean = value.isNotEmpty() && '/' !in value && '.' !in value
}
