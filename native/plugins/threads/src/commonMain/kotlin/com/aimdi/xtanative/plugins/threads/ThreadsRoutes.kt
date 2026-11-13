package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsCommunities
import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsTopic
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

    /** A community's page: its posts, its mark, and Follow on this device. */
    data class Community(val topic: ThreadsTopic) : ThreadsRoute

    /** Pictures and videos full screen, from [start]; [media] names them on the shelf. */
    data class Media(val media: String, val start: Int) : ThreadsRoute

    /** What was saved from Threads on this device. */
    data object Saved : ThreadsRoute

    /** Threads' own sign-in page, its session kept as a pasted one is. */
    data object SignIn : ThreadsRoute
}

internal object ThreadsRoutes {
    fun post(post: ThreadsPost, card: String?): String? =
        post.url?.takeIf { it.isNotBlank() }?.let { "post" + params("url" to it, "handle" to post.handle, "card" to card) }

    fun post(link: ThreadsLink.Post): String = "post" + params("url" to link.url, "handle" to link.handle)

    fun profile(handle: String): String = "profile" + params("handle" to handle)

    fun search(terms: ThreadsSearchTerms): String =
        "search" + params("q" to terms.query, "tag" to if (terms.tag) "1" else null, "profile" to terms.profile)

    fun community(topic: ThreadsTopic): String = "community" + params(
        "name" to topic.name,
        "emoji" to topic.emoji,
        "icon" to topic.iconUrl,
        "url" to topic.url,
    )

    fun media(media: String, start: Int): String = "media" + params("key" to media, "start" to "$start")

    const val SAVED = "saved"

    const val SIGN_IN = "sign-in"

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
            "community" -> communityOf(query)
            "media" -> query["key"]?.let { ThreadsRoute.Media(it, query["start"]?.toIntOrNull() ?: 0) }
            SAVED -> ThreadsRoute.Saved
            SIGN_IN -> ThreadsRoute.SignIn
            else -> null
        }
    }

    /** The route of the screen showing [url]: a threads.com or threads.net profile, post, search or topic; null for any other link. */
    fun forUrl(url: String): String? = when (val link = ThreadsLinks.parse(url)) {
        is ThreadsLink.Post -> post(link)
        is ThreadsLink.Profile -> profile(link.handle)
        is ThreadsLink.Search -> search(link.terms)
        is ThreadsLink.Community -> community(ThreadsTopic(nameOf(link.url), community = true, url = link.url))
        null -> null
    }

    /** A community route's topic; its link only when it is a threads.com page, its picture only over https. */
    private fun communityOf(query: Map<String, String>): ThreadsRoute.Community? {
        val url = query["url"]?.let(ThreadsCommunities::threadsPage)
        val name = query["name"]?.trim()?.ifEmpty { null } ?: url?.let(::nameOf) ?: return null
        return ThreadsRoute.Community(ThreadsTopic(name, query["emoji"], query["icon"]?.takeIf { it.startsWith("https://") }, community = true, url = url))
    }

    /** What a community's address names it until its page says more: its last path segment. */
    private fun nameOf(url: String): String =
        URLDecoder.decode(url.substringBefore('?').trimEnd('/').substringAfterLast('/'), "UTF-8").replace('-', ' ').replace('_', ' ')

    private fun params(vararg pairs: Pair<String, String?>): String =
        pairs.filter { !it.second.isNullOrBlank() }.joinToString("&", prefix = "?") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
