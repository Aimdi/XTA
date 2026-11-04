package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import java.net.URLDecoder
import java.net.URLEncoder

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface ThreadsRoute {
    /** A post's conversation, by its link and author; [card] names the card that was tapped, kept on the shelf. */
    data class Post(val url: String, val handle: String, val card: String? = null) : ThreadsRoute {
        /** The post as its link names it, for a conversation opened from one. */
        fun stub(): ThreadsPost = ThreadsPost.linkStub(url, handle)
    }

    data class Profile(val handle: String) : ThreadsRoute

    /** What words or a tag find, on a page whose query can be edited. */
    data class Search(val terms: ThreadsSearchTerms) : ThreadsRoute

    /** Pictures and videos full screen, from [start]; [media] names them on the shelf. */
    data class Media(val media: String, val start: Int) : ThreadsRoute
}

internal object ThreadsRoutes {
    fun post(post: ThreadsPost, card: String?): String? =
        post.url?.takeIf { it.isNotBlank() }?.let { "post" + params("url" to it, "handle" to post.handle, "card" to card) }

    fun post(link: ThreadsLink.Post): String = "post" + params("url" to link.url, "handle" to link.handle)

    fun profile(handle: String): String = "profile" + params("handle" to handle)

    fun search(terms: ThreadsSearchTerms): String =
        "search" + params("q" to terms.query, "tag" to if (terms.tag) "1" else null, "profile" to terms.profile)

    fun media(media: String, start: Int): String = "media" + params("key" to media, "start" to "$start")

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): ThreadsRoute? {
        val name = route.substringBefore('?')
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            .filterValues { it.isNotBlank() }
        return when (name) {
            "post" -> query["url"]?.let { ThreadsRoute.Post(it, query["handle"].orEmpty(), query["card"]) }
            "profile" -> query["handle"]?.let(ThreadsLinks::normaliseHandle)?.let(ThreadsRoute::Profile)
            "search" -> query["q"]?.let { ThreadsRoute.Search(ThreadsSearchTerms(it, tag = query["tag"] == "1", profile = query["profile"])) }
            "media" -> query["key"]?.let { ThreadsRoute.Media(it, query["start"]?.toIntOrNull() ?: 0) }
            else -> null
        }
    }

    /** The route of the screen showing [url]: a threads.com or threads.net profile or post; null for any other link. */
    fun forUrl(url: String): String? = when (val link = ThreadsLinks.parse(url)) {
        is ThreadsLink.Post -> post(link)
        is ThreadsLink.Profile -> profile(link.handle)
        null -> null
    }

    private fun params(vararg pairs: Pair<String, String?>): String =
        pairs.filter { !it.second.isNullOrBlank() }.joinToString("&", prefix = "?") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
