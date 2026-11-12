package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads a post's `tag_header`: the topic or community it was posted in, as
 * threads.com shows it beside the author ("› 🎭 Cosplayers of Threads").
 * CI's probe saw `is_community`, `community_emoji` and
 * `community_favicon_url` there; the name, an id and a link are read under
 * every key they might be under, and each is harmless when absent.
 */
object ThreadsCommunities {
    private val NAME_KEYS = listOf("display_name", "name", "community_name", "title", "tag_name")
    private val ID_KEYS = listOf("community_id", "community_pk", "id", "pk")
    private val LINK_KEYS = listOf("community_url", "url", "link", "uri", "path", "community_path", "deep_link")
    private val digits = Regex("\\d{3,32}")

    /** The topic's name without its `#`, from the first key that holds one, or from a nested `community`. */
    fun name(header: J): String? = NAME_KEYS.firstNotNullOfOrNull { key -> header[key].string?.trim()?.removePrefix("#")?.trim()?.ifEmpty { null } }
        ?: header["community"].takeIf { it.exists }?.let { nested -> NAME_KEYS.firstNotNullOfOrNull { nested[it].string?.trim()?.ifEmpty { null } } }

    /** The community's id: digits under a community's id key, or its nested object's. */
    fun id(header: J): String? {
        val community = header["is_community"].boolean == true
        return digitsAt(header, ID_KEYS.filter { community || it.startsWith("community") }) ?: digitsAt(header["community"], ID_KEYS)
    }

    private fun digitsAt(node: J, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key -> (node[key].string ?: node[key].long?.toString())?.trim()?.takeIf(digits::matches) }

    /** The community's own threads.com page, when a key names one: a whole link to threads.com or .net, or a path on it. */
    fun url(header: J): String? = listOf(header, header["community"]).firstNotNullOfOrNull { node ->
        LINK_KEYS.firstNotNullOfOrNull { key -> node[key].string?.let(::threadsPage) }
    }

    /** [raw] as a threads.com page address; null for another site, a profile or a post. */
    fun threadsPage(raw: String): String? {
        val text = raw.trim()
        val url = when {
            text.startsWith("/") && !text.startsWith("//") -> (ThreadsLinks.WEB + text).toHttpUrlOrNull()
            text.startsWith("https://") -> text.toHttpUrlOrNull()
            else -> null
        } ?: return null
        val host = url.host.lowercase().removePrefix("www.")
        if (host != "threads.com" && host != "threads.net" || url.pathSegments.none { it.isNotEmpty() }) return null
        val page = url.newBuilder().scheme("https").host("www.threads.com").fragment(null).build().toString()
        return page.takeIf { ThreadsLinks.parse(it).let { link -> link !is ThreadsLink.Post && link !is ThreadsLink.Profile } }
    }
}
