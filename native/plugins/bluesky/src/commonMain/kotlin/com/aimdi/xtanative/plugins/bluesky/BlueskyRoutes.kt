package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyClient
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.services.links.NetworkLink
import com.aimdi.xtanative.services.links.NetworkLinks
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

    /**
     * The post as a link names it, on screen while its thread is read
     * (XTA's _blueskyStub): its author by the link's name, its place on
     * bsky.app, and nothing else yet. None for a post opened by its URI,
     * which the list it was tapped in still holds.
     */
    fun stub(): BlueskyPost? = when (this) {
        is Uri -> null
        is Key -> {
            val did = actor.takeIf { it.startsWith("did:") }.orEmpty()
            BlueskyPost(
                uri = "at://$actor/app.bsky.feed.post/$rkey",
                cid = "",
                handle = if (did.isEmpty()) actor else "",
                did = did,
                authorName = actor,
                text = "",
                url = "https://bsky.app/profile/$actor/post/$rkey",
            )
        }
    }
}

/** Whose accounts a list of people shows: those [actor] follows, or those who follow them. */
internal enum class PeopleKind { FOLLOWING, FOLLOWERS }

/** What an import copies into the follows kept here: someone's following, a list's members, a starter pack's. */
internal enum class ImportKind { FOLLOWING, LIST, STARTER }

/** The plugin's own screens, as routes on the app's back stack ("post?uri=…", "profile?actor=…", …). */
internal sealed interface BlueskyRoute {
    data class Post(val target: PostTarget) : BlueskyRoute

    data class Profile(val actor: String) : BlueskyRoute

    data class People(val actor: String, val kind: PeopleKind) : BlueskyRoute

    /** Who reposted the post [uri], or ([quotes]) the posts quoting it; [url] is where it lives on bsky.app. */
    data class Activity(val uri: String, val url: String, val quotes: Boolean) : BlueskyRoute

    data class Search(val query: String, val author: String, val kind: BlueskySearchKind) : BlueskyRoute

    data class Import(val kind: ImportKind, val value: String) : BlueskyRoute

    /** The custom feeds to find, pin and open on Discover. */
    data object Feeds : BlueskyRoute

    /** What was saved from Bluesky on this device. */
    data object Saved : BlueskyRoute
}

internal object BlueskyRoutes {
    fun post(uri: String): String = route("post", "uri" to uri)

    fun post(actor: String, rkey: String): String = route("post", "actor" to actor, "rkey" to rkey)

    fun profile(actor: String): String = route("profile", "actor" to actor)

    fun people(actor: String, kind: PeopleKind): String = route("people", "actor" to actor, "kind" to kind.name.lowercase())

    fun activity(uri: String, url: String, quotes: Boolean): String = route(if (quotes) "quotes" else "reposts", "uri" to uri, "url" to url)

    fun search(query: String = "", author: String = "", kind: BlueskySearchKind = BlueskySearchKind.PEOPLE): String =
        route("search", "q" to query, "author" to author, "kind" to kind.name.lowercase())

    fun import(kind: ImportKind, value: String = ""): String = route("import", "kind" to kind.name.lowercase(), "value" to value)

    const val FEEDS = "feeds"

    const val SAVED = "saved"

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): BlueskyRoute? {
        val name = route.substringBefore('?')
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        fun value(key: String) = query[key]?.takeIf { it.isNotBlank() }
        val uri = value("uri")?.takeIf { it.startsWith("at://") }
        val actor = value("actor")
        return when (name) {
            "post" -> post(uri, actor, value("rkey"))
            "profile" -> actor?.let(BlueskyRoute::Profile)
            "people" -> actor?.let { who -> kindOf<PeopleKind>(value("kind"))?.let { BlueskyRoute.People(who, it) } }
            "reposts", "quotes" -> uri?.let { BlueskyRoute.Activity(it, value("url").orEmpty(), quotes = name == "quotes") }
            "search" -> BlueskyRoute.Search(query["q"].orEmpty(), query["author"].orEmpty(), kindOf<BlueskySearchKind>(value("kind")) ?: BlueskySearchKind.PEOPLE)
            "import" -> kindOf<ImportKind>(value("kind"))?.let { BlueskyRoute.Import(it, query["value"].orEmpty()) }
            FEEDS -> BlueskyRoute.Feeds
            SAVED -> BlueskyRoute.Saved
            else -> null
        }
    }

    private fun post(uri: String?, actor: String?, rkey: String?): BlueskyRoute? = when {
        uri != null -> BlueskyRoute.Post(PostTarget.Uri(uri))
        actor != null && rkey != null -> BlueskyRoute.Post(PostTarget.Key(actor, rkey))
        else -> null
    }

    private inline fun <reified E : Enum<E>> kindOf(name: String?): E? = enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The route of the screen showing [url], as XTA reads a bsky.app link: a post's thread, else the profile it is under. */
    fun forUrl(url: String): String? = when (val link = NetworkLinks.bluesky(url)) {
        is NetworkLink.BlueskyPost -> post(link.actor, link.rkey)
        is NetworkLink.BlueskyProfile -> profile(link.actor)
        else -> null
    }

    private fun route(name: String, vararg values: Pair<String, String>): String =
        name + "?" + values.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
