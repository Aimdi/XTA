package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class PixivClientTest {
    private val oauthRefusal = """{"error": {"user_message": "", "message": "Error occurred at the OAuth process. Please check your Access Token to fix this. Error Message: invalid_grant", "reason": ""}}"""

    private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun aRefreshKeepsBothTokensAndNamesTheAccount() = runBlocking {
        val credentials = MemoryPixivCredentials(PixivCredentials.pasted("refresh-me"))
        val http = FakePixivHttp { tokenAnswer("access-1", "refresh-2", """{"id": "123", "name": "Reader", "account": "reader"}""") }
        val user = pixiv(http, credentials).refreshAccessToken()

        val call = http.asked.single()
        assertEquals("POST", call.method)
        assertEquals("https://oauth.secure.pixiv.net/auth/token", call.url.toString())
        assertEquals("application/x-www-form-urlencoded", call.contentType)
        assertEquals(
            mapOf(
                "client_id" to PixivApp().clientId,
                "client_secret" to PixivApp().clientSecret,
                "grant_type" to "refresh_token",
                "include_policy" to "true",
                "refresh_token" to "refresh-me",
            ),
            call.form,
        )
        assertEquals(123L to "Reader", user.id to user.name)
        val kept = credentials.credentials
        assertEquals(listOf("access-1", "refresh-2"), listOf(kept.accessToken, kept.refreshToken))
        assertEquals(123L, kept.userId)
        assertEquals(NOW + (3600 - 60) * 1000L, kept.accessExpiresAt, "a minute before Pixiv's own expiry")
    }

    @Test
    fun withoutARefreshTokenThereIsNoAccountAndNothingIsAsked() = runBlocking {
        val http = FakePixivHttp { answer("", 500) }
        val error = assertFailsWith<PixivNoAccountException> { pixiv(http, MemoryPixivCredentials()).verify() }
        assertEquals(PixivFailure.NOT_SIGNED_IN, PixivFailure.of(error))
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun everyRequestCarriesTheTimeAndItsHashAndTheyAgree() = runBlocking {
        val http = FakePixivHttp { tokenAnswer("access-1") }
        pixiv(http, MemoryPixivCredentials(PixivCredentials.pasted("refresh-me"))).verify()

        val headers = http.asked.single().headers
        val time = "2026-08-06T01:02:03+00:00"
        assertEquals(time, headers["X-Client-Time"])
        assertEquals(md5(time + "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"), headers["X-Client-Hash"])
    }

    @Test
    fun theApiReadsAreSignedTheSameWayAndSayWhichAppAsks() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson()) }
        pixiv(http).following()

        val headers = http.asked.single().headers
        assertEquals(md5(headers.getValue("X-Client-Time") + CLIENT_HASH_SALT), headers["X-Client-Hash"])
        assertEquals("Bearer access-1", headers["Authorization"])
        assertEquals("PixivAndroidApp/5.0.234 (Android 11; Pixel 5)", headers["User-Agent"])
        assertEquals(listOf("android", "11", "5.0.234", "application/json"), listOf("App-OS", "App-OS-Version", "App-Version", "Accept").map { headers[it] })
    }

    @Test
    fun acceptLanguageMapsLocalesOntoTheFiveLanguagesPixivAnswersIn() {
        val mapped = listOf("ja", "ko", "zh_Hans", "zh", "zh_Hant", "zh-TW", "zh-Hant-TW", "en", "de", "pt_BR", "nb_NO", "be_Latn")
            .associateWith(::pixivAcceptLanguage)
        assertEquals(
            mapOf(
                "ja" to "ja", "ko" to "ko", "zh_Hans" to "zh-CN", "zh" to "zh-CN", "zh_Hant" to "zh-TW", "zh-TW" to "zh-TW",
                "zh-Hant-TW" to "zh-TW", "en" to "en", "de" to "en", "pt_BR" to "en", "nb_NO" to "en", "be_Latn" to "en",
            ),
            mapped,
        )
    }

    @Test
    fun everyRequestCarriesTheActiveLocaleTokenRequestsIncluded() = runBlocking {
        val credentials = MemoryPixivCredentials(PixivCredentials.pasted("refresh-me"))
        val http = FakePixivHttp { if (it.isTokenCall) tokenAnswer("access-2") else answer(illustsJson()) }
        pixiv(http, credentials, locale = "zh_Hant").following()

        assertEquals(
            mapOf("oauth.secure.pixiv.net" to "zh-TW", "app-api.pixiv.net" to "zh-TW"),
            http.asked.associate { it.url.host to it.headers["Accept-Language"] },
        )
    }

    @Test
    fun pixivsRateLimitAnswerReadsAsRateLimitingNotARefusedToken() = runBlocking {
        for (status in listOf(400, 403)) {
            val http = FakePixivHttp { answer("""{"error": {"user_message": "", "message": "Rate Limit", "reason": ""}}""", status) }
            val error = assertFailsWith<NetworkRequestException> { pixiv(http).getJson("/v1/illust/recommended") }
            assertEquals(429, error.code, "HTTP $status")
            assertEquals(PixivFailure.RATE_LIMITED, PixivFailure.of(error))
            assertTrue(http.tokenCalls.isEmpty(), "a rate limit is no reason to refresh the token")
        }
    }

    @Test
    fun aRefusedRefreshSaysWhatPixivSaid() = runBlocking {
        val http = FakePixivHttp { answer("""{"has_error": true, "errors": {"system": {"message": "Invalid refresh token", "code": 1508}}}""", 403) }
        val error = assertFailsWith<NetworkRequestException> { pixiv(http, MemoryPixivCredentials(PixivCredentials.pasted("bad"))).verify() }
        assertEquals(PixivFailure.UNAUTHORIZED, PixivFailure.of(error))
        assertTrue("Invalid refresh token" in error.message.orEmpty())
    }

    @Test
    fun refreshesAskedForTogetherShareOneRoundTrip() = runBlocking {
        val http = FakePixivHttp {
            delay(40)
            tokenAnswer("access-shared", user = """{"id": "7", "name": "A", "account": "a"}""")
        }
        val client = pixiv(http, MemoryPixivCredentials(PixivCredentials.pasted("refresh-me")))
        val results = listOf(async { client.refreshAccessToken().id }, async { client.refreshAccessToken().id }, async { client.ensureUserId() }).awaitAll()

        assertEquals(1, http.tokenCalls.size)
        assertEquals(listOf(7L, 7L, 7L), results)
        assertEquals(7L, client.storedUserId)
    }

    @Test
    fun aRefreshAskedForAfterAnotherEndedMakesItsOwn() = runBlocking {
        val http = FakePixivHttp { tokenAnswer("access-${it.form["refresh_token"]}") }
        val client = pixiv(http, MemoryPixivCredentials(PixivCredentials.pasted("refresh-me")))
        client.refreshAccessToken()
        client.refreshAccessToken()
        assertEquals(2, http.tokenCalls.size)
    }

    @Test
    fun ensureUserIdReusesAStoredIdWithoutRefreshing() = runBlocking {
        val http = FakePixivHttp { tokenAnswer("never") }
        assertEquals(42L, pixiv(http, signedIn(userId = 42)).ensureUserId())
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun aTokenRefusedWithA400AboutOAuthIsRefreshedOnceAndTheRequestReplayed() = runBlocking {
        val credentials = signedIn()
        val http = FakePixivHttp { request ->
            when {
                request.isTokenCall -> tokenAnswer("access-2")
                request.headers["Authorization"] == "Bearer access-1" -> answer(oauthRefusal, 400)
                else -> answer(illustsJson())
            }
        }
        val page = pixiv(http, credentials).following()

        assertTrue(page.items.isEmpty())
        assertEquals(1, http.tokenCalls.size)
        assertEquals(listOf("Bearer access-1", "Bearer access-2"), http.reads.map { it.headers["Authorization"] })
        assertEquals("access-2", credentials.credentials.accessToken)
    }

    @Test
    fun requestsRefusedTogetherShareOneRefresh() = runBlocking {
        val refreshed = CompletableDeferred<Unit>()
        val http = FakePixivHttp { request ->
            when {
                request.isTokenCall -> refreshed.await().let { tokenAnswer("access-2") }
                request.headers["Authorization"] == "Bearer access-1" -> answer(oauthRefusal, 400)
                else -> answer(illustsJson())
            }
        }
        val client = pixiv(http)
        val both = listOf(async { client.following() }, async { client.recommended() })
        repeat(5) { yield() }
        refreshed.complete(Unit)
        both.awaitAll()
        assertEquals(1, http.tokenCalls.size)
    }

    @Test
    fun aRefusalAfterTheRefreshIsReportedNotRetriedForever() = runBlocking {
        var refreshes = 0
        val http = FakePixivHttp { request ->
            if (request.isTokenCall) tokenAnswer("access-${++refreshes + 1}") else answer(oauthRefusal, 400)
        }
        val error = assertFailsWith<NetworkRequestException> { pixiv(http).following() }
        assertEquals(401, error.code)
        assertEquals(PixivFailure.UNAUTHORIZED, PixivFailure.of(error))
        assertEquals(1 to 2, http.tokenCalls.size to http.reads.size)
    }

    @Test
    fun anyOther400IsABadResponseAndLeavesTheTokenAlone() = runBlocking {
        val http = FakePixivHttp { answer("""{"error": {"message": "Invalid illust_id"}}""", 400) }
        val error = assertFailsWith<NetworkRequestException> { pixiv(http).illustDetail(1) }
        assertEquals(400, error.code)
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error))
        assertTrue(http.tokenCalls.isEmpty())
    }

    @Test
    fun aMissingPageIsNotFoundAndAnAnswerThatIsNoJsonABadResponse() = runBlocking {
        val missing = assertFailsWith<NetworkRequestException> { pixiv(FakePixivHttp { answer("", 404) }).illustDetail(1) }
        assertEquals(PixivFailure.NOT_FOUND, PixivFailure.of(missing))
        val html = assertFailsWith<NetworkRequestException> { pixiv(FakePixivHttp { answer("<html>maintenance</html>") }).following() }
        assertEquals(200 to PixivFailure.BAD_RESPONSE, html.code to PixivFailure.of(html))
    }

    @Test
    fun aWorkPixivNoLongerShowsIsABadResponse() = runBlocking {
        val gone = """{"illust": {"id": 1, "visible": false, "image_urls": {"medium": "https://s.pximg.net/common/images/limit_unknown_360.png"}}}"""
        val error = assertFailsWith<NetworkRequestException> { pixiv(FakePixivHttp { answer(gone) }).illustDetail(1) }
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error))
    }

    @Test
    fun aReadWithoutAuthSendsNoTokenAndNeverRefreshes() = runBlocking {
        val http = FakePixivHttp { answer("""{"ok": true}""") }
        val json = pixiv(http, MemoryPixivCredentials()).getJson("/v1/walkthrough/illusts", auth = false)

        assertEquals(true, json["ok"].boolean)
        val read = http.asked.single()
        assertEquals("app-api.pixiv.net", read.url.host)
        assertFalse("Authorization" in read.headers)
        assertTrue(read.headers["X-Client-Hash"].orEmpty().isNotEmpty())
    }

    @Test
    fun theTokenNeverLeavesTheAppApiHost() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson()) }
        val client = pixiv(http)
        client.getNextJson("https://app-api.pixiv.net/v1/illust/ranking?offset=30")
        client.getNextJson("https://elsewhere.example/v1/illust/ranking?offset=30")

        assertEquals(listOf("Bearer access-1", null), http.asked.map { it.headers["Authorization"] })
        assertEquals("30", http.asked.first().query["offset"])
    }

    @Test
    fun getTextReturnsAnHtmlBodyAsText() = runBlocking {
        val http = FakePixivHttp { answer("<html><body>小説</body></html>") }
        val text = pixiv(http).getText("/webview/v2/novel", mapOf("id" to "9"))

        assertEquals("<html><body>小説</body></html>", text)
        assertEquals("text/html", http.asked.single().headers["Accept"])
        assertEquals(mapOf("id" to "9"), http.asked.single().query)
    }

    @Test
    fun illustPageFollowsTheReadersFiltersUnlessItIsTheirOwnList() {
        val client = pixiv(FakePixivHttp { answer("") }, filters = PixivFilters(hideAi = true))
        val json = J.parse(
            illustsJson(workJson(1), workJson(2, r18 = true), workJson(3, ai = true), next = "https://app-api.pixiv.net/next"),
        )

        val feed = client.illustPage(json)
        assertEquals(listOf(1L), feed.items.map { it.id })
        assertEquals("https://app-api.pixiv.net/next", feed.next)
        assertEquals(listOf(1L, 2L, 3L), client.illustPage(json, ownList = true).items.map { it.id })
        assertEquals(3, client.illustPage(json, includeR18 = true, includeAi = true).items.size)
    }

    @Test
    fun premiumIsKeptFromTheTokenResponseAndClearedOnSignOut() = runBlocking {
        val http = FakePixivHttp { tokenAnswer("access-2", user = """{"id": "7", "name": "Reader", "account": "r", "is_premium": true}""") }
        val client = pixiv(http, MemoryPixivCredentials(PixivCredentials.pasted("refresh-me")))
        val user = client.refreshAccessToken()
        assertEquals(true to true, user.isPremium to client.isPremium)

        client.signOut()
        assertFalse(client.isPremium)
        assertNull(client.storedUserId)
    }

    @Test
    fun aTokenResponseWithoutAUserKeepsWhatWasKnown() = runBlocking {
        val credentials = MemoryPixivCredentials(PixivCredentials(refreshToken = "refresh-me", userId = 9, isPremium = true))
        val client = pixiv(FakePixivHttp { tokenAnswer("access-2", user = null) }, credentials)
        client.refreshAccessToken()
        assertEquals(9L to true, credentials.credentials.userId to credentials.credentials.isPremium)
    }

    @Test
    fun anAnswerForAnAccountSwitchedAwayFromRefreshesTheOneNowInUse() = runBlocking {
        val credentials = MemoryPixivCredentials(PixivCredentials.account(1, "refresh-old"))
        lateinit var client: PixivClient
        val http = FakePixivHttp { request ->
            val token = request.form.getValue("refresh_token")
            if (token == "refresh-old") client.switchTo(2, "refresh-new")
            tokenAnswer("access-for-$token", refresh = token, user = """{"id": "${if (token == "refresh-old") 1 else 2}"}""")
        }
        client = pixiv(http, credentials)
        val user = client.refreshAccessToken()

        assertEquals(listOf("refresh-old", "refresh-new"), http.tokenCalls.map { it.form["refresh_token"] })
        assertEquals(2L, user.id)
        assertEquals("access-for-refresh-new" to 2L, credentials.credentials.accessToken to credentials.credentials.userId)
    }

    @Test
    fun aPastedTokenIsRefreshedAndNamesItsAccount() = runBlocking {
        val credentials = signedIn(userId = 5)
        val http = FakePixivHttp { tokenAnswer("access-pasted", user = """{"id": "8", "name": "Pasted", "account": "p"}""") }
        val client = pixiv(http, credentials)
        client.useRefreshToken("refresh-pasted")
        assertNull(client.storedUserId, "nobody's until the token check")

        assertEquals(8L, client.ensureUserId())
        assertEquals("refresh-pasted", http.tokenCalls.single().form["refresh_token"])
    }

    @Test
    fun aSwitchedAccountRefreshesBeforeItsFirstRead() = runBlocking {
        val credentials = signedIn()
        val http = FakePixivHttp { if (it.isTokenCall) tokenAnswer("access-of-other") else answer(illustsJson()) }
        val client = pixiv(http, credentials)
        client.switchTo(5, "refresh-other", isPremium = true)
        client.following()

        assertEquals("refresh-other", http.tokenCalls.single().form["refresh_token"])
        assertEquals("Bearer access-of-other", http.reads.single().headers["Authorization"])
    }

    @Test
    fun theCoreReadsAskTheirOwnPathsAndParameters() = runBlocking {
        val http = FakePixivHttp { request ->
            when (request.url.encodedPath) {
                "/v1/illust/detail" -> answer("""{"illust": ${workJson(5)}}""")
                "/v1/ugoira/metadata" -> answer(Fixtures.text("ugoira_metadata.json"))
                else -> answer(illustsJson())
            }
        }
        val client = pixiv(http)
        client.recommended()
        client.following(restrict = "private")
        client.bookmarks(userId = 123, restrict = "private", tag = PIXIV_UNCLASSIFIED_TAG)
        client.bookmarks(userId = 123)
        assertEquals(5L, client.illustDetail(5).id)
        client.ugoiraMetadata(5)
        client.related(5)
        client.userIllusts(9, PixivWorkType.MANGA)

        assertEquals(
            listOf(
                "/v1/illust/recommended" to mapOf("include_ranking_illusts" to "true", "include_privacy_policy" to "true", "filter" to "for_android"),
                "/v2/illust/follow" to mapOf("restrict" to "private"),
                "/v1/user/bookmarks/illust" to mapOf("user_id" to "123", "restrict" to "private", "tag" to "未分類", "filter" to "for_android"),
                "/v1/user/bookmarks/illust" to mapOf("user_id" to "123", "restrict" to "public", "filter" to "for_android"),
                "/v1/illust/detail" to mapOf("illust_id" to "5"),
                "/v1/ugoira/metadata" to mapOf("illust_id" to "5"),
                "/v2/illust/related" to mapOf("illust_id" to "5", "filter" to "for_android"),
                "/v1/user/illusts" to mapOf("user_id" to "9", "type" to "manga", "filter" to "for_android"),
            ),
            http.asked.map { it.url.encodedPath to it.query },
        )
        assertTrue(http.asked.none { it.url.encodedPath == "/v1/user/like/illust" }, "Pixiv's likes are bookmarks")
        assertTrue(http.asked.all { it.method == "GET" })
    }

    @Test
    fun relatedWorksOfAnR18SeedKeepR18() = runBlocking {
        val http = FakePixivHttp {
            answer(illustsJson(workJson(1), workJson(2, r18 = true), next = "https://app-api.pixiv.net/v2/illust/related?offset=30"))
        }
        val client = pixiv(http)
        assertEquals(listOf(1L), client.related(9).items.map { it.id })
        val seed = client.illustPage(J.parse(illustsJson(workJson(9, r18 = true))), includeR18 = true).items.single()
        val kept = client.related(seed)
        assertEquals(listOf(1L, 2L), kept.items.map { it.id })
        assertTrue(kept.next.orEmpty().endsWith("offset=30"))
    }

    @Test
    fun relatedCreatorsPutTheSeedLastAndFollowTheReadersChoices() = runBlocking {
        val http = FakePixivHttp { answer(Fixtures.text("user_previews.json")) }
        val previews = pixiv(http).relatedUsers(42)

        assertEquals(listOf("filter", "seed_user_id"), http.asked.single().url.queryParameterNames.toList())
        assertEquals(listOf(10L, 12L), previews.first().illusts.map { it.id })
        assertEquals(listOf(10L, 11L, 12L), pixiv(http).relatedUsers(42, includeR18 = true).first().illusts.map { it.id })
    }

    @Test
    fun trendingTagsAndSuggestionsCarryTheirTranslations() = runBlocking {
        val http = FakePixivHttp { request ->
            if (request.url.encodedPath == "/v2/search/autocomplete") {
                answer("""{"tags": [{"name": "初音ミク", "translated_name": "Hatsune Miku"}, {"name": "ミク", "translated_name": null}, {"name": ""}]}""")
            } else {
                answer(Fixtures.json("discovery.json")["trend_tags"].raw.toString())
            }
        }
        val client = pixiv(http)
        val trending = client.trendingTags()
        val suggested = client.autocomplete(" mik ")

        assertEquals(listOf("原神", "異世界", "noface"), trending.map { it.name })
        assertEquals(listOf("Genshin", null, null), trending.map { it.translatedName })
        assertEquals(listOf(9L, null, null), trending.map { it.illust?.id }, "an R-18 picture leaves its tag bare")
        assertEquals(listOf("初音ミク" to "Hatsune Miku", "ミク" to null), suggested.map { it.name to it.translatedName })
        assertEquals("mik", http.asked.last().query["word"])
        assertEquals(mapOf("filter" to "for_android"), http.asked.first().query)
    }

    @Test
    fun aNextUrlTheClientCannotAskEndsTheListWhereXtaWouldHaveFailedOnIt() {
        val client = pixiv(FakePixivHttp { answer("") })
        fun next(url: String) = client.illustPage(J.parse("""{"illusts": [], "next_url": $url}""")).next
        assertNull(next("\"garbage\""))
        assertNull(next("\"\""))
        assertNull(next("3"))
        assertEquals("https://app-api.pixiv.net/v1/x?offset=30", next("\"https://app-api.pixiv.net/v1/x?offset=30\""))
    }

    @Test
    fun aBlankWordSuggestsNothingWithoutAsking() = runBlocking {
        val http = FakePixivHttp { error("nothing should be asked") }
        assertTrue(pixiv(http).autocomplete("  ").isEmpty())
    }
}
