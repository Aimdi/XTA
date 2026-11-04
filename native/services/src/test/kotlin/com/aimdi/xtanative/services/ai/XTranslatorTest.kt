package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.x.XTranslationException
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XRun
import com.aimdi.xtanative.core.x.parse.XText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The translator over a stand-in for XClient.translate: what it asks, what it keeps, what it hands back. */
class XTranslatorTest {
    private val hallo = XTranslation(
        "Hallo @jack #welt \$TSLA https://t.co/a https://t.co/m",
        listOf(
            XEntity.Mention(6, 11, "jack"),
            XEntity.Hashtag(12, 17, "welt"),
            XEntity.Cashtag(18, 23, "TSLA"),
            XEntity.Link(24, 38, "https://t.co/a", "https://example.com/a", "example.com/a"),
            XEntity.Link(39, 53, "https://t.co/m", "https://x.com/maya/status/1/photo/1", "pic.x.com/m"),
        ),
    )

    /** XClient.translate as a test sees it: each request kept, answered by [answer]. */
    private class FakeTranslate(private val answer: (String, String) -> XTranslation) {
        val asked = ArrayList<Pair<String, String>>()

        suspend fun translate(postId: String, language: String): XTranslation {
            asked += postId to language
            return answer(postId, language)
        }
    }

    private class MemoryCache(val failing: Boolean = false) : TranslationCache {
        val kept = HashMap<String, Pair<String, Long>>()

        override suspend fun read(key: String): String? {
            if (failing) error("disk gone")
            return kept[key]?.first
        }

        override suspend fun write(key: String, value: String, ttlMillis: Long) {
            if (failing) error("disk gone")
            kept[key] = value to ttlMillis
        }
    }

    private fun answering(translation: XTranslation = hallo) = FakeTranslate { _, _ -> translation }

    private fun translator(fake: FakeTranslate, cache: TranslationCache? = null) = XTranslator(fake::translate, cache)

    @Test
    fun theClientIsAskedForThePostInTheTargetLanguage(): Unit = runBlocking {
        val fake = answering()
        val translation = translator(fake).translate("1", "en", "de-DE")
        assertEquals(listOf("1" to "de-DE"), fake.asked)
        assertEquals(Translation(hallo.text, hallo.entities), translation)
    }

    @Test
    fun aTranslatedPostStillHidesTheLinkXPutInForItsMedia(): Unit = runBlocking {
        val translation = translator(answering()).translate("1", "en", "de-DE")
        val post = XPost(
            id = "1", author = XUser("1", "maya", "Maya"), createdAt = null, text = "Hello @jack #world \$TSLA https://t.co/a https://t.co/m",
            displayEnd = 39, media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/m.jpg", 1, 1, shortUrl = "https://t.co/m")),
        )
        assertEquals(
            listOf(
                XRun.Text("Hallo "), XRun.Mention("@jack", "jack"), XRun.Text(" "), XRun.Hashtag("#welt", "welt"), XRun.Text(" "),
                XRun.Cashtag("\$TSLA", "TSLA"), XRun.Text(" "), XRun.Link("example.com/a", "https://example.com/a"),
            ),
            XText.runs(translation.applyTo(post)),
        )
    }

    @Test
    fun aKeptTranslationIsServedAndOnlySuccessesAreKeptForAWeek(): Unit = runBlocking {
        val cache = MemoryCache()
        val fake = answering()
        val first = translator(fake, cache).translate("1", "en", "de-DE")
        val second = translator(fake, cache).translate("1", "en", "de-DE")
        assertEquals(1, fake.asked.size)
        assertEquals(first, second, "the kept copy reads back as the translation, entities and all")
        assertEquals(XTranslator.CACHE_TTL_MILLIS, cache.kept.getValue("translation.en.de-DE.1").second)
        assertTrue(XTranslator.CACHE_TTL_MILLIS in 1 until 365L * 24 * 60 * 60 * 1000)

        val refusing = FakeTranslate { _, _ -> throw XTranslationException(XTranslationException.Kind.TOO_MANY_REQUESTS, "slow down") }
        assertFailsWith<XTranslationException> { translator(refusing, cache).translate("2", "en", "de-DE") }
        assertNull(cache.kept["translation.en.de-DE.2"])
    }

    @Test
    fun xsRefusalReachesTheCallerWithItsKind(): Unit = runBlocking {
        XTranslationException.Kind.entries.forEach { kind ->
            val refusing = FakeTranslate { _, _ -> throw XTranslationException(kind, "no") }
            assertEquals(kind, assertFailsWith<XTranslationException> { translator(refusing).translate("1", "xx", "de") }.kind)
        }
    }

    @Test
    fun aCacheThatFailsIsNoReasonNotToTranslate(): Unit = runBlocking {
        val fake = answering()
        assertEquals(hallo.text, translator(fake, MemoryCache(failing = true)).translate("1", "en", "de").text)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun aKeptEntryIsWhatXAnsweredSoItReadsAsXtasCacheDoes() {
        val stored = XTranslator.encodeCached(Translation(hallo.text, hallo.entities))
        assertTrue(stored.startsWith("""{"result":{"text":"""))
        assertEquals(Translation(hallo.text, hallo.entities), XTranslator.decodeCached(stored))
        assertEquals("bonjour", XTranslator.decodeCached("""{"result":{"text":"bonjour"}}""")?.text, "an entry XTA wrote reads too")
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
    fun anUnusableKeptEntryIsAMissNotACrash(): Unit = runBlocking {
        assertNull(XTranslator.decodeCached(null))
        assertNull(XTranslator.decodeCached(""))
        assertNull(XTranslator.decodeCached("not json at all"))
        assertNull(XTranslator.decodeCached("""{"translation":"bonjour"}"""), "nothing the post can show")
        val cache = MemoryCache().apply { kept["translation.en.de.1"] = "<html>" to 1L }
        val fake = answering()
        assertEquals(hallo.text, translator(fake, cache).translate("1", "en", "de").text)
        assertEquals(1, fake.asked.size, "asked again in place of the broken entry")
    }

    @Test
    fun theSameTranslatorKeepsNothingOfItsOwnBetweenPosts(): Unit = runBlocking {
        val fake = FakeTranslate { id, _ -> XTranslation("post $id") }
        val translator = translator(fake)
        assertEquals("post 1", translator.translate("1", null, "de").text)
        assertEquals("post 2", translator.translate("2", null, "de").text)
        assertEquals(listOf("1" to "de", "2" to "de"), fake.asked)
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
}
