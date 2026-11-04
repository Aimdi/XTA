package com.aimdi.xtanative.networks.substack

import java.net.URLDecoder
import java.net.URLEncoder

/** One of the Substack plugin's own screens, as its route on the app's back stack names it. */
sealed interface SubstackRoute {
    /** A post in the reader: [link] says where to read it; [key] finds the copy it was opened from, when one is at hand. */
    data class Post(val link: SubstackPostLink, val key: String? = null) : SubstackRoute

    /** A post's discussion. */
    data class Comments(val link: SubstackPostLink, val key: String? = null) : SubstackRoute

    data class Publication(val publication: SubstackPublication) : SubstackRoute

    /** A public Note, kept in memory while it is open: Substack has no address to read one again by. */
    data class Note(val id: String) : SubstackRoute

    /** Following a publication by its address or handle. */
    data object Add : SubstackRoute
}

/** The routes of the plugin's screens: `post?base=…&slug=…`, `comments?…`, `publication?base=…`, `note?id=…`, `add`. */
object SubstackRoutes {
    fun post(post: SubstackPost): String = "post?" + linkQuery(post) + "&key=" + encoded(post.key)

    fun post(link: SubstackPostLink): String = "post?base=" + encoded(link.publicationBase) + "&slug=" + encoded(link.slug)

    fun comments(post: SubstackPost): String = "comments?" + linkQuery(post) + "&key=" + encoded(post.key)

    fun publication(publication: SubstackPublication): String =
        "publication?base=" + encoded(publication.baseUrl) + "&sub=" + encoded(publication.subdomain) + "&name=" + encoded(publication.name) +
            (publication.logoUrl?.let { "&logo=" + encoded(it) }.orEmpty())

    fun note(note: SubstackNote): String = "note?id=" + encoded(note.id)

    const val ADD = "add"

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): SubstackRoute? {
        val name = route.substringBefore('?')
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val link = postLink(query)
        return when {
            name == "post" && link != null -> SubstackRoute.Post(link, query["key"])
            name == "comments" && link != null -> SubstackRoute.Comments(link, query["key"])
            name == "publication" -> publication(query)?.let(SubstackRoute::Publication)
            name == "note" -> query["id"]?.takeIf { it.isNotEmpty() }?.let(SubstackRoute::Note)
            route == ADD -> SubstackRoute.Add
            else -> null
        }
    }

    /**
     * The route of the reader for [url]: a post on a Substack host, its share
     * link, or a post on the custom domain of a publication followed
     * ([followedBaseUrls]). Anything else, home pages and notes included, is
     * null, for the browser, as XTA's links decide.
     */
    fun forUrl(url: String, followedBaseUrls: Iterable<String>): String? {
        val text = url.trim()
        if (!text.startsWith("https://") && !text.startsWith("http://")) return null
        return SubstackLinks.postLink(text, followedBaseUrls)?.let(::post)
    }

    private fun linkQuery(post: SubstackPost): String {
        val base = SubstackHosts.originOf(post.publicationBaseUrl)
        return "base=" + encoded(base) + "&slug=" + encoded(post.slug)
    }

    private fun postLink(query: Map<String, String>): SubstackPostLink? {
        val base = query["base"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val slug = query["slug"]?.takeIf { it.isNotBlank() } ?: return null
        return SubstackPostLink(base, slug)
    }

    private fun publication(query: Map<String, String>): SubstackPublication? {
        val base = query["base"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val sub = query["sub"].orEmpty().ifEmpty { SubstackHosts.subdomainOf(SubstackHosts.hostOf(base)) }
        return SubstackPublication(sub, base, query["name"].orEmpty().ifEmpty { sub }, logoUrl = query["logo"]?.takeIf { it.isNotEmpty() })
    }

    private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8")
}
