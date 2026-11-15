package com.aimdi.xtanative.services.translation

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.services.response
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TranslatorTest {
    private val libre = TranslationConfig(TranslationService.LIBRE, endpoint = "https://translate.example.org/")
    private val languages = """[{"code":"en","name":"English"},{"code":"de","name":"German"},{"code":"pt-BR","name":"Portuguese (Brazil)"}]"""

    private fun libreServer(translated: String = """{"translatedText":"Hello world","detectedLanguage":{"confidence":90,"language":"de"}}""") = FakeHttp { asked ->
        when (asked.url.encodedPath) {
            "/languages" -> response(200, languages)
            "/translate" -> response(200, translated)
            else -> response(404)
        }
    }

    @Test
    fun libreTranslateIsAskedWithTheTextAndLanguagesAlone() = runBlocking {
        val http = libreServer()
        val result = Translator(http).translate("Hallo Welt", libre.copy(apiKey = "k1"), "en")
        assertEquals(Translated("Hello world", "de", "LibreTranslate"), result)
        val post = http.asked.single { it.method == "POST" }
        assertEquals("https://translate.example.org/translate", post.url.toString())
        assertEquals("application/json", post.contentType)
        assertEquals(emptyMap(), post.headers, "nothing names the reader")
        val body = J.parse(post.body!!)
        assertEquals(setOf("q", "source", "target", "format", "api_key"), body.keys)
        assertEquals("Hallo Welt", body["q"].string)
        assertEquals("auto", body["source"].string)
        assertEquals("en", body["target"].string)
        assertEquals("text", body["format"].string)
        assertEquals("k1", body["api_key"].string)
        assertEquals("https://translate.example.org/languages", http.asked.first().url.toString(), "the server is asked what it names the language")
    }

    @Test
    fun noKeyIsSentWhenNoneIsSet() = runBlocking {
        val http = libreServer()
        Translator(http).translate("Hallo", libre, "en")
        assertFalse("api_key" in J.parse(http.asked.last().body!!).keys)
    }

    @Test
    fun theServersOwnCodeIsTakenAndALanguageItLacksIsRefusedBeforeSending() = runBlocking {
        val http = libreServer()
        Translator(http).translate("Olá", libre, "pt_BR")
        assertEquals("pt-BR", J.parse(http.asked.last().body!!)["target"].string)
        val refused = assertFailsWith<TranslationException> { Translator(libreServer()).translate("Hallo", libre, "ja") }
        assertEquals(TranslationFailure.UNSUPPORTED_LANGUAGE, refused.failure)
    }

    @Test
    fun linksLineBreaksAndSpacesStayAsTheyAre() = runBlocking {
        val http = FakeHttp { asked ->
            if (asked.url.encodedPath == "/languages") return@FakeHttp response(200, languages)
            val q = J.parse(asked.body!!)["q"].list.map { it.string!! }
            assertEquals(listOf("Guten Morgen", "Schau mal"), q)
            response(200, """{"translatedText":["Good morning","Look"],"detectedLanguage":[{"language":"de"},{"language":"de"}]}""")
        }
        val result = Translator(http).translate("  Guten Morgen\n\nSchau mal https://example.com/a?b=1 ", libre, "en")
        assertEquals("  Good morning\n\nLook https://example.com/a?b=1 ", result.text)
        assertEquals("de", result.sourceLanguage)
    }

    @Test
    fun failuresAreTyped() = runBlocking {
        suspend fun failure(code: Int, body: String = "") = assertFailsWith<TranslationException> {
            Translator(FakeHttp { if (it.url.encodedPath == "/languages") response(200, languages) else response(code, body) }).translate("Hallo", libre, "en")
        }.failure
        assertEquals(TranslationFailure.REFUSED, failure(403, """{"error":"Visit the portal to get an API key"}"""))
        assertEquals(TranslationFailure.REFUSED, failure(400, """{"error":"Invalid API key"}"""))
        assertEquals(TranslationFailure.RATE_LIMITED, failure(429))
        assertEquals(TranslationFailure.UNSUPPORTED_LANGUAGE, failure(400, """{"error":"xx is not supported"}"""))
        assertEquals(TranslationFailure.BAD_ANSWER, failure(500))
        assertEquals(TranslationFailure.BAD_ANSWER, failure(200, """{"nothing":1}"""))
        val unreachable = assertFailsWith<TranslationException> { Translator(FakeHttp { throw IOException("down") }).translate("Hallo", libre, "en") }
        assertEquals(TranslationFailure.UNREACHABLE, unreachable.failure)
        val off = assertFailsWith<TranslationException> { Translator(libreServer()).translate("Hallo", TranslationConfig(), "en") }
        assertEquals(TranslationFailure.NOT_CONFIGURED, off.failure)
    }

    @Test
    fun aTranslationIsAskedOnceAndKept() = runBlocking {
        val http = libreServer()
        val kept = KeptTranslations(load = { null }, save = {})
        val translator = Translator(http, kept)
        translator.translate("Hallo Welt", libre, "en")
        val again = Translator(libreServer(), kept).translate("Hallo Welt", libre, "en")
        assertEquals(Translated("Hello world", "de", "LibreTranslate"), again)
        assertEquals(1, http.asked.count { it.method == "POST" })
    }

    @Test
    fun deeplTakesItsHostFromTheKeyAndItsCodes() = runBlocking {
        val http = FakeHttp { response(200, """{"translations":[{"detected_source_language":"DE","text":"Hello"}]}""") }
        val result = Translator(http).translate("Hallo", TranslationConfig(TranslationService.DEEPL, apiKey = "abc:fx"), "pt_BR")
        assertEquals(Translated("Hello", "de", "DeepL"), result)
        val asked = http.asked.single()
        assertEquals("https://api-free.deepl.com/v2/translate", asked.url.toString())
        assertEquals("DeepL-Auth-Key abc:fx", asked.headers["Authorization"])
        assertEquals("PT-BR", J.parse(asked.body!!)["target_lang"].string)
        assertEquals("https://api.deepl.com/v2/translate", TranslationConfig(TranslationService.DEEPL, apiKey = "pro").url().toString())
    }

    @Test
    fun theAiProviderTranslatesWithAShortPrompt() = runBlocking {
        val http = FakeHttp { response(200, """{"choices":[{"message":{"content":"Hello"}}]}""") }
        val ai = AiConfig(AiProvider.OPENAI.baseUrl, "sk", "gpt-4o-mini")
        val result = Translator(http).translate("Hallo", TranslationConfig(TranslationService.AI, ai = ai), "en")
        assertEquals(Translated("Hello", null, "OpenAI"), result)
        val prompt = J.parse(http.asked.single().body!!)["messages"][0]["content"].string!!
        assertTrue(prompt.startsWith("Translate the following text into English."))
        assertTrue(prompt.endsWith("\n\nHallo"))
    }

    @Test
    fun theTestReadsWhatTheServerTranslates() = runBlocking {
        val found = Translator(libreServer()).languages(libre)
        assertEquals(listOf("en", "de", "pt-BR"), found.map { it.code })
        val down = assertFailsWith<TranslationException> { Translator(FakeHttp { response(200, "<html>") }).languages(libre) }
        assertEquals(TranslationFailure.BAD_ANSWER, down.failure)
    }
}

