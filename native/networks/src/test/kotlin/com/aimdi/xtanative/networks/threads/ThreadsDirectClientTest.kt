package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** What the client asks Meta, and how (XTA's threads_direct_test, threads_time_to_content_test, threads_search_test). */
class ThreadsDirectClientTest {
    private val time = FakeTime()
    private val webAgent = ThreadsRepairs().webUserAgent

    private val pageHeaders = mapOf(
        "User-Agent" to webAgent,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private val cookieHeaders = mapOf(
        "User-Agent" to webAgent,
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "X-IG-App-ID" to "238260118697367",
        "X-CSRFToken" to "c",
        "X-ASBD-ID" to "129477",
        "X-IG-WWW-Claim" to "0",
        "Referer" to "https://www.threads.com/",
        "Cookie" to "sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g",
    )

    private val optedIn = ThreadsSession(COOKIES, useSessionApis = true)

    private fun kept(vararg ids: Pair<String, String>, lsd: String? = null) =
        ThreadsMemory.InMemory(ThreadsKept(userIds = mapOf(*ids), guestLsd = lsd, guestLsdAt = lsd?.let { time.now }))

    private fun Asked.line() = "$method $path"

    @Test
    fun aGuestReadsAProfilesThreadsThroughGraphqlWithTheTokenAndIdOfItsPage() = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.line()) {
                "GET /@instagram" -> ok(profilePage(lsd = "tok123", userId = "63404918397"))
                "POST /api/graphql" -> ok(graphql(postJson("99", user = "instagram", text = "guest gql post")))
                else -> XResponse(500, "unexpected ${asked.url}")
            }
        }
        val memory = ThreadsMemory.InMemory()
        val posts = direct(meta, memory = memory, time = time).guestAccount(" Instagram ")
        assertEquals(listOf("guest gql post"), posts.items.map { it.text })
        assertEquals(null, posts.next, "a guest is given no cursor")

        val (page, query) = meta.asked
        assertEquals("https://www.threads.com/@instagram", page.url.toString())
        assertEquals(pageHeaders, page.headers)
        assertEquals("https://www.threads.com/api/graphql", query.url.toString())
        assertEquals("lsd=tok123&doc_id=6232751443445612&variables=%7B%22userID%22%3A%2263404918397%22%7D", query.body)
        assertEquals("application/x-www-form-urlencoded", query.contentType)
        assertEquals(
            mapOf(
                "User-Agent" to webAgent,
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "X-IG-App-ID" to "238260118697367",
                "X-ASBD-ID" to "129477",
                "X-FB-LSD" to "tok123",
                "X-FB-Friendly-Name" to "BarcelonaProfileThreadsTabQuery",
                "Origin" to "https://www.threads.com",
                "Referer" to "https://www.threads.com/@instagram",
            ),
            query.headers,
        )
        assertEquals("63404918397", memory.kept.userIds["instagram"], "the id is remembered")
        assertEquals("tok123", memory.kept.guestLsd)
    }

    @Test
    fun aKnownAccountWithAFreshTokenCostsOneRequestNotTwo() = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.method == "GET") ok(profilePage(userId = "1")) else ok(graphql(postJson("p-${asked.form["variables"]}", user = "a")))
        }
        val client = direct(meta, memory = kept("a" to "1", "b" to "2"), time = time)
        client.guestAccount("a")
        client.guestAccount("b")
        assertEquals(listOf("GET /@a", "POST /api/graphql", "POST /api/graphql"), meta.asked.map { it.line() })
        assertEquals("""{"userID":"2"}""", meta.asked.last().form["variables"], "the token from a's page serves b")
    }

    @Test
    fun aTokenOlderThanTenMinutesIsTakenFromThePageAgain() = runBlocking {
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(profilePage(lsd = "new")) else ok(graphql(postJson("1", user = "a"))) }
        val memory = ThreadsMemory.InMemory(ThreadsKept(userIds = mapOf("a" to "1"), guestLsd = "old", guestLsdAt = time.now - 11 * 60 * 1000))
        direct(meta, memory = memory, time = time).guestAccount("a")
        assertEquals(listOf("GET /@a", "POST /api/graphql"), meta.asked.map { it.line() })
        assertEquals("new", meta.asked.last().headers["X-FB-LSD"])
    }

    @Test
    fun aShortcutThatFailsIsTriedAgainTheFullWay() = runBlocking {
        val meta = FakeMeta { asked ->
            when {
                asked.method == "GET" -> ok(profilePage(lsd = "fresh"))
                asked.headers["X-FB-LSD"] == "stale" -> XResponse(500, "")
                else -> ok(graphql(postJson("1", user = "a", text = "after the page")))
            }
        }
        val posts = direct(meta, memory = kept("a" to "1", lsd = "stale"), time = time).guestAccount("a").items
        assertEquals(listOf("POST /api/graphql", "GET /@a", "POST /api/graphql"), meta.asked.map { it.line() })
        assertEquals(listOf("after the page"), posts.map { it.text })
    }

    @Test
    fun whenGraphqlComesBackEmptyOrAsAPageThePagesOwnThreadsStandIn() = runBlocking {
        val withThreads = profilePage(more = page(listOf(listOf(postJson("7", text = "ssr fallback")))).removePrefix("<html>").removeSuffix("</html>"))
        for (answer in listOf("""{"data":{"mediaData":{"threads":[]}}}""", "<!DOCTYPE html><html></html>", """{"data":"<html>"}""")) {
            val meta = FakeMeta { asked -> ok(if (asked.method == "GET") withThreads else answer) }
            val posts = direct(meta, time = time).guestAccount("zuck").items
            assertEquals(listOf("ssr fallback"), posts.map { it.text }, answer)
        }
    }

    @Test
    fun withNeitherGraphqlNorThePagesThreadsThereIsNoSuchFeed() = runBlocking {
        val meta = FakeMeta { asked -> ok(if (asked.method == "GET") profilePage() else """{"data":{"mediaData":{"threads":[]}}}""") }
        val error = assertFailsWith<ThreadsException> { direct(meta, time = time).guestAccount("zuck") }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, error.failure)
        assertEquals("no posts for @zuck (GraphQL+SSR empty)", error.message)
    }

    @Test
    fun aPageWithoutItsTokenGoesStraightToItsOwnThreads() = runBlocking {
        val meta = FakeMeta { ok(page(listOf(listOf(postJson("7", text = "from the page"))))) }
        assertEquals(listOf("from the page"), direct(meta, time = time).guestAccount("zuck").items.map { it.text })
        assertEquals(listOf("GET /@zuck"), meta.asked.map { it.line() })
    }

    @Test
    fun aProfilesCardAndThreadsAskedTogetherShareOnePageRead() = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.method == "GET") {
                delay(40)
                ok(profilePage(more = """<meta property="og:title" content="Instagram (@instagram)"/>"""))
            } else {
                ok(graphql(postJson("1", user = "instagram")))
            }
        }
        val client = direct(meta, time = time)
        val card = async { client.guestProfile("instagram") }
        val threads = async { client.guestAccount("instagram") }
        assertEquals("Instagram", card.await().fullName)
        assertEquals(1, threads.await().items.size)
        assertEquals(1, meta.asked.count { it.method == "GET" }, "sharing the page cuts Meta traffic; it must not double it")
    }

    @Test
    fun aGuestCardRemembersItsAccountsId() = runBlocking {
        val memory = ThreadsMemory.InMemory()
        val profile = direct(FakeMeta { ok(fixture("profile_page.html")) }, memory = memory, time = time).guestProfile("Zuck")
        assertEquals("Mark Zuckerberg", profile.fullName)
        assertEquals(mapOf("zuck" to "314216"), memory.kept.userIds)
    }

    @Test
    fun aPageWithoutACardIsNoSuchFeed() = runBlocking {
        val error = assertFailsWith<ThreadsException> { direct(FakeMeta { ok("<html></html>") }, time = time).guestProfile("zuck") }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, error.failure)
    }

    @Test
    fun aPostPageIsReadOnceAndSharedByItsShortCodeForTenMinutes() = runBlocking {
        val meta = FakeMeta { ok(fixture("post_page.html")) }
        val client = direct(meta, time = time)
        assertEquals(3, client.postChains("https://threads.net/@zuck/post/DPzuck0002?igshid=abc").size)
        client.postChains("https://www.threads.com/@zuck/post/DPzuck0002")
        assertEquals(listOf("https://www.threads.com/@zuck/post/DPzuck0002"), meta.asked.map { it.url.toString() }, "no query, one read")
        assertEquals(pageHeaders, meta.asked.single().headers)

        time.now += 10 * 60 * 1000
        client.postChains("https://www.threads.com/@zuck/post/DPzuck0002")
        assertEquals(2, meta.asked.size, "ten minutes on, Meta is asked again")
        client.postChains("https://www.threads.com/@zuck/post/DPzuck0002", force = true)
        assertEquals(3, meta.asked.size, "pulling to refresh asks again")
        assertEquals(listOf("501", "502", "503", "504", "505", "506", "507"), client.postThread("https://www.threads.com/@zuck/post/DPzuck0002").map { it.id })
    }

    @Test
    fun anEmptyPostPageIsNotRememberedAndOnlyFortyAreKept() = runBlocking {
        val meta = FakeMeta { asked -> ok(if (asked.path.endsWith("/empty")) "<html></html>" else page(listOf(listOf(postJson(asked.path.substringAfterLast('/')))))) }
        val client = direct(meta, time = time)
        repeat(2) { client.postChains("https://www.threads.com/@zuck/post/empty") }
        assertEquals(2, meta.asked.size)

        (0..40).forEach { client.postChains("https://www.threads.com/@zuck/post/P$it") }
        client.postChains("https://www.threads.com/@zuck/post/P40")
        assertEquals(43, meta.asked.size, "the newest is kept")
        client.postChains("https://www.threads.com/@zuck/post/P0")
        assertEquals(44, meta.asked.size, "the oldest of forty-one was let go")
    }

    @Test
    fun aLinkThatIsNotThreadsIsRefusedWithoutARequest() = runBlocking {
        val meta = FakeMeta { ok("") }
        val error = assertFailsWith<ThreadsException> { direct(meta, time = time).postChains("https://example.com/@zuck/post/C1") }
        assertEquals(ThreadsFailure.UNREACHABLE, error.failure)
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun theRepliesTabReadsTheProfilesRepliesPage() = runBlocking {
        val meta = FakeMeta { ok(fixture("replies_page.html")) }
        val replies = direct(meta, time = time).guestReplies("Zuck").items
        assertEquals("https://www.threads.com/@zuck/replies", meta.asked.single().url.toString())
        assertEquals(listOf("702", "703"), replies.map { it.id })
    }

    @Test
    fun aGuestSearchReadsThePublicSearchPageAsALoggedOutBrowser() = runBlocking {
        val meta = FakeMeta { ok(fixture("search_page.html")) }
        val client = direct(meta, time = time)
        assertEquals(listOf("602", "603", "604"), client.searchPosts("kiln", recent = true).items.map { it.id })
        assertEquals("https://www.threads.com/search?q=kiln&serp_type=default&filter=recent", meta.asked.single().url.toString())
        assertEquals(pageHeaders, meta.asked.single().headers)
        assertEquals("Ceramics", client.searchPosts("Ceramics", tag = true).items.last().topicTag)
        assertEquals("q=Ceramics&serp_type=tags", meta.asked.last().url.encodedQuery)
    }

    @Test
    fun theBearerReadsItsHomeTimelineFromTheAppApi() = runBlocking {
        val meta = FakeMeta { ok(fixture("app_timeline.json")) }
        val memory = ThreadsMemory.InMemory(ThreadsKept(deviceId = "device-1"))
        val posts = direct(meta, ThreadsSession(bearer = "Bearer IGT:2:secret"), memory, time).followingTimeline(limit = 1).items
        val asked = meta.asked.single()
        assertEquals(
            "https://i.instagram.com/api/v1/feed/text_post_app_timeline/?feed_type=for_you&feed_view_info=%5B%5D&reason=cold_start_fetch" +
                "&client_session_id=device-1&pagination_source_module=feed_unit",
            asked.url.toString(),
        )
        assertEquals(
            mapOf(
                "User-Agent" to "Barcelona 289.0.0.77.109 Android",
                "Authorization" to "Bearer IGT:2:secret",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "X-IG-App-ID" to "238260118697367",
                "X-IG-Capabilities" to "3brTvx0=",
                "X-IG-Connection-Type" to "WIFI",
                "X-IG-Device-ID" to "device-1",
            ),
            asked.headers,
        )
        assertEquals(listOf("For you, first"), posts.map { it.text })
    }

    @Test
    fun aDeviceIdIsMadeOnceAndKept() = runBlocking {
        val meta = FakeMeta { ok(fixture("app_timeline.json")) }
        val memory = ThreadsMemory.InMemory()
        val client = direct(meta, ThreadsSession(bearer = "IGT:2:secret"), memory, time)
        client.followingTimeline()
        client.followingTimeline()
        val ids = meta.asked.map { it.headers["X-IG-Device-ID"] }.distinct()
        assertEquals(listOf(memory.kept.deviceId), ids)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(ids.single()!!))
    }

    @Test
    fun withoutABearerThereIsNoHomeTimeline() = runBlocking {
        val meta = FakeMeta { ok("{}") }
        val error = assertFailsWith<ThreadsException> { direct(meta, ThreadsSession(bearer = "not-a-token"), time = time).followingTimeline() }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, error.failure)
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun pastedCookiesAreNotSpentOnAnAccountUnlessTheReaderOptsIn() = runBlocking {
        val meta = FakeMeta { asked -> if (asked.path.contains("/text_feed/")) ok("""{"threads":[]}""") else ok(graphql(postJson("1", user = "instagram", text = "guest"))) }
        val posts = direct(meta, ThreadsSession(COOKIES), kept("instagram" to "63404918397", lsd = "tok"), time).userThreads("instagram").items
        assertEquals(listOf("POST /api/graphql"), meta.asked.map { it.line() })
        assertEquals(listOf("guest"), posts.map { it.text })
    }

    @Test
    fun withSessionReadsTheCookieApiReadsTheAccountAsABrowserWould() = runBlocking {
        val meta = FakeMeta { ok(fixture("text_feed.json")) }
        val posts = direct(meta, optedIn, kept("instagram" to "63404918397"), time).userThreads("instagram").items
        val asked = meta.asked.single()
        assertEquals("https://www.threads.com/api/v1/text_feed/63404918397/profile/?count=20", asked.url.toString())
        assertEquals(cookieHeaders, asked.headers, "a browser's agent, not the official app's")
        assertEquals(listOf("From the cookie API"), posts.map { it.text })
        assertEquals(77L, posts.single().likeCount)
    }

    @Test
    fun anAccountsIdIsSearchedForOnceAndRemembered() = runBlocking {
        var searches = 0
        val meta = FakeMeta { asked ->
            if (asked.path.contains("/search/")) {
                searches++
                assertEquals("https://www.threads.com/api/v1/users/search/?q=someone&count=10", asked.url.toString())
                ok("""{"users":[{"username":"Someone","pk":"42"}]}""")
            } else {
                assertTrue(asked.path.contains("/42/"), "the remembered id is the one used")
                ok(fixture("text_feed.json"))
            }
        }
        val memory = ThreadsMemory.InMemory()
        val client = direct(meta, optedIn, memory, time)
        client.userThreads("someone")
        client.userThreads("someone")
        assertEquals(1, searches, "repeating an identical search is the signature of a script")
        assertEquals("42", memory.kept.userIds["someone"])

        searches = 0
        direct(meta, optedIn, memory, time).userThreads("someone")
        assertEquals(0, searches, "the id outlives the client, as it outlives a restart")
    }

    @Test
    fun whenTheCookieApiRefusesTheGuestReadStandsIn() = runBlocking {
        val meta = FakeMeta { asked ->
            when {
                asked.path.contains("/text_feed/") -> XResponse(403, """{"message":"login_required","logout_reason":8}""")
                asked.method == "GET" -> ok(profilePage())
                else -> ok(graphql(postJson("5", user = "instagram", text = "via guest after cookie fail")))
            }
        }
        val posts = direct(meta, optedIn, kept("instagram" to "63404918397"), time).userThreads("instagram").items
        assertEquals(listOf("via guest after cookie fail"), posts.map { it.text })
    }

    @Test
    fun peopleSearchStripsOneAtAndRemembersEachAccountsId() = runBlocking {
        val meta = FakeMeta { ok(fixture("users_search.json")) }
        val memory = ThreadsMemory.InMemory()
        val people = direct(meta, ThreadsSession(COOKIES), memory, time).searchUsers(" @instagram ").items
        assertEquals("q=instagram&count=10", meta.asked.single().url.encodedQuery)
        assertEquals(listOf("instagram", "Instagram.Fan"), people.map { it.username })
        assertEquals(mapOf("instagram" to "63404918397", "instagram.fan" to "7000000001"), memory.kept.userIds)
    }

    @Test
    fun peopleSearchNeedsTheCookiesBeforeAnythingElse() = runBlocking {
        val meta = FakeMeta { ok("{}") }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { direct(meta, time = time).searchUsers("") }.failure)
        assertTrue(direct(meta, ThreadsSession(COOKIES), time = time).searchUsers(" @ ").items.isEmpty())
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun theCookiesSignInAsTheirAccount() = runBlocking {
        val meta = FakeMeta { ok("""{"user":{"pk":2000000001,"username":"reader.me","full_name":"A Reader"},"status":"ok"}""") }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        assertEquals("2000000001", client.currentUser().pk)
        assertEquals("reader.me", client.verify())
        assertEquals("https://www.threads.com/api/v1/accounts/current_user/?edit=true", meta.asked.first().url.toString())
        val missing = assertFailsWith<ThreadsException> { direct(FakeMeta { ok("{}") }, ThreadsSession(COOKIES), time = time).currentUser() }
        assertEquals(ThreadsFailure.UNREACHABLE, missing.failure)
    }

    @Test
    fun verifyingABearerAloneReadsOnePost() = runBlocking {
        assertEquals("meta", direct(FakeMeta { ok(fixture("app_timeline.json")) }, ThreadsSession(bearer = "IGT:2:x"), time = time).verify())
        assertEquals("ok", direct(FakeMeta { ok("""{"items":[]}""") }, ThreadsSession(bearer = "IGT:2:x"), time = time).verify())
        val none = assertFailsWith<ThreadsException> { direct(FakeMeta { ok("{}") }, time = time).verify() }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, none.failure)
    }

    @Test
    fun aCardThePublicPageCannotGiveIsAskedOfTheCookieApiOnlyWhenOptedIn() = runBlocking {
        val meta = FakeMeta { asked -> if (asked.path == "/@instagram") XResponse(404, "") else ok(fixture("user_info.json")) }
        val profile = direct(meta, optedIn, kept("instagram" to "63404918397"), time).profile("instagram")
        assertEquals("Discover what's new on Instagram", profile.biography)
        assertEquals(listOf("GET /@instagram", "GET /api/v1/users/63404918397/info/"), meta.asked.map { it.line() })

        val guestOnly = assertFailsWith<ThreadsException> { direct(meta, ThreadsSession(COOKIES), time = time).profile("instagram") }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, guestOnly.failure)
    }

    @Test
    fun publishedRepairsAreWhatIsSentAndOnlyWhenTheyFit() = runBlocking {
        val meta = FakeMeta { asked -> ok(if (asked.method == "GET") profilePage() else graphql(postJson("1", user = "zuck"))) }
        val repairs = ThreadsRepairs.published(mapOf("threads.guest_doc_id" to "1111111111111111", "threads.app_id" to "1\r\nCookie: x"))
        direct(meta, time = time, repairs = repairs).guestAccount("zuck")
        assertEquals("1111111111111111", meta.asked.last().form["doc_id"])
        assertEquals("238260118697367", meta.asked.last().headers["X-IG-App-ID"])
    }
}
