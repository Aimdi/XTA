package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Reads Bluesky through an AppView's public XRPC, as XTA's Bluesky plugin
 * does: with no account, so nothing is ever posted, liked, reposted,
 * followed, muted or blocked.
 *
 * [appView] is asked before every request, so a setting can move the reader
 * to another AppView without a new client; blank or unusable means Bluesky's
 * public one. Only post search goes elsewhere (see [searchPosts]).
 *
 * A refusal is a [NetworkRequestException] carrying the status (404 for a
 * missing thread or owner, 400 for an incomplete link; 200 when the answer
 * could not be used); [BlueskyErrorKind.of] names it as XTA's screens do.
 */
class BlueskyClient(private val http: XHttp, private val appView: () -> String? = { null }) {
    /** The AppView the next request goes to. */
    val baseUrl: String get() = BlueskyUrls.appViewOrDefault(appView())

    /** Checks the AppView answers, by reading bsky.app's own profile. */
    suspend fun verify() {
        profile("bsky.app")
    }

    /** The profile of [actor], a handle or a DID. */
    suspend fun profile(actor: String): BlueskyProfile {
        val profile = BlueskyParser.profile(read("app.bsky.actor.getProfile", "actor" to actor))
        if (profile.did.isEmpty() && profile.handle.isEmpty()) throw unusable("empty profile for $actor")
        return profile
    }

