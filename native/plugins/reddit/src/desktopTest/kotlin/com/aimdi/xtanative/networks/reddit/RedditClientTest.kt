package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.net.SocketException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The client's routes, as XTA's reddit_client_test, reddit_about_search_test,
 * reddit_community_icon_test and reddit_gallery_fetch_test hold them, over a
 * fake Reddit that records every request.
 */
class RedditClientTest {
    private val app = RedditSession(clientId = "id")

    private fun reddit(answer: suspend (Asked) -> XResponse) = FakeReddit(answer)

    /** A token endpoint, and [listing] for every other request. */
    private fun apiReddit(listing: String = listingBody()) =
        reddit { if (it.url.encodedPath.contains("access_token")) page(tokenBody()) else page(listing) }

    private fun refusal(block: suspend () -> Unit): NetworkRequestException =
        assertFailsWith<NetworkRequestException> { runBlocking { block() } }

    @Test
    fun aClientIdUsesTheInstalledClientGrantWithTheIdAsBasicAuth() = runBlocking {
        val fake = apiReddit()
        RedditClient(fake).listing("dartlang", session = RedditSession(clientId = "my_client_id"))

        val token = fake.asked.first()
        assertEquals("https://www.reddit.com/api/v1/access_token", token.url.toString())
        assertEquals("POST", token.method)
        assertTrue("grant_type=https://oauth.reddit.com/grants/installed_client" in token.body!!)
        assertTrue("device_id=${RedditClient.DEVICE_ID}" in token.body!!)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("my_client_id:".toByteArray()), token.headers["Authorization"])
        assertEquals(RedditClient.API_USER_AGENT, token.headers["User-Agent"])
        assertEquals(RedditTransport.FORM, token.contentType)
        assertEquals("oauth.reddit.com", fake.asked.last().url.host)
        assertEquals("Bearer tok_123", fake.asked.last().headers["Authorization"])
    }

    @Test
    fun theTokenIsReusedUntilAMinuteBeforeItExpires() = runBlocking {
        val fake = apiReddit()
        val client = RedditClient(fake)
        client.listing("dartlang", session = app)
        client.listing("flutterdev", session = app)

        assertEquals(1, fake.asked.count { it.url.encodedPath.contains("access_token") })
        assertTrue(client.hasToken)

        val shortLived = reddit { if (it.url.encodedPath.contains("access_token")) page(tokenBody(expiresIn = 30)) else page(listingBody()) }
        val again = RedditClient(shortLived)
        again.listing("dartlang", session = app)
        again.listing("dartlang", session = app)
        assertEquals(2, shortLived.asked.count { it.url.encodedPath.contains("access_token") }, "shorter than the margin is already expired")
        assertFalse(again.hasToken)
    }

    @Test
    fun withoutAnAccountWwwIsReadFirstWithNoTokenAndAsABrowser() = runBlocking {
        val fake = reddit { page(WWW_LISTING) }
        val listing = RedditClient(fake).listing("dartlang", session = RedditSession(clientId = "  "))

        val asked = fake.asked.single()
        assertEquals("www.reddit.com", asked.url.host)
        assertEquals("/svc/shreddit/community-more-posts/hot/", asked.url.encodedPath)
        assertEquals("dartlang", asked.url.queryParameter("name"))
        assertNull(asked.headers["Authorization"])
        assertEquals(RedditClient.WEB_USER_AGENT, asked.headers["User-Agent"])
        assertTrue(asked.headers["User-Agent"]!!.startsWith("Mozilla/"))
        assertEquals("en-US,en;q=0.9", asked.headers["Accept-Language"])
        assertEquals("abc123", listing.items.single().id)
        assertEquals("t3_abc123", listing.next, "the cursor is the fullname, whichever route reads the next page")
    }

    @Test
    fun whenWwwRefusesOldRedditIsScrapedAsABrowser() = runBlocking {
        val fake = reddit(wwwRefused { page(LISTING_HTML) })
        val listing = RedditClient(fake).listing("dartlang")

        val asked = fake.asked.last()
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
        assertEquals("/r/dartlang/hot", asked.url.encodedPath)
        assertNull(asked.headers["Authorization"])
        assertEquals(RedditClient.WEB_USER_AGENT, asked.headers["User-Agent"])
        assertEquals("abc123", listing.items.single().id)
    }

    @Test
    fun unreadableHtmlFallsBackToTheJsonListings() = runBlocking {
        val fake = reddit { if (it.url.encodedPath.endsWith(".json")) page(listingBody()) else page(UNREADABLE_PAGE) }
        val listing = RedditClient(fake).listing("dartlang")

        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts.take(2))
        assertTrue(fake.urls.any { it.encodedPath == "/r/dartlang/hot.json" })
        assertTrue(listing.items.isNotEmpty())
    }

    @Test
    fun theOver18GateIsAnsweredWithACookieAndTheConsentKept() = runBlocking {
        var served = 0
        val fake = reddit(wwwRefused { if (++served == 1) page(OVER18_GATE) else page(LISTING_HTML) })
        val client = RedditClient(fake)
        val listing = client.listing("dartlang")

        val old = fake.asked.filterNot { it.isWww }
        assertNull(old[0].headers["Cookie"])
        assertTrue("over18=1" in old[1].headers["Cookie"]!!)
        assertTrue(listing.items.isNotEmpty())

        client.listing("flutterdev")
        assertTrue("over18=1" in fake.asked.last().headers["Cookie"]!!, "consent once given rides along")
    }

    @Test
    fun aRefusalFromWwwIsAskedOfOldRedditAndTheJsonRoutes() = runBlocking {
        val fake = reddit {
            when {
                !it.url.encodedPath.endsWith(".json") -> page("", 403)
                it.url.host == "www.reddit.com" -> page("""{"error":403}""", 403)
                else -> page(listingBody())
            }
        }
        val listing = RedditClient(fake).listing("dartlang")

        assertEquals(listOf("www.reddit.com", "old.reddit.com", "www.reddit.com", "old.reddit.com"), fake.hosts)
        assertEquals("/r/dartlang/hot.json", fake.urls.last().encodedPath)
        assertTrue(listing.items.isNotEmpty())
    }

    @Test
    fun aPageThatReadsIsNotAskedTwice() = runBlocking {
        val fake = reddit { page(if (it.isWww) WWW_LISTING else LISTING_HTML) }
        RedditClient(fake).listing("dartlang")

        assertEquals(1, fake.asked.size, "the other routes are only paid for on failure")
    }

    @Test
    fun theRouteThatAnsweredIsAskedFirstNextTime() = runBlocking {
        val fake = reddit(wwwRefused { page(LISTING_HTML) })
        val client = RedditClient(fake)
        client.listing("dartlang")
        client.listing("flutterdev")

        assertEquals(listOf("www.reddit.com", "old.reddit.com", "old.reddit.com"), fake.hosts)
    }

    @Test
    fun aRouteThatRefusedGoesLastForFiveMinutesThenBackInItsPlace() {
        var now = 1_700_000_000_000L
        val routes = PublicRoutes { now }
        routes.refuse(PublicRoute.WWW)

        assertEquals(listOf(PublicRoute.HTML, PublicRoute.WWW_JSON, PublicRoute.OLD_JSON, PublicRoute.WWW), routes.inOrder(), "a refusal is a wait, not a verdict")
        now += PublicRoutes.COOLDOWN_MILLIS + 1
        assertEquals(PublicRoute.entries.toList(), routes.inOrder())
        routes.remember(PublicRoute.OLD_JSON)
        assertEquals(PublicRoute.OLD_JSON, routes.inOrder().first(), "the route that answered is asked first")
    }

    @Test
    fun whenEveryRouteRefusedOnlyTheOneWhoseWaitEndsFirstIsAsked() {
        var now = 0L
        val routes = PublicRoutes { now }
        PublicRoute.entries.forEach { now += 1; routes.refuse(it) }

        assertEquals(listOf(PublicRoute.WWW), routes.inOrder())
    }

    @Test
    fun anAnonymousReaderRedditRefusesIsToldWhatHappened() {
        mapOf(403 to "both public hosts", 429 to "both public hosts").forEach { (status, detail) ->
            val error = refusal { RedditClient(reddit { page("""{"error":$status}""", status) }).listing("dartlang") }
            assertEquals(status, error.code, "HTTP $status")
            assertTrue(detail in error.message!!, error.message)
            assertEquals("reddit", error.network)
        }
    }

    @Test
    fun anUnreachableHostIsAFailedConnectionNotARefusal() {
        val offline = reddit { throw SocketException("no route") }

        assertFailsWith<SocketException> { runBlocking { RedditClient(offline).listing("dartlang") } }
        assertFailsWith<SocketException> { runBlocking { RedditClient(offline).listing("dartlang", session = app) } }
    }

    @Test
    fun aMissingCommunityIsNotAskedOfTheNextRoute() {
        val fake = reddit { page("""{"error":404}""", 404) }
        val error = refusal { RedditClient(fake).listing("dartlang") }

        assertEquals(404, error.code)
        // www's feed piece answers an unknown community with an empty page, so its 404 says the piece moved;
        // old.reddit's is about the community, which is missing on every host.
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
    }

    @Test
    fun aRejectedClientIdIsUnauthorised() {
        val error = refusal { RedditClient(reddit { page("""{"error":401}""", 401) }).verify("wrong") }

        assertEquals(401, error.code)
        assertFailsWith<IllegalArgumentException> { runBlocking { RedditClient(reddit { page("") }).verify("  ") } }
    }

    @Test
    fun aCommunityWithNothingInItIsEmptyNotBrokenAndNotAskedElsewhere() = runBlocking {
        val www = reddit { page(WWW_EMPTY) }
        val wwwListing = RedditClient(www).listing("dartlang")
        val old = reddit(wwwRefused { page(EMPTY_LISTING_HTML) })
        val oldListing = RedditClient(old).listing("dartlang")

        assertTrue(wwwListing.items.isEmpty())
        assertNull(wwwListing.next)
        assertEquals(listOf("www.reddit.com"), www.hosts)
        assertTrue(oldListing.items.isEmpty())
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), old.hosts)
    }

    @Test
    fun aPageWhoseShapeChangedIsWorthAskingAnotherRouteAbout() = runBlocking {
        val fake = reddit { if (it.url.encodedPath.endsWith(".json")) page(listingBody()) else page(UNREADABLE_PAGE) }

        assertTrue(RedditClient(fake).listing("dartlang").items.isNotEmpty())
        assertTrue(fake.asked.size > 1, "unreadable is the one page kind that is not a verdict")
    }

    @Test
    fun aPrivateBannedOrQuarantinedCommunityIsReportedAsItselfAndEndsTheSearch() {
        mapOf(PRIVATE_PAGE to (404 to "private"), BANNED_PAGE to (404 to "banned"), QUARANTINED_PAGE to (403 to "quarantined"))
            .forEach { (body, verdict) ->
                val fake = reddit(wwwRefused { page(body) })
                val error = refusal { RedditClient(fake).listing("dartlang") }
                assertEquals(verdict.first, error.code, verdict.second)
                assertTrue(verdict.second in error.message!!, error.message)
                assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts, "no other host disagrees about a banned community")
            }
    }

    @Test
    fun aLoginPageRefusesOnlyItsHostAndTheNextRouteIsAsked() = runBlocking {
        val fake = reddit(wwwRefused { if (it.url.encodedPath.endsWith(".json")) page(listingBody()) else page(LOGIN_PAGE) })

        assertTrue(RedditClient(fake).listing("dartlang").items.isNotEmpty())
        assertTrue(fake.urls.any { it.encodedPath == "/r/dartlang/hot.json" })
    }

    @Test
    fun aRedirectToTheLoginPageIsARefusalOfItsHost() = runBlocking {
        val fake = reddit {
            when {
                it.isWww -> page("", 403)
                it.url.host == "old.reddit.com" && !it.url.encodedPath.endsWith(".json") ->
                    page("", 302, mapOf("Location" to listOf("https://www.reddit.com/login/?dest=x")))
                else -> page(listingBody())
            }
        }

        assertTrue(RedditClient(fake).listing("dartlang").items.isNotEmpty())
    }

    @Test
    fun aPrivateCommunityRefusedWithA403StillNamesTheReason() {
        val error = refusal { RedditClient(reddit { page(PRIVATE_PAGE, 403) }).listing("dartlang") }

        assertEquals(404, error.code)
        assertTrue("private" in error.message!!)
    }

    @Test
    fun theApiIsAskedForTheSortLimitAndRawJson() = runBlocking {
        val fake = apiReddit(listingBody(after = "t3_abc123"))
        val listing = RedditClient(fake).listing("r/dartlang", sort = RedditSort.NEW, limit = 10, session = app)

        val url = fake.urls.last()
        assertEquals("oauth.reddit.com", url.host)
        assertEquals("/r/dartlang/new", url.encodedPath)
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("1", url.queryParameter("raw_json"))
        assertEquals("t3_abc123", listing.next)
        val post = listing.items.single()
        assertEquals("abc123", post.id)
        assertEquals("Dart 4 is out", post.title)
        assertEquals("dartlang", post.subreddit)
        assertEquals(412, post.score)
        assertEquals(37, post.commentCount)
        assertNotNull(post.createdAt)
        assertEquals("https://b.thumbs.redditmedia.com/x.jpg", post.thumbnailUrl)
    }

    @Test
    fun aPageAsksForFiftyPostsByDefault() = runBlocking {
        val fake = apiReddit()
        RedditClient(fake).listing("dartlang", session = app)

        assertEquals(50, RedditClient.PAGE_SIZE)
        assertEquals("50", fake.urls.last().queryParameter("limit"))
    }

    @Test
    fun theTimeWindowGoesWithTopAndControversialOnly() = runBlocking {
        val fake = apiReddit()
        val client = RedditClient(fake)
        client.listing("dartlang", RedditSort.TOP, RedditTimeFilter.WEEK, session = app)
        client.listing("dartlang", RedditSort.HOT, RedditTimeFilter.YEAR, session = app)
        client.listing("dartlang", RedditSort.CONTROVERSIAL, RedditTimeFilter.ALL, session = app)

        val listings = fake.urls.filter { it.host == "oauth.reddit.com" }
        assertEquals("week", listings[0].queryParameter("t"))
        assertNull(listings[1].queryParameter("t"))
        assertEquals("all", listings[2].queryParameter("t"))
    }

    @Test
    fun theTimeWindowRidesEveryAccountFreeRouteInItsSpelling() = runBlocking {
        val fake = reddit { if (it.url.encodedPath.endsWith(".json")) page(listingBody()) else page(UNREADABLE_PAGE) }
        RedditClient(fake).listing("dartlang", RedditSort.TOP, RedditTimeFilter.MONTH)

        val urls = fake.urls
        assertEquals("/svc/shreddit/community-more-posts/top/", urls[0].encodedPath)
        assertEquals("MONTH", urls[0].queryParameter("t"))
        assertEquals("/r/dartlang/top", urls[1].encodedPath)
        assertEquals("month", urls[1].queryParameter("t"))
        assertNull(urls[1].queryParameter("raw_json"))
        assertTrue(urls.any { it.encodedPath == "/r/dartlang/top.json" && it.queryParameter("t") == "month" })
    }

    @Test
    fun theNextPageIsAskedForAfterTheCursor() = runBlocking {
        val api = apiReddit()
        RedditClient(api).listing("dartlang", after = "t3_abc123", session = app)
        val www = reddit { page(WWW_LISTING) }
        RedditClient(www).listing("dartlang", after = "t3_abc123")

        assertEquals("t3_abc123", api.urls.last().queryParameter("after"))
        assertEquals("dDNfYWJjMTIz", www.urls.last().queryParameter("after"), "as the site's own paging writes it")
    }

    @Test
    fun aNameThatIsNoCommunityNeverLeavesTheDevice() {
        val fake = apiReddit()
        val error = refusal { RedditClient(fake).listing("not a subreddit", session = app) }

        assertEquals(404, error.code)
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun eachStatusOfTheApiIsItsOwnFailure() {
        listOf(403, 404, 429, 500).forEach { status ->
            val fake = reddit { if (it.url.encodedPath.contains("access_token")) page(tokenBody()) else page("""{"error":$status}""", status) }
            val error = refusal { RedditClient(fake).listing("dartlang", session = app) }
            assertEquals(status, error.code)
            assertEquals("HTTP $status from oauth.reddit.com", error.message)
        }
    }

    @Test
    fun a401DropsTheCachedTokenSoTheNextTryAuthorisesAgain() = runBlocking {
        var listings = 0
        val fake = reddit {
            if (it.url.encodedPath.contains("access_token")) page(tokenBody()) else if (++listings == 1) page("""{"error":401}""", 401) else page(listingBody())
        }
        val client = RedditClient(fake)

        assertFailsWith<NetworkRequestException> { client.listing("dartlang", session = app) }
        assertFalse(client.hasToken)
        client.listing("dartlang", session = app)
        assertEquals(2, fake.asked.count { it.url.encodedPath.contains("access_token") })
    }

    @Test
    fun htmlWhereJsonWasAskedForIsUnreadableNotACrash() {
        val fake = reddit { if (it.url.encodedPath.contains("access_token")) page(tokenBody()) else page("<html>blocked</html>") }
        val error = refusal { RedditClient(fake).listing("dartlang", session = app) }

        assertEquals(200, error.code)
        assertEquals("Response was not a JSON object", error.message)
    }

    @Test
    fun aUserTokenGoesToTheApiAsItIsWithoutMintingAnother() = runBlocking {
        val fake = reddit { page(EMPTY_LISTING_JSON) }
        RedditClient(fake).listing("dartlang", session = RedditSession(clientId = "my_id", userToken = "user_tok"))

        assertEquals("oauth.reddit.com", fake.asked.single().url.host)
        assertEquals("Bearer user_tok", fake.asked.single().headers["Authorization"])
        assertEquals(RedditClient.API_USER_AGENT, fake.asked.single().headers["User-Agent"])
    }

    @Test
    fun askingForTheAccountFreeRouteKeepsItEvenWithATokenThere() = runBlocking {
        val fake = reddit { page(WWW_LISTING) }
        RedditClient(fake).listing("dartlang", session = RedditSession(clientId = "app", userToken = "user_tok", preferPublic = true))

        assertEquals("www.reddit.com", fake.asked.single().url.host)
        assertNull(fake.asked.single().headers["Authorization"])
    }

    private val permalink = "/r/dartlang/comments/abc123/dart_4_is_out/"

    private val threadHtml = """
        <!doctype html><html><body>
          <div id="siteTable"><div class="thing" data-fullname="t3_abc123" data-url="https://dart.dev/blog"></div></div>
          <div class="commentarea"><div class="sitetable nestedlisting">
            <div class="thing id-t1_c1 comment" data-fullname="t1_c1" data-author="someone">
              <div class="entry unvoted">
                <p class="tagline"><span class="score unvoted">3 points</span><time datetime="2026-07-01T10:00:00+00:00"></time></p>
                <form class="usertext"><div class="usertext-body"><div class="md"><p>Hello</p></div></div></form>
              </div>
              <div class="child"></div>
            </div>
          </div></div>
        </body></html>"""

    private fun threadJson(body: String = "Hello") =
        """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc123","title":"Dart 4 is out","subreddit":"dartlang",""" +
            """"permalink":"$permalink","selftext":"Body","url":"https://dart.dev/blog","is_self":true}}]}},""" +
            """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"c1","author":"someone","body":"$body","score":3,""" +
            """"created_utc":1769000000,"permalink":"${permalink}c1/","replies":""}}]}}]"""

    @Test
    fun aSignedInReaderReadsAThreadFromTheApi() = runBlocking {
        val fake = reddit { page(threadJson()) }
        val thread = RedditClient(fake).thread(permalink, session = RedditSession(clientId = "app", userToken = "user_tok"))

        assertEquals(listOf("oauth.reddit.com"), fake.hosts)
        assertEquals("Bearer user_tok", fake.asked.single().headers["Authorization"])
        assertEquals("/r/dartlang/comments/abc123/dart_4_is_out.json", fake.urls.single().encodedPath)
        assertEquals("500", fake.urls.single().queryParameter("limit"))
        assertEquals("c1", thread.comments.single().id)
        assertEquals("Body", thread.selfText)
    }

    @Test
    fun aRefusedApiThreadFallsBackToTheWebsite() = runBlocking {
        val fake = reddit { if (it.url.host == "oauth.reddit.com") page("""{"error":403}""", 403) else page(threadHtml) }
        val thread = RedditClient(fake).thread(permalink, session = RedditSession(clientId = "app", userToken = "user_tok"))

        // www could not read old.reddit's markup, so old.reddit's page was next.
        assertEquals(listOf("oauth.reddit.com", "www.reddit.com", "old.reddit.com"), fake.hosts)
        assertEquals("Hello", thread.comments.single().body)
        assertEquals("https://dart.dev/blog", thread.postUrl)
    }

    @Test
    fun anEmptyScrapeFallsBackToThePublicJson() = runBlocking {
        val emptyThread = """<!doctype html><html><body><div id="siteTable"></div><div class="commentarea"></div></body></html>"""
        val fake = reddit { if (it.url.encodedPath.endsWith(".json")) page(threadJson("From JSON")) else page(emptyThread) }
        val thread = RedditClient(fake).thread(permalink)

        assertEquals(listOf("www.reddit.com", "old.reddit.com", "www.reddit.com"), fake.hosts)
        assertEquals("From JSON", thread.comments.single().body)
    }

    @Test
    fun theAccountFreeRouteIsKeptForAThreadEvenWithATokenThere() = runBlocking {
        val fake = reddit { page(threadHtml) }
        val thread = RedditClient(fake).thread(permalink, session = RedditSession(clientId = "app", userToken = "user_tok", preferPublic = true))

        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
        assertTrue(fake.asked.none { "Authorization" in it.headers })
        assertEquals(1, thread.comments.size)
    }

    private val flutterThread = "/r/FlutterDev/comments/1xa75i8/why_flutter_text_looks_lighter_than_swiftui_on_ios/"

    @Test
    fun aThreadIsReadFromThePieceThatLoadsItsComments() = runBlocking {
        val fake = reddit { page(fixture("comments.html")) }
        val thread = RedditClient(fake).thread(flutterThread)

        assertEquals("/svc/shreddit/comments/r/FlutterDev/t3_1xa75i8", fake.urls.single().encodedPath)
        assertNull(fake.urls.single().query, "best is the default")
        assertEquals("TJGhinder", thread.comments.first().author)
        assertTrue(thread.comments.last().isStub, "Reddit held more back at the top")
        assertEquals(flutterThread, thread.comments.last().permalink)
    }

    @Test
    fun theChosenOrderRidesTheCommentsPieceInItsSpelling() = runBlocking {
        val fake = reddit { page(fixture("comments.html")) }
        RedditClient(fake).thread(flutterThread, RedditCommentSort.TOP)

        assertEquals("TOP", fake.urls.single().queryParameter("sort"))
    }

    @Test
    fun aCommentsPageAsksForThatCommentInEitherSpelling() {
        listOf("/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/", "/r/FlutterDev/comments/1xa75i8/why_flutter/phuyrt3/").forEach {
            assertEquals("/svc/shreddit/comments/r/FlutterDev/t3_1xa75i8/t1_phuyrt3", RedditLinks.wwwCommentsUrl(it)!!.encodedPath, it)
        }
        assertEquals("/svc/shreddit/comments/r/u_spez/t3_1vgbkge", RedditLinks.wwwCommentsUrl("/user/spez/comments/1vgbkge/x/")!!.encodedPath)
        assertNull(RedditLinks.wwwCommentsUrl("/r/x/about/"))
    }

    @Test
    fun aPostThatArrivedBareIsReadFromItsEmbedCard() = runBlocking {
        val fake = reddit { page(fixture("embed_self.html")) }
        val post = RedditClient(fake).post(flutterThread)!!

        assertEquals("https://embed.reddit.com/r/FlutterDev/comments/1xa75i8/?embed=true", fake.urls.single().toString())
        assertTrue(post.selfText!!.startsWith("Spent a while"))
        assertEquals(flutterThread, post.permalink, "the page it was asked for")
        assertEquals("birjuvachhani", post.author)
    }

    @Test
    fun aCommentsPageIsFilledInFromItsPostsCard() = runBlocking {
        val fake = reddit { page(fixture("embed_self.html")) }
        RedditClient(fake).post("/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/")

        assertEquals("/r/FlutterDev/comments/1xa75i8/", fake.urls.single().encodedPath)
        assertEquals("/user/spez/comments/1vgbkge/", RedditLinks.wwwEmbedUrl("/user/spez/comments/1vgbkge/x/")!!.encodedPath)
        assertNull(RedditLinks.wwwEmbedUrl("/r/x/about/"))
    }

    @Test
    fun aCardRedditRefusesOrNoAnswerAtAllLeavesThePostAsItWas() = runBlocking {
        assertNull(RedditClient(reddit { page("", 400) }).post(flutterThread))
        assertNull(RedditClient(reddit { throw SocketException("offline") }).post(flutterThread))
    }

    @Test
    fun withAnAccountTheApiNamesThePostAndTheCardStandsInWhenItRefuses() = runBlocking {
        suspend fun fetch(byId: XResponse) = RedditClient(
            reddit {
                when {
                    it.url.encodedPath.contains("access_token") -> page(tokenBody())
                    it.url.host == "oauth.reddit.com" -> byId.also { _ -> assertEquals("/by_id/t3_1xa75i8", it.url.encodedPath) }
                    else -> page(fixture("embed_self.html"))
                }
            },
        ).post(flutterThread, RedditSession(clientId = "app"))

        assertEquals("abc123", fetch(page(listingBody()))!!.id, "the API's own answer")
        assertEquals("birjuvachhani", fetch(page("""{"error":403}""", 403))!!.author, "from the card")
    }

    @Test
    fun aBigThreadsHeldBackCommentsAreAskedForAsTheSiteAsks() = runBlocking {
        val fake = reddit {
            if (it.method == "GET") {
                page(fixture("comments_big.html"), headers = mapOf("set-cookie" to listOf("csrf_token=tok123; Path=/; Secure")))
            } else {
                page(
                    """<shreddit-comment thingid="t1_x" author="eleventh" depth="0">
                      <div slot="comment"><div class="md"><p>Eleventh</p></div></div>
                    </shreddit-comment>
                    <faceplate-partial id="top-level-more-comments-partial" method="post"
                        src="/svc/shreddit/more-comments/AskReddit/t3_1xbzqmk?top-level=1&amp;commentPositionOffset=20">
                      <input type="hidden" name="cursor" value="after-twenty">
                    </faceplate-partial>""",
                )
            }
        }
        val big = "/r/AskReddit/comments/1xbzqmk/whats_your_proudest_i_saw_that_movie_in_theaters/"
        val client = RedditClient(fake)
        val stub = client.thread(big).comments.last()
        assertNotNull(stub.nextPage)

        val more = client.moreComments(stub)
        val post = fake.asked.last()
        assertEquals("POST", post.method)
        assertEquals(
            "https://www.reddit.com/svc/shreddit/more-comments/AskReddit/t3_1xbzqmk?top-level=1&comments-remaining=90&commentPositionOffset=10",
            post.url.toString(),
        )
        assertEquals(stub.nextPage!!.cursor, post.form["cursor"])
        assertEquals("tok123", post.form["csrf_token"])
        assertTrue("csrf_token=tok123" in post.headers["Cookie"]!!)
        assertEquals("https://www.reddit.com", post.headers["Origin"])
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", post.contentType)
        assertEquals("Eleventh", more.first().body)
        assertEquals("after-twenty", more.last().nextPage?.cursor)
        assertEquals(big, more.last().permalink, "the rest is still this thread")
    }

    @Test
    fun heldBackCommentsRedditWillNotHandOverAreAFailure() {
        val stub = RedditComment(
            id = "more",
            body = "",
            permalink = flutterThread,
            moreCount = -1,
            nextPage = RedditCommentsCursor("/svc/shreddit/more-comments/x/t3_y", "c"),
        )

        listOf(page("", 403), page(fixture("browser_check.html"))).forEach { answer ->
            refusal { RedditClient(reddit { answer }).moreComments(stub) }
        }
        assertEquals(404, refusal { RedditClient(reddit { page("") }).moreComments(stub.copy(nextPage = null)) }.code)
    }

    @Test
    fun theBrowserCheckFallsThroughToOldReddit() = runBlocking {
        val fake = reddit { page(if (it.isWww) fixture("browser_check.html") else LISTING_HTML) }
        val listing = RedditClient(fake).listing("dartlang")

        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
        assertEquals("abc123", listing.items.single().id)
    }

    @Test
    fun whenNothingElseServesAThreadTheFailureIsWwwsOwn() {
        val fake = reddit { page(if (it.isWww) fixture("browser_check.html") else LOGIN_PAGE) }
        val error = refusal { RedditClient(fake).thread(flutterThread) }

        assertEquals(403, error.code)
        assertTrue("browser check" in error.message!!, "old.reddit's login page holds no comments")
    }

    @Test
    fun aMissingThreadIsNotAskedOfOldReddit() {
        val fake = reddit { page("", 404) }

        assertEquals(404, refusal { RedditClient(fake).thread(flutterThread) }.code)
        assertEquals(listOf("www.reddit.com"), fake.hosts)
    }

    @Test
    fun aSearchReadsWwwsResultsInItsTerms() = runBlocking {
        val fake = reddit { page(fixture("search_posts.html")) }
        val posts = RedditClient(fake).searchPosts("flutter", sort = RedditSearchSort.NEW)

        val url = fake.urls.single()
        assertEquals("/svc/shreddit/search/", url.encodedPath)
        assertEquals(mapOf("q" to "flutter", "type" to "posts", "sort" to "new", "t" to "all"), url.queryParameterNames.associateWith { url.queryParameter(it) })
        assertEquals(3, posts.items.size)
        assertEquals("1xb5vks", posts.items.first().id)
        assertNull(posts.next)
    }

    @Test
    fun communitiesAndPeopleAreSearchedByTheirOwnTypes() = runBlocking {
        val fake = reddit { page(fixture(if (it.url.queryParameter("type") == "people") "search_people.html" else "search_communities.html")) }
        val client = RedditClient(fake)
        val communities = client.searchSubreddits("flutter")
        val people = client.searchUsers("spez")

        assertEquals(listOf("communities", "people"), fake.urls.map { it.queryParameter("type") })
        assertEquals("FlutterDev", communities.items.first().name)
        assertEquals(941063, people.items.first().karma)
    }

    @Test
    fun theOrderAndTheCommunityScopeRideTheSearchOnEachHost() = runBlocking {
        val json = listingBody(children = """{"kind":"t3","data":{"id":"abc","title":"Flutter","subreddit":"dartlang","permalink":"/r/dartlang/comments/abc/flutter/"}}""")
        val fake = reddit { page(json) }
        val posts = RedditClient(fake).searchPosts("flutter", subreddit = "dartlang", sort = RedditSearchSort.COMMENTS)

        // www.reddit.com's search first; it cannot read the JSON this fake answers with, so the JSON was next.
        val (www, publicJson) = fake.urls
        assertEquals("/svc/shreddit/r/dartlang/search/", www.encodedPath)
        assertEquals("comments", www.queryParameter("sort"))
        assertEquals("posts", www.queryParameter("type"))
        assertEquals("1", www.queryParameter("restrict_sr"))
        assertEquals("/r/dartlang/search.json", publicJson.encodedPath)
        assertEquals("comments", publicJson.queryParameter("sort"))
        assertEquals("on", publicJson.queryParameter("restrict_sr"))
        assertEquals("Flutter", posts.items.single().title)
    }

    @Test
    fun aSearchWithAClientIdAsksTheApi() = runBlocking {
        val json = listingBody(children = """{"kind":"t3","data":{"id":"xyz","title":"Hu Tao","subreddit":"Genshin_Impact","permalink":"/r/Genshin_Impact/comments/xyz/hu_tao/","num_comments":9}}""")
        val fake = apiReddit(json)
        val posts = RedditClient(fake).searchPosts("hu tao", session = app)

        assertEquals("/search", fake.urls.last().encodedPath)
        assertEquals("oauth.reddit.com", fake.urls.last().host)
        assertEquals("Hu Tao", posts.items.single().title)
        assertEquals(9, posts.items.single().commentCount)
    }

    @Test
    fun accountsAreSearchedOnOldRedditsSearchPageWhenNothingElseAnswers() = runBlocking {
        val fake = reddit {
            when {
                it.isWww -> page("", 403)
                it.url.encodedPath.endsWith(".json") -> page("", 403)
                else -> page("""<html><body><div class="search-result search-result-user"><a href="/user/spez">spez</a></div></body></html>""")
            }
        }
        val people = RedditClient(fake).searchUsers("spez")

        assertEquals("/search", fake.urls.last().encodedPath)
        assertEquals("user", fake.urls.last().queryParameter("type"))
        assertEquals(listOf("spez"), people.items.map { it.name })
    }

    @Test
    fun aSearchNothingAnswersReportsWwwsFailure() {
        val error = refusal { RedditClient(reddit { if (it.isWww) page("", 429) else page(LOGIN_PAGE, 200) }).searchPosts("flutter") }

        assertEquals(429, error.code)
        assertEquals("HTTP 429 from www.reddit.com", error.message)
    }

    @Test
    fun aProfileIsReadFromItsPieceAndPagesOn() = runBlocking {
        val fake = reddit { page(fixture("profile_posts.html")) }
        val client = RedditClient(fake)
        val first = client.userPosts("u/spez")
        client.userPosts("spez", after = first.next)

        val asked = fake.urls
        assertEquals("/svc/shreddit/profiles/profile_posts-more-posts/new/", asked.first().encodedPath)
        assertEquals(setOf("name"), asked.first().queryParameterNames)
        assertEquals("spez", asked.first().queryParameter("name"))
        assertEquals(3, first.items.size)
        assertEquals("t3_3cxedn", first.next)
        assertEquals("dDNfM2N4ZWRu", asked.last().queryParameter("after"))
    }

    @Test
    fun aProfileWwwRefusesIsReadOffOldReddit() = runBlocking {
        val fake = reddit(wwwRefused { page(LISTING_HTML) })
        val posts = RedditClient(fake).userPosts("/user/someone", limit = 25)

        assertEquals("/user/someone/submitted", fake.urls.last().encodedPath)
        assertEquals("25", fake.urls.last().queryParameter("limit"))
        assertEquals("abc123", posts.items.single().id)
        assertEquals(404, refusal { RedditClient(fake).userPosts("u/") }.code)
    }

    @Test
    fun whatACommunitySaysAboutItselfComesFromTheCommunitySearch() = runBlocking {
        val fake = reddit { page(fixture("search_communities.html")) }
        val client = RedditClient(fake)
        val about = client.about("flutterdev")
        val icon = client.icon("FlutterDev")

        assertEquals("communities", fake.urls.first().queryParameter("type"))
        assertEquals("FlutterDev", about.name, "as Reddit spells it")
        assertTrue(about.description!!.startsWith("A community for the publishing"))
        assertNull(about.subscribers)
        assertEquals(about.iconUrl, icon)
        assertEquals(1, fake.asked.size, "the icon was noticed on the way")
    }

    private val aboutJson = """{"kind":"t5","data":{"display_name":"dartlang","title":"The Dart Programming Language",""" +
        """"public_description":"All things Dart.","subscribers":104532,"active_user_count":1204,"over18":false,""" +
        """"community_icon":"https://styles.redditmedia.com/t5_2s30g/styles/communityIcon.png","icon_img":""}}"""

    @Test
    fun withAClientIdAboutJsonIsReadFromTheApi() = runBlocking {
        val fake = apiReddit(aboutJson)
        val about = RedditClient(fake).about("dartlang", app)

        assertEquals("/r/dartlang/about.json", fake.urls.last().encodedPath)
        assertEquals("Bearer tok_123", fake.asked.last().headers["Authorization"])
        assertEquals(
            RedditSubredditAbout("dartlang", "The Dart Programming Language", "All things Dart.", 104532, 1204, false, "https://styles.redditmedia.com/t5_2s30g/styles/communityIcon.png"),
            about,
        )
    }

    @Test
    fun withoutAnyCredentialTheSidebarIsTheLastResort() = runBlocking {
        val sidebar = """<!doctype html><html><body><div class="side"><div class="titlebox">
            <h1 class="redditname"><a href="/r/dartlang/">dartlang</a></h1>
            <span class="subscribers"><span class="number">104,532</span> readers</span>
            <form class="usertext"><div class="usertext-body"><div class="md"><p>All things Dart.</p></div></div></form>
            </div></div></body></html>"""
        val fake = reddit { page(sidebar) }
        val about = RedditClient(fake).about("dartlang")

        assertEquals(listOf("www.reddit.com", "www.reddit.com", "old.reddit.com", "old.reddit.com"), fake.hosts)
        assertEquals("/r/dartlang/", fake.urls.last().encodedPath)
        assertEquals(104532, about.subscribers)
        assertEquals("All things Dart.", about.description)
    }

    @Test
    fun theAccountFreeAboutJsonIsReadAndOldRedditIsItsSecondDoor() = runBlocking {
        val icon = "https://styles.redditmedia.com/t5_gf2/styles/communityIcon.png"
        val fake = reddit {
            when {
                it.url.host == "old.reddit.com" && it.url.encodedPath == "/r/girlsfrontline2/about.json" ->
                    page("""{"kind":"t5","data":{"display_name":"girlsfrontline2","community_icon":"$icon?width=64&s=sig"}}""")
                it.url.host == "www.reddit.com" -> page("blocked", 403)
                else -> page("no", 404)
            }
        }

        assertEquals(icon, RedditClient(fake).icon("girlsfrontline2"))
        assertTrue(fake.urls.any { it.host == "www.reddit.com" && it.encodedPath.endsWith("about.json") })
        assertTrue(fake.urls.any { it.host == "old.reddit.com" && it.encodedPath.endsWith("about.json") })
    }

    @Test
    fun anIconWithAClientIdComesFromTheApi() = runBlocking {
        val fake = apiReddit("""{"kind":"t5","data":{"display_name":"novelai","community_icon":"https://styles.redditmedia.com/t5_nai/styles/communityIcon.png"}}""")

        assertEquals("https://styles.redditmedia.com/t5_nai/styles/communityIcon.png", RedditClient(fake).icon("novelai", app))
        assertTrue(fake.urls.any { it.encodedPath == "/r/novelai/about.json" })
    }

    @Test
    fun anIconThatCannotBeFoundIsNullNeverAFailure() = runBlocking {
        assertNull(RedditClient(reddit { page("", 403) }).icon("dartlang"))
        assertNull(RedditClient(reddit { throw SocketException("offline") }).icon("dartlang"))
        assertNull(RedditClient(reddit { page("") }).icon("not a name"))
    }

    private val galleryThread = """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"g1","title":"A gallery","subreddit":"pics",""" +
        """"permalink":"/r/pics/comments/g1/a_gallery/","url":"https://www.reddit.com/gallery/g1",""" +
        """"gallery_data":{"items":[{"media_id":"two"},{"media_id":"one"}]},"media_metadata":{""" +
        """"one":{"s":{"u":"https://preview.redd.it/one.jpg?width=640&amp;s=abc"}},"two":{"s":{"u":"https://preview.redd.it/two.jpg?width=640&amp;s=def"}}}}}]}},""" +
        """{"kind":"Listing","data":{"children":[]}}]"""

    @Test
    fun aScrapedGallerysPicturesAreReadFromThePostsOwnJsonInOrder() = runBlocking {
        val fake = reddit { page(galleryThread) }
        val images = RedditClient(fake).galleryImages("/r/pics/comments/g1/a_gallery/")

        assertEquals(listOf("https://preview.redd.it/two.jpg?width=640&s=def", "https://preview.redd.it/one.jpg?width=640&s=abc"), images)
        assertEquals("/r/pics/comments/g1/a_gallery.json", fake.urls.single().encodedPath, "no account: the public JSON")
    }

    @Test
    fun aGalleryThatIsNotOneOrIsRefusedYieldsNothingRatherThanThrowing() = runBlocking {
        val notGallery = """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"g1","title":"t"}}]}},{"kind":"Listing","data":{"children":[]}}]"""

        assertTrue(RedditClient(reddit { page(notGallery) }).galleryImages("/r/pics/comments/g1/a_gallery/").isEmpty())
        assertTrue(RedditClient(reddit { page("nope", 403) }).galleryImages("/r/pics/comments/g1/a_gallery/").isEmpty())
        assertTrue(RedditClient(reddit { page("<html>blocked</html>") }).galleryImages("/r/pics/comments/g1/a_gallery/").isEmpty())
    }

    @Test
    fun aSubredditNameIsTakenOutOfWhateverWasPasted() {
        listOf("dartlang", "r/dartlang", "/r/dartlang", "/r/dartlang/", "R/dartlang").forEach { assertEquals("dartlang", normaliseSubreddit(it), it) }
        assertEquals("dartlang", normaliseSubreddit("https://www.reddit.com/r/dartlang/"))
        assertEquals("dartlang", normaliseSubreddit("https://old.reddit.com/r/dartlang/comments/abc/x/"))
        listOf("", "   ", "a", "has spaces", "https://reddit.com/u/someone", "r/", "way_too_long_subreddit_name_here")
            .forEach { assertNull(normaliseSubreddit(it), it) }
    }

    @Test
    fun aThreadsJsonPathDropsItsQueryAndLastSlash() {
        assertEquals("/r/x/comments/a/t.json", RedditLinks.commentsJsonPath("/r/x/comments/a/t/?context=3"))
        assertEquals("/r/x/comments/a/t.json", RedditLinks.commentsJsonPath("r/x/comments/a/t.json"))
    }
}
