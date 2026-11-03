package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class XTranslatorTest {
    private val maya = XAccount(
        id = "ct0-maya",
        screenName = "maya",
        headers = mapOf("Cookie" to "auth_token=maya; ct0=ct0-maya", "x-csrf-token" to "ct0-maya", "authorization" to "Bearer from-maya"),
    )
    private val signers = XSigners({ TransactionSigner(IntArray(48) { it }, "animation") })

    private val translated = """
        {"result":{"text":"Hallo @jack #welt https://t.co/a https://t.co/m","entities":{
          "user_mentions":[{"screen_name":"jack","indices":[6,11]}],
          "hashtags":[{"text":"welt","indices":[12,17]}],
          "urls":[{"url":"https://t.co/a","expanded_url":"https://example.com/a","display_url":"example.com/a","indices":[18,32]}],
          "media":[{"url":"https://t.co/m","expanded_url":"https://x.com/maya/status/1/photo/1","display_url":"pic.x.com/m","indices":[33,47]}]
        }}}
    """.trimIndent()

    private class MemoryCache : TranslationCache {
        val kept = HashMap<String, Pair<String, Long>>()
        override suspend fun read(key: String): String? = kept[key]?.first
        override suspend fun write(key: String, value: String, ttlMillis: Long) {
            kept[key] = value to ttlMillis
        }
    }

    private fun translator(http: FakeHttp, cache: TranslationCache? = null, accounts: List<XAccount> = listOf(maya)) =
        XTranslator(http, { accounts }, signers, cache, retryDelayMillis = 1)

    @Test
    fun aTranslationIsAskedOfXsGrokAsXtaAsksIt() = runBlocking {
        val http = FakeHttp { response(200, translated) }
        translator(http).translate("1", "en", "de-DE")
        val asked = http.asked.single()
        assertEquals("POST", asked.method)
        assertEquals("https://api.x.com/2/grok/translation.json", asked.url.toString())
        assertEquals("text/plain;charset=UTF-8", asked.contentType)
        assertEquals("""{"id":"1","dst_lang":"de-DE","content_type":"POST"}""", asked.body)
        assertEquals("Bearer from-maya", asked.headers["authorization"], "the account's own header wins")
        assertEquals("auth_token=maya; ct0=ct0-maya", asked.headers["Cookie"])
        assertTrue(asked.headers["x-client-transaction-id"]!!.length > 40)
    }

    @Test
    fun theContentTypeAndEncodingAreLeftToTheHttpClient() = runBlocking {
        val http = FakeHttp { response(200, translated) }
        translator(http).translate("1", "en", "de-DE")
        val names = http.asked.single().headers.keys.map { it.lowercase() }
        assertFalse("content-type" in names, "the body names its own type")
        assertFalse("accept-encoding" in names, "XTA asked for br and zstd, which OkHttp would hand back undecoded")
    }

    @Test
    fun theTranslationComesWithItsEntities() = runBlocking {
        val translation = translator(FakeHttp { response(200, translated) }).translate("1", "en", "de-DE")
        assertEquals("Hallo @jack #welt https://t.co/a https://t.co/m", translation.text)
        assertEquals(
            listOf(
                XEntity.Mention(6, 11, "jack"),
                XEntity.Hashtag(12, 17, "welt"),
                XEntity.Link(18, 32, "https://t.co/a", "https://example.com/a", "example.com/a"),
                XEntity.Link(33, 47, "https://t.co/m", "https://x.com/maya/status/1/photo/1", "pic.x.com/m"),
            ),
            translation.entities,
        )
    }

    @Test
    fun aTranslatedPostStillHidesTheLinkXPutInForItsMedia() = runBlocking {
        val translation = translator(FakeHttp { response(200, translated) }).translate("1", "en", "de-DE")
        val post = XPost(
            id = "1", author = XUser("1", "maya", "Maya"), createdAt = null, text = "Hello @jack #world https://t.co/a https://t.co/m",
            displayEnd = 33, media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/m.jpg", 1, 1, shortUrl = "https://t.co/m")),
        )
        assertEquals(
            listOf(XRun.Text("Hallo "), XRun.Mention("@jack", "jack"), XRun.Text(" "), XRun.Hashtag("#welt", "welt"), XRun.Text(" "), XRun.Link("example.com/a", "https://example.com/a")),
            XText.runs(translation.applyTo(post)),
        )
    }

    @Test
    fun aKeptTranslationIsServedAndOnlySuccessesAreKeptForAWeek() = runBlocking {
        val cache = MemoryCache()
        val http = FakeHttp { response(200, translated) }
        translator(http, cache).translate("1", "en", "de-DE")
        translator(http, cache).translate("1", "en", "de-DE")
        assertEquals(1, http.asked.size)
        assertEquals(translated to XTranslator.CACHE_TTL_MILLIS, cache.kept["translation.en.de-DE.1"])
        assertTrue(XTranslator.CACHE_TTL_MILLIS in 1 until 365L * 24 * 60 * 60 * 1000)

        val refused = FakeHttp { response(429, "{}") }
        assertFailsWith<TranslationException> { translator(refused, cache).translate("2", "en", "de-DE") }
        assertNull(cache.kept["translation.en.de-DE.2"])
    }

    @Test
    fun translationsIntoAnotherLanguageOrOfAnotherPostDoNotShareAKey() {
        assertTrue(XTranslator.cacheKey("123", "en", "fr") != XTranslator.cacheKey("123", "en", "de"))
        assertTrue(XTranslator.cacheKey("123", "en", "fr") != XTranslator.cacheKey("456", "en", "fr"))
        assertTrue(XTranslator.cacheKey("123", "en", "fr") != XTranslator.cacheKey("123", "es", "fr"))
        assertTrue(XTranslator.cacheKey("1", "en", "pt-BR") != XTranslator.cacheKey("1", "en", "pt"))
        assertEquals(XTranslator.cacheKey("123", "en", "fr"), XTranslator.cacheKey("123", "en", "fr"))
    }

    @Test
    fun anUnusableKeptEntryIsAMissNotACrash() {
        assertNull(XTranslator.decodeCached(null))
        assertNull(XTranslator.decodeCached(""))
        assertNull(XTranslator.decodeCached("not json at all"))
        assertNull(XTranslator.decodeCached("""{"translation":"bonjour"}"""), "nothing the post can show")
        assertEquals("bonjour", XTranslator.decodeCached("""{"result":{"text":"bonjour"}}""")?.text)
    }

    @Test
    fun anAnswerThatIsNotJsonYetIsAskedAgain() = runBlocking {
        var calls = 0
        val http = FakeHttp { calls++; if (calls < 3) response(200, "<html>generating</html>") else response(200, translated) }
        assertEquals("Hallo @jack #welt https://t.co/a https://t.co/m", translator(http).translate("1", "en", "de").text)
        assertEquals(3, http.asked.size)

        val never = FakeHttp { response(200, "") }
        assertEquals(TranslationFailure.NotReady, assertFailsWith<TranslationException> { translator(never).translate("1", "en", "de") }.failure)
        assertEquals(3, never.asked.size)
    }

    @Test
    fun xsRefusalsAreTyped() = runBlocking {
        suspend fun failure(code: Int, body: String) =
            assertFailsWith<TranslationException> { translator(FakeHttp { response(code, body) }).translate("1", "xx", "de") }.failure
        assertEquals(TranslationFailure.Unsupported("tlh"), failure(400, """{"error":"tlh is not supported"}"""))
        assertEquals(TranslationFailure.Refused("bad request"), failure(400, """{"error":"bad request"}"""))
        assertEquals(TranslationFailure.Refused(null), failure(400, """{"error":{"nested":true}}"""))
        assertEquals(TranslationFailure.Banned, failure(403, "{}"))
        assertEquals(TranslationFailure.TooMany, failure(429, "{}"))
        assertEquals(TranslationFailure.Failed, failure(500, "{}"))
        assertEquals(TranslationFailure.Http(418), failure(418, "{}"))
        assertEquals(TranslationFailure.Refused("quota"), failure(200, """{"error":{"message":"quota","code":88}}"""))
        assertEquals(TranslationFailure.Refused("88"), failure(200, """{"error":{"code":88}}"""))
        assertEquals(TranslationFailure.Failed, failure(200, """{"result":{}}"""))
    }

    @Test
    fun withoutAnAccountTheRequestStillGoesSignedAsAGuestWould() = runBlocking {
        val http = FakeHttp { response(403, "{}") }
        assertFailsWith<TranslationException> { translator(http, accounts = emptyList()).translate("1", "en", "de") }
        val headers = http.asked.single().headers
        assertTrue(headers["authorization"]!!.startsWith("Bearer AAAA"))
        assertNull(headers["Cookie"])
    }

    @Test
    fun translationIsOfferedForPostsInAnotherLanguageOnly() {
        assertTrue(XTranslator.offers("de", "en"))
        assertFalse(XTranslator.offers("en", "en"))
        assertFalse(XTranslator.offers("en-GB", "en_US"))
        XTranslator.UNTRANSLATABLE.forEach { assertFalse(XTranslator.offers(it, "en"), it) }
        assertTrue(XTranslator.offers(null, "en"), "an idle button beats a stranded reader")
        assertTrue(XTranslator.offers("", "en"))
    }

    @Test
    fun theTargetIsTheAppsLanguageOrTheSystemsAsATag() {
        assertEquals("de-DE", XTranslator.target("system", "de_DE"))
        assertEquals("de-DE", XTranslator.target(null, "de_DE"))
        assertEquals("pt-BR", XTranslator.target("pt_BR", "de_DE"))
        assertEquals("fr", XTranslator.target("fr", "de_DE"))
        assertEquals("en-US", XTranslator.target(null, "en_US.UTF-8"), "XTA kept the encoding of a Linux locale in the tag")
        assertEquals("zh-Hant", XTranslator.target("zh_Hant", "en"))
    }

    @Test
    fun theRequestBodyNamesThePostAndTheLanguage() {
        assertEquals("POST", J(XTranslator.request("9", "ja"))["content_type"].string)
    }
}