    /**
     * Posts by [actor], newest first. [filter] is one of the AppView's:
     * [Bluesky.AUTHOR_FEED_POSTS], [Bluesky.AUTHOR_FEED_REPLIES] (the
     * default), [Bluesky.AUTHOR_FEED_MEDIA], [Bluesky.AUTHOR_FEED_NO_REPLIES].
     */
    suspend fun authorFeed(actor: String, limit: Int = 20, cursor: String? = null, filter: String? = null): Page<BlueskyPost> {
        val body = read(
            "app.bsky.feed.getAuthorFeed",
            "actor" to actor, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null }, "filter" to filter?.ifEmpty { null },
        )
        return Page(BlueskyParser.feed(body), body["cursor"].string)
    }

    /** A custom feed's posts, in the feed's own order. */
    suspend fun feed(feed: String, limit: Int = 30, cursor: String? = null): Page<BlueskyPost> {
        val body = read("app.bsky.feed.getFeed", "feed" to feed, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null })
        return Page(BlueskyParser.feed(body), body["cursor"].string)
    }

    /** The posts of a public list's members. */
    suspend fun listFeed(list: String, limit: Int = 30, cursor: String? = null): Page<BlueskyPost> {
        val body = read("app.bsky.feed.getListFeed", "list" to list, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null })
        return Page(BlueskyParser.feed(body), body["cursor"].string)
    }

    /** One post and the conversation around it, [parentHeight] posts up and [depth] levels of replies down. */
    suspend fun postThread(uri: String, depth: Int = 10, parentHeight: Int = 80): BlueskyThread {
        val body = read("app.bsky.feed.getPostThread", "uri" to uri, "depth" to "$depth", "parentHeight" to "$parentHeight")
        return BlueskyParser.thread(body) ?: throw NetworkRequestException(Bluesky.NETWORK, 404, "thread missing for $uri")
    }

    /** The posts at [uris] (`at://` ones, each once), asked 25 at a time as the lexicon allows. */
    suspend fun posts(uris: List<String>): List<BlueskyPost> =
        uris.filter { it.startsWith("at://") }.distinct().chunked(25).flatMap { batch ->
            BlueskyParser.posts(read("app.bsky.feed.getPosts", *batch.map { "uris" to it }.toTypedArray()))
        }

    /** The posts quoting [uri], 50 a page. */
    suspend fun quotes(uri: String, cursor: String? = null): Page<BlueskyPost> {
        val body = read("app.bsky.feed.getQuotes", "uri" to uri, "limit" to "50", "cursor" to cursor)
        return Page(BlueskyParser.posts(body), body["cursor"].string)
    }

    /** The people who reposted [uri], 50 a page. */
    suspend fun repostedBy(uri: String, cursor: String? = null): Page<BlueskyProfile> {
        val body = read("app.bsky.feed.getRepostedBy", "uri" to uri, "limit" to "50", "cursor" to cursor)
        return Page(BlueskyParser.people(body["repostedBy"].list), body["cursor"].string)
    }

    /** Accounts matching [query], the first [limit] of them. */
    suspend fun searchActors(query: String, limit: Int = 10): List<BlueskyProfile> =
        read("app.bsky.actor.searchActors", "q" to query, "limit" to "$limit")["actors"].list.map(BlueskyParser::profile)

    /** A page of accounts matching [query]; [limit] is kept within the AppView's 1–100. */
    suspend fun searchActorsPage(query: String, limit: Int = 20, cursor: String? = null): Page<BlueskyProfile> {
        val body = read(
            "app.bsky.actor.searchActors",
            "q" to query, "limit" to "${limit.coerceIn(1, 100)}", "cursor" to cursor?.ifEmpty { null },
        )
        return Page(body["actors"].list.map(BlueskyParser::profile), body["cursor"].string)
    }

    /**
     * Posts matching [query], [sort]ed `latest` or `top`, narrowed to an
     * [author] and to [tags] (`#` dropped, at most ten).
     *
     * Bluesky's own AppViews refuse post search to readers without an
     * account on public.api.bsky.app, so it goes to [Bluesky.SEARCH_APP_VIEW]
     * instead; that one refuses its cursors, so later pages ask for posts
     * older than the last page's oldest ([BlueskyParser.searchCursor]).
     * Another AppView is searched, and paged, as it is.
     */
    suspend fun searchPosts(
        query: String,
        limit: Int = 20,
        cursor: String? = null,
        sort: String = "latest",
        author: String? = null,
        tags: List<String> = emptyList(),
    ): Page<BlueskyPost> {
        val base = baseUrl
        val byTime = base == Bluesky.DEFAULT_APP_VIEW || base == Bluesky.SEARCH_APP_VIEW
        val until = BlueskyParser.searchUntil(cursor)
        val body = read(
            "app.bsky.feed.searchPosts",
            "q" to query,
            "limit" to "${limit.coerceIn(1, 100)}",
            "sort" to if (sort == "top") "top" else "latest",
            "author" to author?.trim()?.ifEmpty { null },
            *tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().take(10).map { "tag" to it }.toTypedArray(),
            "until" to until,
            "cursor" to cursor?.ifEmpty { null }?.takeIf { until == null },
            root = if (byTime) Bluesky.SEARCH_APP_VIEW else base,
        )
        return Page(BlueskyParser.posts(body), if (byTime) BlueskyParser.searchCursor(body) else body["cursor"].string)
    }

    /** Accounts the AppView suggests to a reader without an account. */
    suspend fun suggestions(limit: Int = 20): List<BlueskyProfile> =
        read("app.bsky.actor.getSuggestions", "limit" to "$limit")["actors"].list.map(BlueskyParser::profile)

    /** Accounts like [actor], as a profile suggests them; an AppView without suggestions answers none. */
    suspend fun suggestedFollowsByActor(actor: String): List<BlueskyProfile> =
        read("app.bsky.graph.getSuggestedFollowsByActor", "actor" to actor)["suggestions"].list.map(BlueskyParser::profile)

    /** The accounts [actor] follows on Bluesky, as the AppView shows anyone. */
    suspend fun follows(actor: String, limit: Int = 100, cursor: String? = null): Page<BlueskyProfile> {
        val body = read("app.bsky.graph.getFollows", "actor" to actor, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null })
        return Page(BlueskyParser.people(body["follows"].list), body["cursor"].string)
    }

    /** The accounts that follow [actor]. */
    suspend fun followers(actor: String, limit: Int = 100, cursor: String? = null): Page<BlueskyProfile> {
        val body = read("app.bsky.graph.getFollowers", "actor" to actor, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null })
        return Page(BlueskyParser.people(body["followers"].list), body["cursor"].string)
    }

    /** The lists [actor] made. */
    suspend fun lists(actor: String, limit: Int = 50, cursor: String? = null): Page<BlueskyListInfo> =
        BlueskyParser.lists(read("app.bsky.graph.getLists", "actor" to actor, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null }))

    /** A public list and a page of its members, the list named by its `at://` URI. */
    suspend fun list(listUri: String, limit: Int = 100, cursor: String? = null): BlueskyListMembers =
        BlueskyParser.listMembers(
            read("app.bsky.graph.getList", "list" to listUri, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null }),
        )

    /** The list a starter pack (by `at://` URI) gathers its accounts in; null when the AppView names none. */
    suspend fun starterPackListUri(starterPack: String): String? =
        BlueskyParser.starterPackListUri(read("app.bsky.graph.getStarterPack", "starterPack" to starterPack))

    /** A custom feed's name, description and creator. */
    suspend fun feedGenerator(feed: String): BlueskyFeedGenerator {
        val body = read("app.bsky.feed.getFeedGenerator", "feed" to feed)
        val generator = BlueskyParser.feedGenerator(if (body["view"].exists) body["view"] else body)
        if (generator.uri.isEmpty()) throw unusable("empty feed generator for $feed")
        return generator
    }

    /** Several custom feeds at once (pinned ones, known ones), at most 25. */
    suspend fun feedGenerators(feeds: List<String>): List<BlueskyFeedGenerator> {
        val uris = feeds.map { it.trim() }.filter { it.isNotEmpty() }.take(25)
        if (uris.isEmpty()) return emptyList()
        return BlueskyParser.feedGenerators(read("app.bsky.feed.getFeedGenerators", *uris.map { "feeds" to it }.toTypedArray()))
    }

    /** The popular custom feeds bsky.app's Discover lists, or those matching [query]. */
    suspend fun popularFeedGenerators(limit: Int = 20, cursor: String? = null, query: String? = null): Page<BlueskyFeedGenerator> {
        val body = read(
            "app.bsky.unspecced.getPopularFeedGenerators",
            "limit" to "$limit", "cursor" to cursor?.ifEmpty { null }, "query" to query?.ifEmpty { null },
        )
        return Page(BlueskyParser.feedGenerators(body), body["cursor"].string)
    }

    /** The custom feeds [actor] made (not the ones they saved). */
    suspend fun actorFeeds(actor: String, limit: Int = 50, cursor: String? = null): Page<BlueskyFeedGenerator> {
        val body = read("app.bsky.feed.getActorFeeds", "actor" to actor, "limit" to "$limit", "cursor" to cursor?.ifEmpty { null })
        return Page(BlueskyParser.feedGenerators(body), body["cursor"].string)
    }

    /** The `at://` URI of a list a link names, its owner's handle resolved to a DID when it is a bsky.app link. */
    suspend fun resolveListUri(ref: BlueskyRef): String = resolve(ref, "app.bsky.graph.list", "list")

    /** The `at://` URI of a custom feed a link names. */
    suspend fun resolveFeedUri(ref: BlueskyRef): String = resolve(ref, "app.bsky.feed.generator", "feed")

    /** The `at://` URI of a starter pack a link names. */
    suspend fun resolveStarterPackUri(ref: BlueskyRef): String = resolve(ref, "app.bsky.graph.starterpack", "starter pack")

    /**
     * People for a search: the account a query names (a handle, a DID, a
     * profile link) is read directly; anything else is searched, 25 a page.
     */
    suspend fun searchPeople(query: String, cursor: String? = null): Page<BlueskyProfile> {
        val text = query.trim()
        val named = BlueskyUrls.searchActor(text) ?: return searchActorsPage(text, limit = 25, cursor = cursor)
        return Page(listOf(profile(named)), null)
    }

    /**
     * Posts for a search, as XTA's search sheet asks for them: a post link or
     * `at://` URI reads that one post; a lone `#tag` searches that exact tag;
     * anything else is searched, 25 a page, narrowed by [author] and [tag].
     */
    suspend fun searchPostsFor(
        query: String,
        cursor: String? = null,
        sort: String = "latest",
        author: String? = null,
        tag: String? = null,
    ): Page<BlueskyPost> {
        val text = query.trim()
        BlueskyUrls.searchPostTarget(text)?.let { return Page(listOf(targetPost(it)), null) }
        val hashtag = text.takeIf { BlueskySearchRules.hashtag.matches(it) }?.substring(1)
        val tags = listOfNotNull(tag?.ifEmpty { null }, hashtag).distinct()
        val words = if (hashtag == null) text else ""
        return searchPosts(words, limit = 25, cursor = cursor, sort = sort, author = author?.ifEmpty { null }, tags = tags)
    }

    private suspend fun targetPost(target: BlueskyPostTarget): BlueskyPost {
        val did = if (target.actor.startsWith("did:")) target.actor else profile(target.actor).did
        if (did.isEmpty()) throw NetworkRequestException(Bluesky.NETWORK, 404, "Missing actor DID")
        return posts(listOf("at://$did/app.bsky.feed.post/${target.rkey}")).firstOrNull()
            ?: throw NetworkRequestException(Bluesky.NETWORK, 404, "Post unavailable")
    }

    /**
     * A page of [profile]'s [tab], read by DID so a renamed handle still
     * reads, with what the AppView's filter lets through that the tab does
     * not show left out.
     */
    suspend fun profileTab(profile: BlueskyProfile, tab: BlueskyProfileTab, cursor: String? = null): Page<BlueskyPost> {
        val page = authorFeed(profile.actor, cursor = cursor, filter = tab.filter)
        return Page(page.items.filter(tab::shows), page.next)
    }

    /** The post [profile] pinned, read on its own; null when it has none or it is gone. */
    suspend fun pinnedPost(profile: BlueskyProfile): BlueskyPost? {
        val uri = profile.pinnedPostUri?.ifEmpty { null } ?: return null
        return posts(listOf(uri)).firstOrNull { it.uri == uri }
    }

    private suspend fun resolve(ref: BlueskyRef, collection: String, what: String): String = when (ref) {
        is BlueskyRef.AtUri -> ref.uri.trim().ifEmpty { throw incomplete(what) }
        is BlueskyRef.Web -> {
            val actor = ref.actor.trim()
            val rkey = ref.rkey.trim()
            if (actor.isEmpty() || rkey.isEmpty()) throw incomplete(what)
            val did = profile(actor).did.ifEmpty { throw NetworkRequestException(Bluesky.NETWORK, 404, "$what owner missing did: $actor") }
            "at://$did/$collection/$rkey"
        }
    }

    /**
     * One XRPC GET of [method] on [root], with the [query] parameters that
     * have a value, in order (a name may repeat). Anything but 200 is a
     * refusal; so is an answer that is not a JSON object, which every XRPC
     * answer is (and which [J.parse], being lenient, would not catch alone).
     */
    private suspend fun read(method: String, vararg query: Pair<String, String?>, root: String = baseUrl): J {
        val url = "${root.trimEnd('/')}/xrpc/$method".toHttpUrl().newBuilder()
            .apply { query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) } }
            .build()
        val answer = http.get(url, HEADERS, followRedirects = true)
        if (answer.code != 200) throw NetworkRequestException(Bluesky.NETWORK, answer.code, "$url: ${answer.code}")
        return J.parse(answer.body).takeIf { it.raw is JsonObject } ?: throw unusable("$url: not JSON")
    }

    private fun unusable(message: String) = NetworkRequestException(Bluesky.NETWORK, 200, message)

    private fun incomplete(what: String) = NetworkRequestException(Bluesky.NETWORK, 400, "incomplete $what reference")

    companion object {
        /** XTA's own request headers: it names itself and asks for JSON. */
        val HEADERS = mapOf("User-Agent" to "XTA Bluesky plugin", "Accept" to "application/json")
    }
}
