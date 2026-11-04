package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class TikTokClientTest {
    /** Stands in for tiktok.com: answers each GET with [answer] and keeps what was asked. */
    private class FakeTikTok(private val answer: (HttpUrl) -> XResponse) : XHttp {
        private val asked = mutableListOf<Pair<HttpUrl, Map<String, String>>>()
        val requests: List<Pair<HttpUrl, Map<String, String>>> get() = synchronized(asked) { asked.toList() }
        val urls: List<HttpUrl> get() = requests.map { it.first }

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            synchronized(asked) { asked += url to headers }
            return answer(url)
        }
    }

    private val device = "7300000000000000000"
    private val secUid = "MS4wLjABAAAAv7iSuuXDJGDvJkmH_vz1qkDZYo1apxgzaxdBSeIuPiM"
    private val missing = XResponse(404, "missing")

    private fun ok(body: String) = XResponse(200, body)

    private fun fixture(name: String) = javaClass.getResource("/tiktok/$name")!!.readText()

    private fun client(tiktok: XHttp, session: TikTokSession = TikTokSession(deviceId = device), onSession: (TikTokSession) -> Unit = {}) =
        TikTokClient(tiktok, session = session, onSession = onSession)

    /** A profile page for [handle] as TikTok's app JSON. */
    private fun profileHtml(handle: String, nickname: String = "Name") =
        """<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">{"__DEFAULT_SCOPE__":{"webapp.user-detail":{"userInfo":""" +
            """{"user":{"id":"1","secUid":"MS4wLjABAAAA_$handle","uniqueId":"$handle","nickname":"$nickname",""" +
            """"avatarThumb":"https://p16.tiktokcdn.com/a.jpg","privateAccount":false,"verified":true},"stats":{"followerCount":10}}}}}</script></html>"""

    private fun refusal(read: suspend () -> Unit): NetworkRequestException =
        assertFailsWith<NetworkRequestException> { runBlocking { read() } }

    @Test
    fun aProfileIsReadFromItsPage() = runBlocking<Unit> {
        val tiktok = FakeTikTok { url -> if (url.encodedPath == "/@tiktok") ok(fixture("profile.html")) else missing }
        val profile = client(tiktok).profile("https://www.tiktok.com/@TikTok")
        assertEquals("tiktok", profile.uniqueId)
        assertEquals(secUid, profile.secUid)

        val (url, headers) = tiktok.requests.single()
        assertEquals("https://www.tiktok.com/@tiktok", url.toString())
        assertTrue(headers["Accept"]!!.startsWith("text/html"))
        assertEquals("en-US,en;q=0.9", headers["Accept-Language"])
        assertTrue(headers["User-Agent"]!!.contains("Chrome/"))
        assertNull(headers["Referer"])
        assertNull(headers["Cookie"], "a new guest has no cookies")
    }

    @Test
    fun aPageWithoutAnAccountIsNotFound() {
        val tiktok = FakeTikTok { ok("<html><body>Couldn't find this account</body></html>") }
        val refused = refusal { client(tiktok).profile("nobody_here") }
        assertEquals(TikTokClient.NOT_FOUND, refused.code)
        assertEquals("tiktok", refused.network)
        assertEquals(404, refusal { client(tiktok).profile("https://vm.tiktok.com/ZMabc/") }.code)
        assertEquals(1, tiktok.urls.size, "a short link is not asked for")
    }

    @Test
    fun aRateLimitIsSaidAsTikTokSentIt() {
        assertEquals(TikTokClient.RATE_LIMITED, refusal { client(FakeTikTok { XResponse(429, "") }).profile("tiktok") }.code)
    }

    @Test
    fun theCreatorListIsAskedAsAWebBrowserWould() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("item_list.json")) }
        val page = client(tiktok).creatorItems(secUid)
        assertEquals(listOf("7300000000000000002", "7300000000000000003"), page.items.map { it.id })
        assertEquals("1699980000000", page.next)

        val (url, headers) = tiktok.requests.single()
        assertEquals("/api/creator/item_list/", url.encodedPath)
        val expected = mapOf(
            "aid" to "1988", "app_language" to "en", "app_name" to "tiktok_web", "browser_language" to "en-US",
            "browser_name" to "Mozilla", "browser_online" to "true", "browser_platform" to "Win32",
            "browser_version" to "5.0 (Windows)", "channel" to "tiktok_web", "cookie_enabled" to "true",
            "device_id" to device, "device_platform" to "web_pc", "focus_state" to "true", "from_page" to "user",
            "history_len" to "2", "is_fullscreen" to "false", "is_page_visible" to "true", "language" to "en",
            "os" to "windows", "priority_region" to "", "referer" to "", "region" to "US", "screen_height" to "1080",
            "screen_width" to "1920", "tz_name" to "UTC", "webcast_language" to "en",
            "count" to "15", "cursor" to "0", "secUid" to secUid, "type" to "1",
        )
        assertEquals(expected.keys.toList(), url.queryParameterNames.toList(), "the names, in a browser's order")
        expected.forEach { (name, value) -> assertEquals(value, url.queryParameter(name), name) }
        assertEquals("https://www.tiktok.com/", headers["Referer"])
        assertEquals("application/json, text/plain, */*", headers["Accept"])
    }

    @Test
    fun theNextPageIsAskedFromItsCursor() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("item_list.json")) }
        client(tiktok).creatorItems(secUid, cursor = "1699980000000", count = 8)
        assertEquals("1699980000000", tiktok.urls.single().queryParameter("cursor"))
        assertEquals("8", tiktok.urls.single().queryParameter("count"))
    }

    @Test
    fun theMsTokenCookieRidesInTheQuery() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("item_list.json")) }
        client(tiktok, TikTokSession(cookies = "ttwid=t1; msToken=m1", deviceId = device)).creatorItems(secUid)
        val (url, headers) = tiktok.requests.single()
        assertEquals("m1", url.queryParameter("msToken"))
        assertEquals("ttwid=t1; msToken=m1", headers["Cookie"])
    }

    @Test
    fun anEmptyListThatHasMoreIsAskedAgainWithANewDeviceId() = runBlocking<Unit> {
        val heard = mutableListOf<TikTokSession>()
        var calls = 0
        val tiktok = FakeTikTok {
            calls++
            if (calls == 1) ok("""{"statusCode":0,"hasMore":true,"itemList":[]}""") else ok(fixture("item_list.json"))
        }
        val page = client(tiktok, onSession = { heard += it }).creatorItems(secUid)
        assertEquals(2, page.items.size)

        val (first, second) = tiktok.urls.map { it.queryParameter("device_id") }
        assertEquals(device, first)
        assertNotEquals(device, second)
        assertEquals(second, heard.last().deviceId, "the new device id is kept")
    }

    @Test
    fun noSuchAccountIsNotFoundAtOnce() {
        val tiktok = FakeTikTok { ok("""{"statusCode":10201,"hasMore":true,"itemList":[]}""") }
        assertEquals(404, refusal { client(tiktok).creatorItems(secUid) }.code)
        assertEquals(1, tiktok.urls.size)
    }

    @Test
    fun aListRefusedByStatusIsUnreadable() {
        val tiktok = FakeTikTok { ok("""{"statusCode":10222,"itemList":[]}""") }
        assertEquals(TikTokClient.UNREADABLE, refusal { client(tiktok).creatorItems(secUid) }.code)
        assertEquals(TikTokClient.UNREADABLE, refusal { client(FakeTikTok { ok("not json") }).creatorItems(secUid) }.code)
    }

    @Test
    fun aVideoIsReadFromItsPage() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("video.html")) }
        val post = client(tiktok).video("7300000000000000001")
        assertEquals("Hello #fyp @tiktok", post.desc)
        assertEquals(5, post.sources.size)
        client(tiktok).video("7300000000000000001", handle = "tiktok")
        assertEquals(
            listOf("/@_/video/7300000000000000001", "/@tiktok/video/7300000000000000001"),
            tiktok.urls.map { it.encodedPath },
        )
        assertEquals(404, refusal { client(FakeTikTok { ok("<html></html>") }).video("1") }.code)
    }

    @Test
    fun suggestionsAskThePreviewWithTheWordsTyped() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("suggest_preview.json")) }
        assertEquals(listOf("nba highlights", "NBA", "nba youngboy"), client(tiktok).suggestQueries(" nba "))
        val (url, headers) = tiktok.requests.single()
        assertEquals("/api/search/general/preview/", url.encodedPath)
        assertEquals("nba", url.queryParameter("keyword"))
        assertEquals("search", url.queryParameter("from_page"))
        assertEquals("https://www.tiktok.com/search?q=nba", headers["Referer"])
        assertTrue(client(tiktok).suggestQueries("  ").isEmpty())
        assertEquals(1, tiktok.urls.size)
    }

    @Test
    fun fixedFromXtaTheSearchRefererCarriesTheWordsEncoded() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("suggest_preview.json")) }
        client(tiktok).suggestQueries("café olé")
        val referer = tiktok.requests.single().second["Referer"]!!
        assertEquals("https://www.tiktok.com/search?q=caf%C3%A9%20ol%C3%A9", referer)
        assertTrue(referer.all { it.code in 0x20..0x7e }, "a header holds printable ASCII alone")
    }

    @Test
    fun trendingAsksTheGuide() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok(fixture("suggest_guide.json")) }
        assertEquals(listOf("NFL Preseason", "cats meowing"), client(tiktok).trendingQueries())
        val (url, headers) = tiktok.requests.single()
        assertEquals("/api/search/suggest/guide/", url.encodedPath)
        assertEquals("search", url.queryParameter("from_page"))
        assertEquals("https://www.tiktok.com/search", headers["Referer"])
    }

    @Test
    fun discoverIsKeptForTenMinutes() = runBlocking<Unit> {
        var now = 1_700_000_000_000L
        val tiktok = FakeTikTok { ok(fixture("discover.json")) }
        val reader = TikTokClient(tiktok, session = TikTokSession(deviceId = device), clock = { now })
        assertEquals(2, reader.discoverUsers().size)
        now += 9 * 60 * 1000L
        reader.discoverUsers()
        assertEquals(1, tiktok.urls.size)
        assertEquals("https://www.tiktok.com/node/share/discover", tiktok.urls.single().toString())
        now += 2 * 60 * 1000L
        reader.discoverUsers()
        assertEquals(2, tiktok.urls.size)
    }

    @Test
    fun aSearchFindsADisplayNameThroughProfilePagesAndDiscover() = runBlocking<Unit> {
        val tiktok = FakeTikTok { url ->
            val path = url.encodedPath
            when {
                path == "/api/search/general/preview/" -> ok("""{"sug_list":[{"content":"charli d amelio"}]}""")
                path == "/node/share/discover" -> ok(
                    """{"statusCode":0,"body":[{"exploreList":[""" +
                        """{"cardItem":{"type":2,"title":"charli d’amelio","link":"/@charlidamelio","extraInfo":{"verified":true,"fans":150000000}}},""" +
                        """{"cardItem":{"type":2,"title":"The Rock","link":"/@therock","extraInfo":{"verified":true,"fans":1}}}]}]}""",
                )
                path == "/@charlidamelio" || path == "/@charli" -> ok(profileHtml(path.drop(2), nickname = "charli damelio"))
                path == "/api/creator/item_list/" -> ok(
                    """{"statusCode":0,"hasMore":false,"itemList":[{"id":"99","desc":"hi","createTime":1700000000,""" +
                        """"author":{"uniqueId":"charlidamelio","nickname":"charli"},""" +
                        """"video":{"playAddr":"https://v16.tiktokcdn.com/a.mp4","cover":"https://p16.tiktokcdn.com/c.jpg"}}]}""",
                )
                else -> missing
            }
        }
        val page = client(tiktok).search("Charli D'Amelio")

        val paths = tiktok.urls.map { it.encodedPath }
        assertTrue("/api/search/general/preview/" in paths)
        assertTrue("/node/share/discover" in paths)
        assertTrue("/@charlidamelio" in paths)
        assertEquals(listOf("charlidamelio", "charli"), page.users.map { it.uniqueId }, "found by username; The Rock does not match")
        assertEquals(listOf("99"), page.posts.map { it.id }, "the same video from both accounts' lists, once")
        assertEquals(listOf("charli d amelio"), page.suggestions)
    }

    @Test
    fun aSearchWhosePartsAllFailIsEmptyNotAnError() = runBlocking<Unit> {
        val page = client(FakeTikTok { XResponse(500, "") }).search("nba")
        assertTrue(page.users.isEmpty() && page.posts.isEmpty() && page.suggestions.isEmpty())
        assertEquals(TikTokSearchPage(), client(FakeTikTok { missing }).search("   "))
    }

    @Test
    fun theCookiesTikTokSetsAreKeptAndHeard() = runBlocking<Unit> {
        val heard = mutableListOf<TikTokSession>()
        val setCookie = "tt_csrf_token=abc; expires=Wed, 21 Oct 2026 07:28:00 GMT; path=/, ttwid=1%7Cxyz; Path=/; HttpOnly, msToken=m2; Path=/"
        val tiktok = FakeTikTok { XResponse(200, fixture("profile.html"), mapOf("set-cookie" to listOf(setCookie))) }
        val reader = client(tiktok, onSession = { heard += it })
        reader.profile("tiktok")
        assertEquals(mapOf("tt_csrf_token" to "abc", "ttwid" to "1%7Cxyz", "msToken" to "m2"), reader.cookies, "an expiry's comma splits nothing")
        assertEquals(TikTokSession("tt_csrf_token=abc; ttwid=1%7Cxyz; msToken=m2", device), heard.last())

        reader.profile("tiktok")
        assertEquals("tt_csrf_token=abc; ttwid=1%7Cxyz; msToken=m2", tiktok.requests.last().second["Cookie"])
    }

    @Test
    fun playbackCarriesTheBrowserTikTokAndTheCookies() {
        val reader = client(FakeTikTok { missing }, TikTokSession(cookies = "ttwid=t1", deviceId = device))
        assertEquals(
            mapOf(
                "User-Agent" to TikTokRepairs().userAgent,
                "Referer" to "https://www.tiktok.com/",
                "Origin" to "https://www.tiktok.com",
                "Cookie" to "ttwid=t1",
            ),
            reader.playbackHeaders,
        )
        assertFalse("Cookie" in client(FakeTikTok { missing }).playbackHeaders)
    }

    @Test
    fun aNewGuestMakesUpADeviceIdOnceAndKeepsIt() {
        val heard = mutableListOf<TikTokSession>()
        val reader = TikTokClient(FakeTikTok { missing }, onSession = { heard += it })
        val id = reader.deviceId
        assertTrue(id.toLong() in TikTokClient.DEVICE_ID_MIN..TikTokClient.DEVICE_ID_MAX)
        assertEquals(id, reader.deviceId)
        assertEquals(listOf(TikTokSession("", id)), heard)
    }

    @Test
    fun aClearedSessionStartsANewGuest() {
        val heard = mutableListOf<TikTokSession>()
        val reader = client(FakeTikTok { missing }, TikTokSession(cookies = "ttwid=t1", deviceId = device), onSession = { heard += it })
        reader.clearSession()
        assertEquals("", reader.cookieHeader)
        assertEquals(TikTokSession(), heard.last())
        assertNotEquals("", reader.deviceId)
    }

    @Test
    fun theAidAndUserAgentInForceAreSent() = runBlocking<Unit> {
        val tiktok = FakeTikTok { ok("""{"sug_list":[]}""") }
        val reader = TikTokClient(
            tiktok,
            session = TikTokSession(deviceId = device),
            repairs = { TikTokRepairs(aid = "2000", userAgent = "Repaired/1.0") },
        )
        reader.suggestQueries("flutter")
        val (url, headers) = tiktok.requests.single()
        assertEquals("2000", url.queryParameter("aid"))
        assertEquals("Repaired/1.0", headers["User-Agent"])
        assertEquals("Repaired/1.0", reader.userAgent)
    }
}