class TranslationConfigTest {
    @Test
    fun anAddressKeepsItsBasePathAndLosesAFullEndpoint() {
        val libre = TranslationConfig(TranslationService.LIBRE)
        assertEquals("https://example.org/lt/translate", libre.copy(endpoint = "https://example.org/lt/").url().toString())
        assertEquals("https://example.org/lt/languages", libre.copy(endpoint = "https://example.org/lt/translate").url("languages").toString())
        assertNull(libre.copy(endpoint = "https://user:pw@example.org").url())
        assertNull(libre.copy(endpoint = "https://example.org/?a=1").url())
        assertNull(libre.copy(endpoint = "ftp://example.org").url())
        assertFalse(libre.isValid, "LibreTranslate has no address of its own")
        assertFalse(TranslationConfig(TranslationService.DEEPL).isValid, "DeepL needs a key")
        assertFalse(TranslationConfig(TranslationService.AI).isValid, "the AI needs setting up")
    }

    @Test
    fun theSettingReadsBackAsXtaKeptItAndOffWhenUnusable() {
        val ai = AiConfig()
        val config = TranslationConfig(TranslationService.LIBRE, "https://lt.example.org", "key", "de", ai)
        assertEquals(config, TranslationConfig.read(config.toJson(), "key", ai))
        assertEquals("""{"version":1,"provider":"libre","endpoint":"https://lt.example.org","target":"de"}""", config.toJson(), "no key in it")
        assertEquals(TranslationService.OFF, TranslationConfig.read("""{"version":1,"provider":"libre","endpoint":""}""", "", ai).service)
        assertEquals(TranslationService.OFF, TranslationConfig.read("nonsense", "", ai).service)
        assertEquals(TranslationService.AI, TranslationConfig.read("""{"version":1,"provider":"ai"}""", "", ai).service, "an AI choice waits for the AI's setup")
    }

