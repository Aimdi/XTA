package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class InstagramClientTest {
    /** Stands in for instagram.com: answers each GET with [answer] and keeps what was asked. */
    private class FakeInstagram(private val answer: (HttpUrl) -> XResponse) : XHttp {
        private val asked = mutableListOf<Pair<HttpUrl, Map<String, String>>>()
        val requests: List<Pair<HttpUrl, Map<String, String>>> get() = synchronized(asked) { asked.toList() }
        val urls: List<HttpUrl> get() = requests.map { it.first }

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            synchronized(asked) { asked += url to headers }
            return answer(url)
        }
    }

    private val session = "sessionid=s1; csrftoken=c1; ds_user_id=9; mid=m1; ig_did=d1"
    private val home = XResponse(200, "<html></html>", mapOf("set-cookie" to listOf("csrftoken=abc; Path=/, mid=mid1; Path=/")))
    private val missing = XResponse(404, "no")

    private fun ok(body: String) = XResponse(200, body)

    private fun fixture(name: String) = javaClass.getResource("/instagram/$name")!!.readText()

    /** web_profile_info for [username], with one post (none when [locked]) and more to page to. */
    private fun profileJson(username: String = "instagram", locked: Boolean = false): String {
        val post = """{"node":{"id":"1$username","shortcode":"ABC$username","taken_at_timestamp":1700000000,""" +
            """"display_url":"https://scontent.cdninstagram.com/$username.jpg","owner":{"username":"$username"}}}"""
        return """{"data":{"user":{"id":"25025320","username":"$username","full_name":"Instagram","is_private":$locked,""" +
            """"edge_owner_to_timeline_media":{"count":${if (locked) 0 else 1},""" +
            """"page_info":{"has_next_page":${!locked},"end_cursor":"QWE"},"edges":[${if (locked) "" else post}]}}},"status":"ok"}"""
    }

    private fun isWebProfile(url: HttpUrl) = url.encodedPath == "/api/v1/users/web_profile_info/"

    /** What a guest finds: the homepage's cookies, and each account's web_profile_info. */
    private fun guestSite(url: HttpUrl): XResponse = when {
        url.encodedPath == "/" -> home
        isWebProfile(url) -> ok(profileJson(url.queryParameter("username")!!))
        else -> missing
    }

    private fun refusal(read: suspend () -> Unit): NetworkRequestException =
        assertFailsWith<NetworkRequestException> { runBlocking { read() } }

    @Test
    fun aProfileWarmsTheHomepageThenAsksTheApiHost() = runBlocking<Unit> {
        val instagram = FakeInstagram { guestSite(it) }
        assertEquals("instagram", InstagramClient(instagram).profile("@Instagram").username)

        val (warm, api) = instagram.requests
        assertEquals("https://www.instagram.com/", warm.first.toString())
        assertTrue(warm.second["Accept"]!!.startsWith("text/html"))
        assertEquals("https://i.instagram.com/api/v1/users/web_profile_info/?username=instagram", api.first.toString())
        val headers = api.second
        assertEquals("936619743392459", headers["X-IG-App-ID"])
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertEquals("https://www.instagram.com", headers["Origin"])
        assertEquals("https://www.instagram.com/instagram/", headers["Referer"])
        assertEquals("application/json, text/plain, */*", headers["Accept"])
        assertEquals("en-US,en;q=0.9", headers["Accept-Language"])
        assertEquals("csrftoken=abc; mid=mid1", headers["Cookie"])
        assertEquals("abc", headers["X-CSRFToken"])
        assertTrue(headers["User-Agent"]!!.contains("Chrome/"))
    }

    @Test
    fun webProfileInfoFallsBackToWwwWhenTheApiHostLimitsOrWantsALogin() = runBlocking<Unit> {
        for (code in listOf(429, 401, 403)) {
            val instagram = FakeInstagram { url ->
                when {
                    url.encodedPath == "/" -> home
                    url.host == "i.instagram.com" -> XResponse(code, "")
                    isWebProfile(url) -> ok(profileJson())
                    else -> missing
                }
            }
            assertEquals("25025320", InstagramClient(instagram).profile("instagram").id)
            assertEquals(listOf("i.instagram.com", "www.instagram.com"), instagram.urls.filter { isWebProfile(it) }.map { it.host }, "$code")
        }
    }

    @Test
    fun aServerErrorIsSurfacedWithoutAskingWww() {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/") home else XResponse(500, "") }
        assertEquals(500, refusal { InstagramClient(instagram).profile("instagram") }.code)
        assertEquals(listOf("i.instagram.com"), instagram.urls.filter { isWebProfile(it) }.map { it.host })
    }

    @Test
    fun theHomepagesCookiesAreKeptAndHeard() = runBlocking<Unit> {
        val heard = mutableListOf<String>()
        val client = InstagramClient(FakeInstagram { guestSite(it) }, onCookies = { heard += it })
        client.warmGuest()
        assertEquals("csrftoken=abc; mid=mid1", client.cookieHeader)
        assertEquals("csrftoken=abc; mid=mid1", heard.last())
        assertFalse(client.hasSession, "a guest's cookies are not a session")
    }

    @Test
    fun keptGuestCookiesSpareTheHomepage() = runBlocking<Unit> {
        val instagram = FakeInstagram { guestSite(it) }
        InstagramClient(instagram, cookies = "csrftoken=c; mid=m").profile("instagram")
        assertEquals(listOf("/api/v1/users/web_profile_info/"), instagram.urls.map { it.encodedPath })
    }

    @Test
    fun theLoginPageInPlaceOfJsonIsLoginRequired() {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/") home else ok("<html><body>login</body></html>") }
        val refused = refusal { InstagramClient(instagram).profile("instagram") }
        assertEquals(InstagramClient.LOGIN_REQUIRED, refused.code)
        assertEquals("instagram", refused.network)
        assertEquals(2, instagram.urls.count { isWebProfile(it) }, "both hosts were asked")
    }

    @Test
    fun loginRequiredJsonIsLoginRequired() {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/") home else ok(fixture("login_required.json")) }
        assertEquals(InstagramClient.LOGIN_REQUIRED, refusal { InstagramClient(instagram).searchUsers("nasa") }.code)
    }

    @Test
    fun anAnswerThatIsNotJsonIsUnreadable() {
        val instagram = FakeInstagram { ok("oops") }
        assertEquals(InstagramClient.UNREADABLE, refusal { InstagramClient(instagram).userFeed("1") }.code)
    }

    @Test
    fun aPrivateProfileIsReturnedForItsLock() = runBlocking<Unit> {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/") home else ok(profileJson("secret", locked = true)) }
        val profile = InstagramClient(instagram).profile("secret")
        assertEquals("secret", profile.username)
        assertTrue(profile.isPrivate)
    }

    @Test
    fun anAnswerWithoutAnAccountIsNotFound() {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/") home else ok("""{"data":{"user":null},"status":"ok"}""") }
        assertEquals(InstagramClient.NOT_FOUND, refusal { InstagramClient(instagram).profile("nobody") }.code)
        assertEquals(1, instagram.urls.count { isWebProfile(it) }, "an answer naming nobody is not asked again on www")
    }

    @Test
    fun aNameThatCannotBeAUsernameIsNotFoundBeforeAnyRequest() {
        val instagram = FakeInstagram { missing }
        assertEquals(404, refusal { InstagramClient(instagram).profile("https://www.instagram.com/p/ABC/") }.code)
        assertTrue(instagram.urls.isEmpty())
    }

    @Test
    fun theGuestMixInterleavesThePublicAccountsAfterOneWarmUp() = runBlocking<Unit> {
        val instagram = FakeInstagram { guestSite(it) }
        val page = InstagramClient(instagram).guestDiscover()
        assertEquals(listOf("instagram", "natgeo", "nasa"), page.items.map { it.author.username })
        assertNull(page.next)
        assertEquals(1, instagram.urls.count { it.encodedPath == "/" })
    }

    @Test
    fun theGuestMixShowsTheAccountsThatAnswered() = runBlocking<Unit> {
        val instagram = FakeInstagram { url -> if (url.queryParameter("username") == "natgeo") XResponse(500, "") else guestSite(url) }
        assertEquals(listOf("instagram", "nasa"), InstagramClient(instagram).guestDiscover().items.map { it.author.username })
    }

    @Test
    fun theGuestMixFailsWithTheRateLimitWhenEveryAccountFails() {
        val instagram = FakeInstagram { url ->
            when (url.queryParameter("username")) {
                null -> home
                "instagram" -> XResponse(500, "")
                else -> XResponse(429, "")
            }
        }
        assertEquals(InstagramClient.RATE_LIMITED, refusal { InstagramClient(instagram).guestDiscover() }.code)
    }

    @Test
    fun aSessionIsAllFiveCookies() {
        val heard = mutableListOf<String>()
        val client = InstagramClient(FakeInstagram { missing }, onCookies = { heard += it })
        assertFalse(client.hasSession)
        client.setCookies(session)
        assertTrue(client.hasSession)
        assertEquals(session, client.cookieHeader)
        assertEquals(session, heard.last())
        client.setCookies(" sessionid=s1 ; csrftoken=c1; broken; =x; ds_user_id=")
        assertEquals("sessionid=s1; csrftoken=c1", client.cookieHeader)
        assertFalse(client.hasSession, "part of a session is not one")
        client.clearSession()
        assertEquals("", client.cookieHeader)
        assertEquals("", heard.last())
        assertTrue(InstagramClient.hasSession(session), "a kept header reads as the client holding it would")
        assertFalse(InstagramClient.hasSession("csrftoken=c1; mid=m1"))
    }

    @Test
    fun forYouIsExploreWhenTheSessionReadsIt() = runBlocking<Unit> {
        val instagram = FakeInstagram { url ->
            if (url.encodedPath == "/api/v1/discover/web/explore_grid/") ok(fixture("explore_grid.json")) else missing
        }
        val page = InstagramClient(instagram, cookies = session).forYou()
        assertEquals(listOf("AAA", "BBB", "CCC"), page.items.map { it.shortcode })
        assertEquals("cur1", page.next)

        val (url, headers) = instagram.requests.single()
        assertEquals("www.instagram.com", url.host)
        assertEquals("false", url.queryParameter("is_prefetch"))
        assertEquals("false", url.queryParameter("omit_cover_media"))
        assertEquals("explore_popular", url.queryParameter("module"))
        assertNull(url.queryParameter("max_id"))
        assertEquals("https://www.instagram.com/explore/", headers["Referer"])
        assertEquals("c1", headers["X-CSRFToken"])
        assertEquals(session, headers["Cookie"])
    }

    @Test
    fun forYouSkipsExploreWithoutASession() = runBlocking<Unit> {
        val instagram = FakeInstagram { url -> if ("explore" in url.encodedPath) XResponse(500, "not for a guest") else guestSite(url) }
        val page = InstagramClient(instagram).forYou()
        assertEquals(listOf("instagram", "natgeo", "nasa"), page.items.map { it.author.username })
        assertTrue(instagram.urls.none { "explore" in it.encodedPath })
    }

    @Test
    fun forYouFallsBackToThePublicAccountsWhenExploreWantsALogin() = runBlocking<Unit> {
        val instagram = FakeInstagram { url -> if ("explore" in url.encodedPath) ok(fixture("login_required.json")) else guestSite(url) }
        val page = InstagramClient(instagram, cookies = session).forYou()
        assertEquals(listOf("instagram", "natgeo", "nasa"), page.items.map { it.author.username })
        assertEquals(
            listOf("/api/v1/discover/web/explore_grid/", "/api/v1/discover/topical_explore/"),
            instagram.urls.map { it.encodedPath }.filter { "explore" in it },
        )
    }

    @Test
    fun aLaterForYouPageWithoutExploreIsTheEnd() = runBlocking<Unit> {
        val instagram = FakeInstagram { guestSite(it) }
        val page = InstagramClient(instagram).forYou(cursor = "next")
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
        assertTrue(instagram.urls.isEmpty())
    }

    @Test
    fun exploreFallsBackToTopicalExploreWhenTheGridIsRefused() = runBlocking<Unit> {
        val instagram = FakeInstagram { url ->
            when (url.encodedPath) {
                "/api/v1/discover/web/explore_grid/" -> XResponse(429, "")
                "/api/v1/discover/topical_explore/" -> ok(fixture("explore_grid.json"))
                else -> missing
            }
        }
        assertEquals(3, InstagramClient(instagram, cookies = session).exploreFeed(cursor = "cur1").items.size)
        val topical = instagram.urls.last()
        assertEquals("i.instagram.com", topical.host)
        assertEquals("cur1", topical.queryParameter("max_id"))
    }

    @Test
    fun aMissingExploreIsSurfacedNotMixed() {
        val instagram = FakeInstagram { missing }
        assertEquals(404, refusal { InstagramClient(instagram, cookies = session).forYou() }.code)
        assertEquals(1, instagram.urls.size)
    }

    @Test
    fun theFeedPagesByMaxId() = runBlocking<Unit> {
        val instagram = FakeInstagram { ok(fixture("feed_user.json")) }
        val page = InstagramClient(instagram, cookies = session).userFeed("25025320", cursor = "abc")
        assertEquals(2, page.items.size)
        assertEquals("3141592653589793005_25025320", page.next)

        val (url, headers) = instagram.requests.single()
        assertEquals("https://i.instagram.com/api/v1/feed/user/25025320/?count=12&max_id=abc", url.toString())
        assertEquals("https://www.instagram.com/", headers["Referer"])
    }

    @Test
    fun aGuestGetsTheFirstPageOfAProfileAlone() = runBlocking<Unit> {
        val instagram = FakeInstagram { guestSite(it) }
        val client = InstagramClient(instagram)
        val first = client.profilePosts("instagram")
        assertEquals(listOf("ABCinstagram"), first.items.map { it.shortcode })
        assertNull(first.next, "Instagram has more, but a guest cannot page")
        assertEquals("QWE", client.profileMedia("instagram").next, "the grid itself says there is more")

        val asked = instagram.urls.size
        assertTrue(client.profilePosts("instagram", cursor = "QWE").items.isEmpty())
        assertEquals(asked, instagram.urls.size, "nothing more was asked")
    }

    @Test
    fun aSessionPagesAProfileOnThroughTheFeed() = runBlocking<Unit> {
        val instagram = FakeInstagram { url ->
            when {
                isWebProfile(url) -> ok(profileJson())
                url.encodedPath.startsWith("/api/v1/feed/user/") -> ok(fixture("feed_user.json"))
                else -> missing
            }
        }
        val client = InstagramClient(instagram, cookies = session)
        val first = client.profilePosts("instagram")
        assertEquals("QWE", first.next)

        val second = client.profilePosts("instagram", cursor = first.next, pk = "25025320")
        assertEquals(listOf("C4Reel", "C5Album"), second.items.map { it.shortcode })
        assertEquals("/api/v1/feed/user/25025320/", instagram.urls.last().encodedPath)
        assertEquals("QWE", instagram.urls.last().queryParameter("max_id"))

        client.profilePosts("instagram", cursor = "QWE")
        assertTrue(isWebProfile(instagram.urls.takeLast(2).first()), "without the pk the profile is read for it")
    }

    @Test
    fun peopleSearchAsksTopsearchForAccounts() = runBlocking<Unit> {
        val instagram = FakeInstagram { url -> if (url.encodedPath == "/api/v1/web/search/topsearch/") ok(fixture("topsearch.json")) else missing }
        val users = InstagramClient(instagram, cookies = session).searchUsers("  nat geo ")
        assertEquals(listOf("natgeo", "natgeotravel"), users.map { it.username })

        val (url, headers) = instagram.requests.single()
        assertEquals("www.instagram.com", url.host)
        assertEquals("nat geo", url.queryParameter("query"))
        assertEquals("user", url.queryParameter("context"))
        assertEquals("https://www.instagram.com/", headers["Referer"])
    }

    @Test
    fun anEmptySearchAsksNothing() = runBlocking<Unit> {
        val instagram = FakeInstagram { missing }
        assertTrue(InstagramClient(instagram).searchUsers("   ").isEmpty())
        assertTrue(instagram.urls.isEmpty())
    }

    @Test
    fun theAppIdAndUserAgentInForceAreSent() = runBlocking<Unit> {
        var inForce = InstagramRepairs(appId = "111111111111111", userAgent = "Repaired/1.0")
        val instagram = FakeInstagram { guestSite(it) }
        val client = InstagramClient(instagram, repairs = { inForce })
        client.profile("instagram")
        val api = instagram.requests.first { it.first.host == "i.instagram.com" }.second
        assertEquals("111111111111111", api["X-IG-App-ID"])
        assertEquals("Repaired/1.0", api["User-Agent"])

        inForce = InstagramRepairs()
        client.profile("instagram")
        assertEquals("936619743392459", instagram.requests.last().second["X-IG-App-ID"], "a withdrawn repair is the shipped id again")
    }
}
