package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyClient
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import java.net.URLDecoder
import java.net.URLEncoder

/** A post to open: by its `at://` URI, or by its author and record key as a bsky.app link names it. */
internal sealed interface PostTarget {
    /** What tells two targets apart, for what a screen keeps. */
    val key: String

    data class Uri(val uri: String) : PostTarget {
        override val key: String get() = uri
    }

    data class Key(val actor: String, val rkey: String) : PostTarget {
        override val key: String get() = "$actor/$rkey"
    }

    /** The post's `at://` URI; an author named by handle is looked up for their DID first. */
    suspend fun uri(client: BlueskyClient): String = when (this) {
        is Uri -> uri
        is Key -> "at://" + (if (actor.startsWith("did:")) actor else client.profile(actor).did) + "/app.bsky.feed.post/" + rkey
    }
}

/** The plugin's own screens, as routes on the app's back stack: "post?uri=…", "post?actor=…&rkey=…", "profile?actor=…". */
internal sealed interface BlueskyRoute {
    data class Post(val target: PostTarget) : BlueskyRoute

    data class Profile(val actor: String) : BlueskyRoute
}

internal object BlueskyRoutes {
    fun post(uri: String): String = "post?uri=" + encoded(uri)

    fun post(actor: String, rkey: String): String = "post?actor=" + encoded(actor) + "&rkey=" + encoded(rkey)

    fun profile(actor: String): String = "profile?actor=" + encoded(actor)

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): BlueskyRoute? {
        val name = route.substringBefore('?')
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val uri = query["uri"]?.takeIf { it.startsWith("at://") }
        val actor = query["actor"]?.takeIf { it.isNotBlank() }
        val rkey = query["rkey"]?.takeIf { it.isNotBlank() }
        return when {
            name == "post" && uri != null -> BlueskyRoute.Post(PostTarget.Uri(uri))
            name == "post" && actor != null && rkey != null -> BlueskyRoute.Post(PostTarget.Key(actor, rkey))
            name == "profile" && actor != null -> BlueskyRoute.Profile(actor)
            else -> null
        }
    }

    /** The route of the screen showing [url]: a bsky.app post or profile link; null for any other link. */
    fun forUrl(url: String): String? {
        val text = url.trim()
        if (!text.startsWith("https://")) return null
        BlueskyUrls.searchPostTarget(text)?.let { return post(it.actor, it.rkey) }
        return BlueskyUrls.searchActor(text)?.let(::profile)
    }

    private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8")
}
