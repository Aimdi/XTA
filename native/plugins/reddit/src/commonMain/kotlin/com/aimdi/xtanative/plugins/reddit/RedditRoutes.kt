package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.reddit.RedditPost
import java.net.URLDecoder
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface RedditRoute {
    /** A post and its comments, by [permalink]; a link names its [id], [subreddit] and maybe its [title] until the post itself is read. */
    data class Thread(val permalink: String, val id: String? = null, val subreddit: String? = null, val title: String? = null) : RedditRoute {
        /** The post as a link names it (XTA's `_redditStub`), for a thread opened from one. */
        fun stub(): RedditPost = RedditPost(
            id = id ?: permalink,
            title = title ?: id.orEmpty(),
            subreddit = subreddit.orEmpty(),
            permalink = permalink,
        )
    }

    data class Subreddit(val name: String) : RedditRoute

    data class User(val name: String) : RedditRoute

    /** The posts saved on this device. */
    data object Saved : RedditRoute

    /** Search, within [subreddit] when there is one, starting with [query] when there is one. */
    data class Search(val subreddit: String?, val query: String?) : RedditRoute

    /** Reddit's sign-in page, watched for its way back. */
    data object SignIn : RedditRoute
}

internal object RedditRoutes {
    const val SAVED = "saved"
    const val SIGN_IN = "sign-in"

    fun thread(permalink: String): String = "thread" + params("permalink" to permalink)

    fun subreddit(name: String): String = "r" + params("name" to name)

    fun user(name: String): String = "u" + params("name" to name)

    fun search(subreddit: String?, query: String? = null): String = "search" + params("r" to subreddit, "q" to query)

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): RedditRoute? {
        val name = route.substringBefore('?')
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            .filterValues { it.isNotBlank() }
        return when (name) {
            "thread" -> query["permalink"]?.let { RedditRoute.Thread(it, query["id"], query["sub"], query["title"]) }
            "r" -> query["name"]?.let(RedditRoute::Subreddit)
            "u" -> query["name"]?.let(RedditRoute::User)
            "search" -> RedditRoute.Search(query["r"], query["q"])
            SAVED -> RedditRoute.Saved
            SIGN_IN -> RedditRoute.SignIn
            else -> null
        }
    }

    /**
     * The route of the screen showing [url] (XTA's `parseRedditLink`): a
     * community, an account, or a post on reddit.com. Null for anything else:
     * Reddit's picture and video files, and redd.it's short links, which name
     * a post without the community whose page serves it.
     */
    fun forUrl(url: String): String? {
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        return when {
            community(segments[0], segments[1]) -> communityRoute(segments)
            segments[0] == "u" || segments[0] == "user" -> user(segments[1])
            else -> null
        }
    }

    private fun communityRoute(segments: List<String>): String {
        val subreddit = segments[1]
        val id = segments.getOrNull(3)?.takeIf { segments[2] == "comments" && postId.matches(it) }?.lowercase() ?: return subreddit(subreddit)
        val title = segments.getOrNull(4)?.replace('_', ' ')
        return "thread" + params("permalink" to "/r/$subreddit/comments/$id/", "id" to id, "sub" to subreddit, "title" to title)
    }

    private fun community(first: String, name: String) = (first == "r" || first == "R") && communityName.matches(name)

    private val communityName = Regex("^[A-Za-z0-9_]{2,50}$")
    private val postId = Regex("^[A-Za-z0-9]{5,10}$")

    private fun params(vararg pairs: Pair<String, String?>): String =
        pairs.filter { !it.second.isNullOrBlank() }.joinToString("&", prefix = "?") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