    @Test
    fun aFingerprintNamesTheTextTheLanguageAndTheSettings() {
        val config = TranslationConfig(TranslationService.LIBRE, "https://lt.example.org")
        assertEquals(config.fingerprint("a", "de"), config.fingerprint("a", "de"))
        assertTrue(config.fingerprint("a", "de") != config.fingerprint("a", "en"))
        assertTrue(config.fingerprint("a", "de") != config.copy(endpoint = "https://other.example.org").fingerprint("a", "de"))
        assertFalse("key" in config.copy(apiKey = "key").fingerprint("a", "de"))
    }

    @Test
    fun eachServiceNamesALanguageItsOwnWay() {
        assertEquals("EN-US", TranslationLanguages.deepl("en"))
        assertEquals("ZH-HANT", TranslationLanguages.deepl("zh_Hant"))
        assertEquals("NB", TranslationLanguages.deepl("nb_NO"))
        assertEquals(listOf("zh-Hant", "zt", "zh-TW", "zh"), TranslationLanguages.libre("zh-TW"))
        assertEquals(listOf("pt-BR", "pb", "pt"), TranslationLanguages.libre("pt-BR"))
        assertEquals("Brazilian Portuguese", TranslationLanguages.name("pt-BR"))
    }
}

class TranslationPiecesTest {
    @Test
    fun joiningThePiecesGivesTheTextBack() {
        val text = "Eins. Zwei!\r\nhttps://x.com/a  drei \n\n…"
        val pieces = TranslationPieces.of(text)
        assertEquals(text, pieces.joinToString("") { it.text })
        assertEquals(listOf("Eins. Zwei!", "drei"), pieces.filter { it.translate }.map { it.text })
    }

    @Test
    fun aLongParagraphIsCutAfterASentenceAndNeverInsideAPair() {
        val sentence = "Das ist ein Satz. "
        val pieces = TranslationPieces.of(sentence.repeat(20), max = 100)
        assertTrue(pieces.all { it.text.length <= 100 })
        assertTrue(pieces.filter { it.translate }.all { it.text.endsWith(".") })
        val emoji = "😀".repeat(80)
        val cut = TranslationPieces.of(emoji, max = 51)
        assertEquals(emoji, cut.joinToString("") { it.text })
        assertTrue(cut.none { it.text.last().isHighSurrogate() })
    }
}

class KeptTranslationsTest {
    @Test
    fun theNewestAreKeptWithinTheirBoundsAndTheirWeek() = runBlocking {
        var now = 0L
        var file: String? = null
        val kept = KeptTranslations(load = { file }, save = { file = it }, maxEntries = 2, clock = { now })
        kept.write("a", "1", 100)
        kept.write("b", "2", 100)
        kept.read("a")
        kept.write("c", "3", 100)
        assertNull(kept.read("b"), "the one read least lately goes")
        assertEquals("1", kept.read("a"))
        val again = KeptTranslations(load = { file }, save = {}, clock = { now })
        assertEquals("3", again.read("c"), "read back from the file")
        now = 100
        assertNull(again.read("c"), "past its time")
        val damaged = KeptTranslations(load = { "{" }, save = {})
        assertNull(damaged.read("a"))
    }
}
