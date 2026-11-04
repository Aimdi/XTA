package com.aimdi.xtanative.plugins.deepmarks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.deepmarks.DeepmarksClient
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** Save to Deepmarks and Test connection, said in XTA's words (deepmarks_save.dart, deepmarks_settings_screen.dart). */
class DeepmarksSavesTest {
    // NIP-19's published example key and its public key, as XTA's tests sign with: never anyone's own.
    private val secretHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
    private val publicHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

    /** The Deepmarks API, answering each request with [answer]; what it was asked is kept. */
    private class Api(private val answer: (HttpUrl) -> XResponse) : XHttp {
        val asked = mutableListOf<Pair<HttpUrl, String?>>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = reply(url, null)

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String) = reply(url, body)

        private fun reply(url: HttpUrl, body: String?): XResponse {
            asked += url to body
            return answer(url)
        }
    }

    private fun fixture(name: String) = javaClass.getResource("/deepmarks/$name")!!.readText()

    private fun json(body: String, code: Int = 200) = XResponse(code, body, mapOf("content-type" to listOf("application/json")))

    private val configured = Settings().with(DeepmarksPrefs.apiKey, "dmk_live_x").with(DeepmarksPrefs.secretKey, secretHex)

    private fun save(api: Api, settings: Settings = configured) = runBlocking {
        DeepmarksSaves.save(DeepmarksClient(api), settings, "https://x.com/jack/status/20", "Jack (@jack): just setting up my twttr", Instant.parse("2026-07-25T00:00:00Z"))
    }

    private fun test(api: Api, apiKey: String = "dmk_live_x", secret: String = secretHex) =
        runBlocking { DeepmarksSaves.test(DeepmarksClient(api), "", apiKey, secret) }

    @Test
    fun theBookmarkIsSignedHereAndOnlyTheFinishedEventIsSent() {
        val api = Api { json(fixture("publish.json")) }
        assertEquals(Strings.saved, save(api))
        val (url, body) = api.asked.single()
        assertEquals("https://api.deepmarks.org/api/v1/bookmarks", url.toString())
        val event = J.parse(body!!)
        assertEquals(publicHex, event["pubkey"].string)
        assertEquals(listOf("d", "https://x.com/jack/status/20"), event["tags"][0].list.map { it.string })
        assertEquals(listOf("title", "Jack (@jack): just setting up my twttr"), event["tags"][1].list.map { it.string })
        assertFalse(secretHex in body, "the signing key never leaves the device")
    }

    @Test
    fun anEventNoRelayTookSaysSo() {
        assertEquals(Strings.saved_no_relay, save(Api { json(fixture("publish_no_relay.json")) }))
    }

    @Test
    fun withoutBothKeysNothingIsSentAndTheReaderIsToldWhatIsMissing() {
        val api = Api { error("nothing should be asked") }
        assertEquals(Strings.not_configured, save(api, configured.with(DeepmarksPrefs.apiKey, "")))
        assertEquals(Strings.not_configured, save(api, configured.with(DeepmarksPrefs.secretKey, " ")))
        assertEquals(Strings.error_secret_key, save(api, configured.with(DeepmarksPrefs.secretKey, "nsec1notakey")))
        assertTrue(api.asked.isEmpty())
    }

    @Test
    fun eachRefusalSaysWhatToDoAboutIt() {
        val cases = mapOf(
            400 to Strings.error_rejected,
            401 to Strings.error_unauthorized,
            402 to Strings.error_lifetime,
            403 to Strings.error_key_mismatch,
            429 to Strings.error_rate_limited,
            503 to Strings.error_server,
        )
        cases.forEach { (code, words) -> assertEquals(words, save(Api { json("{}", code) }), "HTTP $code") }
        assertEquals(Strings.error_network, save(Api { throw IOException("offline") }))
    }

    @Test
    fun testConnectionChecksTheKeysAreOneAccounts() {
        assertEquals(ConnectionStatus.OK to Strings.test_ok, test(Api { json(fixture("bookmarks.json")) }))
        val other = """{"bookmarks":[{"id":"e2","pubkey":"${"ab".repeat(32)}"}]}"""
        assertEquals(ConnectionStatus.FAILED to Strings.error_key_mismatch, test(Api { json(other) }))
        assertEquals(ConnectionStatus.WARNING to Strings.test_ok_unverified, test(Api { json("""{"bookmarks":[]}""") }), "no public bookmark to tell by")
        assertEquals(ConnectionStatus.OK to Strings.test_ok, test(Api { json(other) }, secret = ""), "no signing key to compare yet")
    }

    @Test
    fun testConnectionSaysWhatIsWrongBeforeAskingWhereItCan() {
        val api = Api { error("nothing should be asked") }
        assertEquals(ConnectionStatus.FAILED to Strings.error_secret_key, test(api, secret = "not a key"))
        assertEquals(ConnectionStatus.FAILED to Strings.not_configured, test(api, apiKey = ""))
        assertTrue(api.asked.isEmpty())
        assertEquals(ConnectionStatus.FAILED to Strings.error_unauthorized, test(Api { json("{}", 401) }))
    }

    @Test
    fun theSigningKeyNamesWhomItSignsAs() {
        assertEquals(publicHex, DeepmarksSaves.publicKey(" $secretHex "))
        assertNull(DeepmarksSaves.publicKey(""))
        assertNull(DeepmarksSaves.publicKey("nsec1notakey"))
    }

    @Test
    fun theKeysAreTheReadersOwnSecretsUnderThePluginsKeys() {
        val prefix = PluginSettings.prefixOf("deepmarks")
        listOf(DeepmarksPrefs.apiKey, DeepmarksPrefs.secretKey).forEach { pref ->
            assertTrue(pref.key.startsWith(prefix), "uninstalling erases ${pref.key}")
            assertTrue(SecretPrefs.isSecret(pref.key))
        }
        val exported = configured.with(DeepmarksPrefs.apiBase, "http://localhost:4000").exported()!!
        assertFalse(DeepmarksPrefs.apiKey.key in exported || DeepmarksPrefs.secretKey.key in exported, "no export or backup carries a key")
        assertTrue(DeepmarksPrefs.apiBase.key in exported)
    }
}
