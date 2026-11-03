package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class BlueskyClientTest {
    private fun client(appView: String? = null, answer: (okhttp3.HttpUrl) -> XResponse): Pair<BlueskyClient, FakeAppView> {
        val http = FakeAppView(answer)
        return BlueskyClient(http) { appView } to http
    }

    @Test
    fun aProfileIsReadFromThePublicAppViewWithXtasHeaders() = runBlocking {
        val (bluesky, http) = client { ok(fixture("profile.json")) }
        val profile = bluesky.profile("bsky.app")
        assertEquals("bsky.app", profile.handle)
        val url = http.asked.single()
        assertEquals("https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=bsky.app", url.toString())
        assertEquals("XTA Bluesky plugin", http.headers["User-Agent"])
        assertEquals("application/json", http.headers["Accept"])
        assertFalse(http.headers.keys.any { it.equals("authorization", ignoreCase = true) }, "nothing is read with an account")
        assertEquals(true, http.followedRedirects)
    }

    @Test
    fun anAuthorFeedSendsItsLimitCursorAndFilterAndHandsBackTheNextCursor() = runBlocking {
        val (bluesky, http) = client { ok(fixture("author_feed.json")) }
        val page = bluesky.authorFeed("did:plc:alice", limit = 5, cursor = "c1", filter = Bluesky.AUTHOR_FEED_REPLIES)
        assertEquals(11, page.items.size)
        assertEquals("2026-07-29T08:00:00.000Z", page.next)
        val url = http.asked.single()
        assertEquals("/xrpc/app.bsky.feed.getAuthorFeed", url.encodedPath)
        assertEquals(listOf("actor", "limit", "cursor", "filter"), url.queryParameterNames.toList())
        assertEquals(listOf("did:plc:alice", "5", "c1", "posts_with_replies"), url.queryParameterNames.map { url.queryParameter(it) })

        bluesky.authorFeed("alice.bsky.social", cursor = "", filter = "")
        assertEquals(listOf("actor", "limit"), http.asked.last().queryParameterNames.toList(), "an empty cursor or filter is not sent")
        assertEquals("20", http.asked.last().queryParameter("limit"))
    }

    @Test
    fun refusalsAreTypedByTheirStatus() {
        fun refusal(code: Int, body: String = ""): NetworkRequestException {
            val (bluesky, _) = client { XResponse(code, body) }
            return assertFailsWith<NetworkRequestException> { runBlocking { bluesky.authorFeed("alice.bsky.social") } }
        }
        assertEquals(BlueskyErrorKind.NOT_FOUND, BlueskyErrorKind.of(refusal(404)))
        assertEquals(BlueskyErrorKind.RATE_LIMITED, BlueskyErrorKind.of(refusal(429)))
        assertEquals(BlueskyErrorKind.BAD_RESPONSE, BlueskyErrorKind.of(refusal(500)))
        assertEquals(BlueskyErrorKind.BAD_RESPONSE, BlueskyErrorKind.of(refusal(200, "<html>not json</html>")))
        assertEquals(BlueskyErrorKind.BAD_RESPONSE, BlueskyErrorKind.of(refusal(200, "OK")), "a lone word is no XRPC answer, lenient parsing or not")
        assertEquals(Bluesky.NETWORK, refusal(404).network)
        assertEquals(429, refusal(429).code)
        assertEquals(BlueskyErrorKind.NETWORK, BlueskyErrorKind.of(IOException("offline")))
    }

    @Test
    fun noConnectionStaysTheTransportsOwnFailure() {
        val (bluesky, _) = client { throw IOException("offline") }
        assertFailsWith<IOException> { runBlocking { bluesky.profile("bsky.app") } }
    }

    @Test
    fun aProfileNamingNoOneIsABadResponse() {
        val (bluesky, _) = client { ok("{}") }
        val error = assertFailsWith<NetworkRequestException> { runBlocking { bluesky.profile("nobody.bsky.social") } }
        assertEquals(BlueskyErrorKind.BAD_RESPONSE, BlueskyErrorKind.of(error))
    }

    @Test
    fun theAppViewIsAskedBeforeEveryRequestAndABadOneFallsBack() = runBlocking {
        var setting: String? = ""
        val http = FakeAppView { ok("""{"did":"did:plc:z","handle":"bsky.app"}""") }
        val bluesky = BlueskyClient(http) { setting }
        bluesky.verify()
        setting = "custom.appview.test/"
        bluesky.verify()
        setting = "https://proxy.example:8443/bsky/"
        bluesky.verify()
        setting = "ftp://nope"
        bluesky.verify()
        assertEquals(
            listOf(
                "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=bsky.app",
                "https://custom.appview.test/xrpc/app.bsky.actor.getProfile?actor=bsky.app",
                "https://proxy.example:8443/bsky/xrpc/app.bsky.actor.getProfile?actor=bsky.app",
                "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=bsky.app",
            ),
            http.asked.map { it.toString() },
        )
    }

    @Test
    fun postSearchFromBlueskysAppViewGoesToItsApiAndPagesByTime() = runBlocking {
        val (bluesky, http) = client { ok(fixture("search_posts.json")) }
        val first = bluesky.searchPosts("bluesky", sort = "top")
        assertEquals("api.bsky.app", http.asked.single().host, "public.api.bsky.app answers searchPosts with 403")
        assertNull(http.asked.single().queryParameter("cursor"))
        assertEquals("until:2026-09-20T10:00:00.000Z", first.next)
        assertEquals(listOf("newer", "older", "unindexed"), first.items.map { it.uri.substringAfterLast('/') })
        assertEquals(BlueskyFacet(6, 14, BlueskyFacet.Kind.TAG, "bluesky"), first.items.first().facets.single())

        bluesky.searchPosts("bluesky", sort = "top", cursor = first.next)
        val second = http.asked.last()
        assertEquals("api.bsky.app", second.host)
        assertEquals("2026-09-20T10:00:00.000Z", second.queryParameter("until"))
        assertNull(second.queryParameter("cursor"))
        assertEquals("top", second.queryParameter("sort"))
    }

    @Test
    fun postSearchAlsoMovesOffApiBskyAppWrittenAsABareHost() = runBlocking {
        val (bluesky, http) = client("api.bsky.app") { ok("""{"posts":[${postView("last")}]}""") }
        assertNull(bluesky.searchPosts("bluesky").next, "no cursor, no next page")
        assertEquals("https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?q=bluesky&limit=20&sort=latest", http.asked.single().toString())
    }

    @Test
    fun postSearchOnAnotherAppViewCarriesItsParametersAndItsCursor() = runBlocking {
        val (bluesky, http) = client("https://appview.example") { ok("""{"posts":[${postView("result")}],"cursor":"next-page"}""") }
        val page = bluesky.searchPosts("art & design", limit = 500, cursor = "cursor+/=", sort = "top", author = " alice.test ", tags = listOf("#art", "  design  ", "#art", "", "#"))
        val url = http.asked.single()
        assertEquals("appview.example", url.host)
        assertEquals("/xrpc/app.bsky.feed.searchPosts", url.encodedPath)
        assertEquals("art & design", url.queryParameter("q"))
        assertEquals("100", url.queryParameter("limit"))
        assertEquals("cursor+/=", url.queryParameter("cursor"))
        assertEquals("top", url.queryParameter("sort"))
        assertEquals("alice.test", url.queryParameter("author"))
        assertEquals(listOf("art", "design"), url.queryParameterValues("tag"))
        assertEquals("next-page", page.next)

        bluesky.searchPosts("fallback", limit = -3, sort = "unsupported", author = " ")
        val plain = http.asked.last()
        assertEquals("latest", plain.queryParameter("sort"))
        assertEquals("1", plain.queryParameter("limit"))
        assertFalse("author" in plain.queryParameterNames)
    }

    @Test
    fun postsAreAskedInDeduplicatedBatchesOf25() = runBlocking {
        val (bluesky, http) = client { url -> ok("""{"posts":[${url.queryParameterValues("uris").joinToString(",") { postView(it!!.substringAfterLast('/')) }}]}""") }
        val uris = (0 until 27).map { "at://did:plc:alice/app.bsky.feed.post/post$it" }
        val posts = bluesky.posts(uris + uris.first() + "" + "https://example.org/not-an-at-uri" + uris.last())
        assertEquals(listOf(25, 2), http.asked.map { it.queryParameterValues("uris").size })
        assertEquals(uris, http.asked.flatMap { it.queryParameterValues("uris") })
        assertEquals(uris, posts.map { it.uri })
        bluesky.posts(emptyList())
        assertEquals(2, http.asked.size, "nothing to ask, nothing asked")
    }

    @Test
    fun feedsListsAndTheGraphAreReadAtTheirOwnEndpoints() = runBlocking {
        val (bluesky, http) = client { url ->
            ok(
                when (url.encodedPath.substringAfterLast('/')) {
                    "app.bsky.graph.getList" -> fixture("list.json")
                    "app.bsky.unspecced.getPopularFeedGenerators", "app.bsky.feed.getActorFeeds" -> fixture("feed_generators.json")
                    else -> """{"feed":[{"post":${postView("x")}}],"follows":[{"did":"did:plc:1","handle":"one.bsky.social"}],""" +
                        """"followers":[{"did":"did:plc:2","handle":"two.bsky.social"}],"repostedBy":[{"handle":"r.bsky.social"},{}],""" +
                        """"posts":[${postView("q")}],"lists":[{"uri":"at://did:plc:a/app.bsky.graph.list/1","name":"L"}],""" +
                        """"actors":[{"did":"did:plc:s","handle":"s.bsky.social"}],"suggestions":[{"handle":"like.bsky.social"}],"cursor":"more"}"""
                },
            )
        }
        assertEquals("x", bluesky.feed(Bluesky.DISCOVER_FEED_URI).items.single().text)
        assertEquals("x", bluesky.listFeed("at://did:plc:a/app.bsky.graph.list/1", cursor = "c").items.single().text)
        assertEquals("one.bsky.social", bluesky.follows("alice.bsky.social").items.single().handle)
        assertEquals("two.bsky.social", bluesky.followers("alice.bsky.social", cursor = "f2").items.single().handle)
        assertEquals("L", bluesky.lists("alice.bsky.social").items.single().name)
        assertEquals("Cool people", bluesky.list("at://did:plc:alice/app.bsky.graph.list/3abc").list?.name)
        assertEquals(2, bluesky.actorFeeds("alice.bsky.social").items.size)
        assertEquals("next", bluesky.popularFeedGenerators(query = "cats", cursor = "p2").next)
        assertEquals("s.bsky.social", bluesky.suggestions(limit = 5).single().handle)
        assertEquals("like.bsky.social", bluesky.suggestedFollowsByActor("alice.bsky.social").single().handle)
        assertEquals(listOf("r.bsky.social"), bluesky.repostedBy("at://did:plc:alice/app.bsky.feed.post/x").items.map { it.handle }, "someone without a name is left out")
        assertEquals("q", bluesky.quotes("at://did:plc:alice/app.bsky.feed.post/x", cursor = "q2").items.single().text)
        assertEquals("more", bluesky.searchActorsPage("blue sky", limit = 25, cursor = "second/page=").next)
        assertEquals(1, bluesky.searchActors("one").size)

        assertEquals(
            listOf(
                "app.bsky.feed.getFeed", "app.bsky.feed.getListFeed", "app.bsky.graph.getFollows", "app.bsky.graph.getFollowers",
                "app.bsky.graph.getLists", "app.bsky.graph.getList", "app.bsky.feed.getActorFeeds", "app.bsky.unspecced.getPopularFeedGenerators",
                "app.bsky.actor.getSuggestions", "app.bsky.graph.getSuggestedFollowsByActor", "app.bsky.feed.getRepostedBy", "app.bsky.feed.getQuotes",
                "app.bsky.actor.searchActors", "app.bsky.actor.searchActors",
            ),
            http.methods,
        )
        val query = http.asked.map { url -> url.queryParameterNames.associateWith { url.queryParameter(it) } }
        assertEquals(mapOf("feed" to Bluesky.DISCOVER_FEED_URI, "limit" to "30"), query[0])
        assertEquals(mapOf("list" to "at://did:plc:a/app.bsky.graph.list/1", "limit" to "30", "cursor" to "c"), query[1])
        assertEquals(mapOf("actor" to "alice.bsky.social", "limit" to "100"), query[2])
        assertEquals(mapOf("actor" to "alice.bsky.social", "limit" to "100", "cursor" to "f2"), query[3])
        assertEquals(mapOf("actor" to "alice.bsky.social", "limit" to "50"), query[4])
        assertEquals(mapOf("list" to "at://did:plc:alice/app.bsky.graph.list/3abc", "limit" to "100"), query[5])
        assertEquals(mapOf("actor" to "alice.bsky.social", "limit" to "50"), query[6])
        assertEquals(mapOf("limit" to "20", "cursor" to "p2", "query" to "cats"), query[7])
        assertEquals(mapOf("limit" to "5"), query[8])
        assertEquals(mapOf("actor" to "alice.bsky.social"), query[9])
        assertEquals(mapOf("uri" to "at://did:plc:alice/app.bsky.feed.post/x", "limit" to "50"), query[10])
        assertEquals(mapOf("uri" to "at://did:plc:alice/app.bsky.feed.post/x", "limit" to "50", "cursor" to "q2"), query[11])
        assertEquals(mapOf("q" to "blue sky", "limit" to "25", "cursor" to "second/page="), query[12])
        assertEquals(mapOf("q" to "one", "limit" to "10"), query[13])
    }

    @Test
    fun aThreadIsAskedWithItsDepthAndAMissingOneIsNotFound() {
        val (bluesky, http) = client { ok(fixture("post_thread.json")) }
        val thread = runBlocking { bluesky.postThread("at://did:plc:alice/app.bsky.feed.post/focal") }
        assertEquals("focal", thread.post.text)
        val url = http.asked.single()
        assertEquals("/xrpc/app.bsky.feed.getPostThread", url.encodedPath)
        assertEquals(listOf("at://did:plc:alice/app.bsky.feed.post/focal", "10", "80"), listOf("uri", "depth", "parentHeight").map { url.queryParameter(it) })

        val (empty, _) = client { ok("""{"thread":{"${'$'}type":"app.bsky.feed.defs#notFoundPost"}}""") }
        val error = assertFailsWith<NetworkRequestException> { runBlocking { empty.postThread("at://gone") } }
        assertEquals(BlueskyErrorKind.NOT_FOUND, BlueskyErrorKind.of(error))
    }

    @Test
    fun feedGeneratorsAreAskedTogetherWithRepeatedFeedsParameters() = runBlocking {
        val (bluesky, http) = client { ok(fixture("feed_generators.json")) }
        val feeds = bluesky.feedGenerators(listOf(" ${Bluesky.DISCOVER_FEED_URI} ", "") + (1..30).map { "at://did:plc:a/app.bsky.feed.generator/f$it" })
        assertEquals(2, feeds.size)
        val asked = http.asked.single().queryParameterValues("feeds")
        assertEquals(25, asked.size)
        assertEquals(Bluesky.DISCOVER_FEED_URI, asked.first())
        assertTrue(bluesky.feedGenerators(listOf(" ", "")).isEmpty())
        assertEquals(1, http.asked.size)
    }

    @Test
    fun oneFeedGeneratorIsReadFromItsViewAndOneWithoutAUriIsABadResponse() = runBlocking {
        val (bluesky, http) = client { ok("""{"view":{"uri":"${Bluesky.DISCOVER_FEED_URI}","displayName":"Discover"},"isOnline":true}""") }
        assertEquals("Discover", bluesky.feedGenerator(Bluesky.DISCOVER_FEED_URI).displayName)
        assertEquals(Bluesky.DISCOVER_FEED_URI, http.asked.single().queryParameter("feed"))
        val (empty, _) = client { ok("{}") }
        val error = assertFailsWith<NetworkRequestException> { runBlocking { empty.feedGenerator("at://x") } }
        assertEquals(BlueskyErrorKind.BAD_RESPONSE, BlueskyErrorKind.of(error))
    }

    @Test
    fun webLinksResolveThroughTheirOwnersDid() = runBlocking {
        val (bluesky, http) = client { url ->
            when (url.encodedPath.substringAfterLast('/')) {
                "app.bsky.actor.getProfile" -> ok("""{"did":"did:plc:owner","handle":"alice.bsky.social"}""")
                else -> ok("""{"starterPack":{"list":{"uri":"at://did:plc:owner/app.bsky.graph.list/1"}}}""")
            }
        }
        val web = BlueskyRef.Web("alice.bsky.social", "3abc")
        assertEquals("at://did:plc:owner/app.bsky.graph.list/3abc", bluesky.resolveListUri(web))
        assertEquals("at://did:plc:owner/app.bsky.feed.generator/3abc", bluesky.resolveFeedUri(web))
        val pack = bluesky.resolveStarterPackUri(web)
        assertEquals("at://did:plc:owner/app.bsky.graph.starterpack/3abc", pack)
        assertEquals("at://did:plc:owner/app.bsky.graph.list/1", bluesky.starterPackListUri(pack))
        assertEquals(pack, http.asked.last().queryParameter("starterPack"))
        assertEquals("at://did:plc:a/app.bsky.graph.list/x", bluesky.resolveListUri(BlueskyRef.AtUri(" at://did:plc:a/app.bsky.graph.list/x ")))
        assertEquals(4, http.asked.size, "an at:// URI needs no request")
    }

    @Test
    fun aLinkThatCannotBeResolvedSaysWhy() {
        val (bluesky, http) = client { ok("""{"handle":"alice.bsky.social"}""") }
        val incomplete = assertFailsWith<NetworkRequestException> { runBlocking { bluesky.resolveListUri(BlueskyRef.Web(" ", "3abc")) } }
        assertEquals(400, incomplete.code)
        assertTrue(http.asked.isEmpty())
        val ownerless = assertFailsWith<NetworkRequestException> { runBlocking { bluesky.resolveFeedUri(BlueskyRef.Web("alice.bsky.social", "cats")) } }
        assertEquals(BlueskyErrorKind.NOT_FOUND, BlueskyErrorKind.of(ownerless))
    }

    @Test
    fun peopleSearchReadsANamedAccountDirectlyAndSearchesAnythingElse() = runBlocking {
        val (bluesky, http) = client { url ->
            if (url.encodedPath.endsWith("getProfile")) ok("""{"did":"did:plc:maya","handle":"maya.bsky.social"}""")
            else ok("""{"actors":[{"did":"did:plc:m","handle":"m.bsky.social"}],"cursor":"next"}""")
        }
        val named = bluesky.searchPeople(" https://bsky.app/profile/maya.bsky.social ")
        assertEquals(listOf("maya.bsky.social"), named.items.map { it.handle })
        assertNull(named.next)
        assertEquals("maya.bsky.social", http.asked.last().queryParameter("actor"))

        val searched = bluesky.searchPeople(" maya ", cursor = "c1")
        assertEquals("next", searched.next)
        assertEquals(mapOf("q" to "maya", "limit" to "25", "cursor" to "c1"), http.asked.last().let { url -> url.queryParameterNames.associateWith { url.queryParameter(it) } })
    }

    @Test
    fun aPostLinkInPostSearchReadsThatOnePost() = runBlocking {
        val (bluesky, http) = client { url ->
            if (url.encodedPath.endsWith("getProfile")) ok("""{"did":"did:plc:maya","handle":"maya.bsky.social"}""")
            else ok("""{"posts":[${postView("root", "maya.bsky.social", "did:plc:maya")}]}""")
        }
        val page = bluesky.searchPostsFor("https://bsky.app/profile/maya.bsky.social/post/root")
        assertEquals("root", page.items.single().text)
        assertNull(page.next)
        assertEquals(listOf("app.bsky.actor.getProfile", "app.bsky.feed.getPosts"), http.methods)
        assertEquals(listOf("at://did:plc:maya/app.bsky.feed.post/root"), http.asked.last().queryParameterValues("uris"))

        bluesky.searchPostsFor("at://did:plc:maya/app.bsky.feed.post/root")
        assertEquals("app.bsky.feed.getPosts", http.methods.last(), "an at:// URI needs no handle resolved")
        assertEquals(3, http.asked.size)
    }

    @Test
    fun aPostLinkToNothingIsNotFound() {
        val (bluesky, _) = client { ok("""{"posts":[]}""") }
        val error = assertFailsWith<NetworkRequestException> { runBlocking { bluesky.searchPostsFor("at://did:plc:maya/app.bsky.feed.post/gone") } }
        assertEquals(BlueskyErrorKind.NOT_FOUND, BlueskyErrorKind.of(error))
    }

    @Test
    fun aLoneHashtagSearchesThatExactTagAndFiltersReachTheSearch() = runBlocking {
        val (bluesky, http) = client { ok("""{"posts":[]}""") }
        bluesky.searchPostsFor(" #art ")
        val tagged = http.asked.last()
        assertEquals("", tagged.queryParameter("q"))
        assertEquals(listOf("art"), tagged.queryParameterValues("tag"))
        assertEquals("25", tagged.queryParameter("limit"))

        bluesky.searchPostsFor("sketches", cursor = "until:2026-09-20T10:00:00.000Z", sort = "top", author = "maya.bsky.social", tag = "art")
        val filtered = http.asked.last()
        assertEquals("sketches", filtered.queryParameter("q"))
        assertEquals("top", filtered.queryParameter("sort"))
        assertEquals("maya.bsky.social", filtered.queryParameter("author"))
        assertEquals(listOf("art"), filtered.queryParameterValues("tag"))
        assertEquals("2026-09-20T10:00:00.000Z", filtered.queryParameter("until"))
    }

    @Test
    fun aProfileTabReadsByDidWithItsFilterAndKeepsWhatTheTabShows() = runBlocking {
        val (bluesky, http) = client { ok(fixture("author_feed.json")) }
        val profile = BlueskyProfile(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")
        val replies = bluesky.profileTab(profile, BlueskyProfileTab.REPLIES, cursor = "r2")
        assertEquals(listOf("answer"), replies.items.map { it.uri.substringAfterLast('/') })
        assertEquals("2026-07-29T08:00:00.000Z", replies.next)
        assertEquals(mapOf("actor" to "did:plc:alice", "limit" to "20", "cursor" to "r2", "filter" to "posts_with_replies"), http.asked.last().let { url -> url.queryParameterNames.associateWith { url.queryParameter(it) } })

        val media = bluesky.profileTab(profile, BlueskyProfileTab.MEDIA)
        assertEquals(listOf("pics", "clip", "both"), media.items.map { it.uri.substringAfterLast('/') })
        assertEquals("posts_with_media", http.asked.last().queryParameter("filter"))
        assertEquals(11, bluesky.profileTab(profile.copy(did = ""), BlueskyProfileTab.POSTS).items.size)
        assertEquals("alice.bsky.social", http.asked.last().queryParameter("actor"), "without a DID, the handle")
        assertEquals("posts_and_author_threads", http.asked.last().queryParameter("filter"))
    }

    @Test
    fun aPinnedPostIsReadOnItsOwn() = runBlocking {
        val (bluesky, http) = client { ok("""{"posts":[${postView("pin")}]}""") }
        val profile = BlueskyProfile(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice", pinnedPostUri = "at://did:plc:alice/app.bsky.feed.post/pin")
        assertEquals("pin", bluesky.pinnedPost(profile)?.text)
        assertNull(bluesky.pinnedPost(profile.copy(pinnedPostUri = "at://did:plc:alice/app.bsky.feed.post/other")), "a different post is not the pin")
        assertNull(bluesky.pinnedPost(profile.copy(pinnedPostUri = null)))
        assertEquals(2, http.asked.size)
    }

    @Test
    fun walkingAFollowListStopsAtAnEmptyPageOrARepeatedCursor() = runBlocking {
        val pages = mapOf(
            null to """{"follows":[{"handle":"a.bsky.social"}],"cursor":"2"}""",
            "2" to """{"follows":[{"handle":"b.bsky.social"}],"cursor":"2"}""",
        )
        val (bluesky, http) = client { url -> ok(pages.getValue(url.queryParameter("cursor"))) }
        val read = BlueskyPaging.walk(pauseMillis = 0) { cursor -> bluesky.follows("alice.bsky.social", cursor = cursor) }.toList()
        assertEquals(listOf(listOf("a.bsky.social"), listOf("b.bsky.social")), read.map { page -> page.map { it.handle } })
        assertEquals(2, http.asked.size, "the repeated cursor is not read again")

        val (empty, emptyHttp) = client { ok("""{"follows":[],"cursor":"more"}""") }
        BlueskyPaging.walk(pauseMillis = 0) { cursor -> empty.follows("x.bsky.social", cursor = cursor) }.toList()
        assertEquals(1, emptyHttp.asked.size, "a page that brings no one ends the walk")
    }
}
