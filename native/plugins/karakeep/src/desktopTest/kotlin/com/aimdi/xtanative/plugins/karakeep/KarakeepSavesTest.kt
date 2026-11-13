package com.aimdi.xtanative.plugins.karakeep

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.karakeep.KarakeepClient
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** Save to Karakeep and Test connection, said in XTA's words (karakeep_save.dart, karakeep_settings_screen.dart). */
class KarakeepSavesTest {
    /** The reader's Karakeep, answering each request with [answer]; what it was asked is kept. */
    private class Server(private val answer: (HttpUrl) -> XResponse) : XHttp {
        val asked = mutableListOf<Pair<HttpUrl, String?>>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = reply(url, null)

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String) = reply(url, body)

        private fun reply(url: HttpUrl, body: String?): XResponse {
            asked += url to body
            return answer(url)
        }
    }

    private fun json(body: String, code: Int) = XResponse(code, body, mapOf("content-type" to listOf("application/json")))

    private val configured = Settings().with(KarakeepPrefs.serverUrl, "karakeep.example.com").with(KarakeepPrefs.apiKey, "kk")

    private fun save(server: Server, settings: Settings = configured) =
        runBlocking { KarakeepSaves.save(KarakeepClient(server), settings, "https://x.com/jack/status/20", "Jack (@jack): just setting up my twttr") }

    @Test
    fun aNewBookmarkSaysSavedAndOneKarakeepHadSaysAlreadyThere() {
        val created = Server { json("""{"id":"b1"}""", 201) }
        assertEquals(Strings.saved, save(created))
        val (url, body) = created.asked.single()
        assertEquals("https://karakeep.example.com/api/v1/bookmarks", url.toString())
        assertEquals("https://x.com/jack/status/20", J.parse(body!!)["url"].string)
        assertEquals("Jack (@jack): just setting up my twttr", J.parse(body)["title"].string)

        assertEquals(Strings.already_saved, save(Server { json("""{"id":"b1"}""", 200) }))
    }

    @Test
    fun withoutAServerOrAKeyNothingIsSentAndTheReaderIsToldWhatIsMissing() {
        val server = Server { error("nothing should be asked") }
        assertEquals(Strings.not_configured, save(server, Settings()))
        assertEquals(Strings.not_configured, save(server, configured.with(KarakeepPrefs.apiKey, "  ")))
        assertTrue(server.asked.isEmpty())
    }

    @Test
    fun eachFailureSaysWhatWentWrong() {
        assertEquals(Strings.error_unauthorized, save(Server { json("{}", 401) }))
        assertEquals(Strings.error_server_url, save(Server { json("{}", 404) }))
        assertEquals(Strings.error_server_url, save(Server { XResponse(200, "<html>router</html>") }), "a page is no bookmark")
        assertEquals(Strings.error_generic, save(Server { json("""{"error":"no"}""", 500) }))
        assertEquals(Strings.error_network, save(Server { throw IOException("no route to host") }))
    }

    @Test
    fun testConnectionSaysConnectedOrWhyNot(): Unit = runBlocking {
        val ok = Server { json("""{"bookmarks":[]}""", 200) }
        assertEquals(ConnectionStatus.OK to Strings.test_ok, KarakeepSaves.test(KarakeepClient(ok), "karakeep.example.com", "kk"))
        assertEquals("/api/v1/bookmarks", ok.asked.single().first.encodedPath)

        assertEquals(ConnectionStatus.FAILED to Strings.not_configured, KarakeepSaves.test(KarakeepClient(ok), "", "kk"))
        assertEquals(ConnectionStatus.FAILED to Strings.error_unauthorized, KarakeepSaves.test(KarakeepClient(Server { json("{}", 403) }), "karakeep.example.com", "kk"))
    }

    @Test
    fun theKeyIsTheReadersOwnSecretUnderThePluginsKeys() {
        val prefix = PluginSettings.prefixOf("karakeep")
        assertTrue(KarakeepPrefs.apiKey.key.startsWith(prefix) && KarakeepPrefs.serverUrl.key.startsWith(prefix), "uninstalling erases what is under the prefix")
        assertTrue(SecretPrefs.isSecret(KarakeepPrefs.apiKey.key))
        val exported = configured.exported()!!
        assertFalse(KarakeepPrefs.apiKey.key in exported, "no export or backup carries the key")
        assertTrue(KarakeepPrefs.serverUrl.key in exported, "the address travels, as XTA's backup carries it")
    }
}
