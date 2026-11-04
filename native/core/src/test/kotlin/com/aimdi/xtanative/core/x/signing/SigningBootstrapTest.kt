package com.aimdi.xtanative.core.x.signing

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl

/** The bootstrap paths of test/client_transaction_bootstrap_test.dart, against a stand-in for x.com. */
class SigningBootstrapTest {
    private val vector = Json.parseToJsonElement(javaClass.getResource("/signer_vectors.json")!!.readText())
        .jsonArray.first().jsonObject
    private val page = vector["html"]!!.jsonPrimitive.content
    private val script = vector["js"]!!.jsonPrimitive.content
    private val bundle = "https://abs.twimg.com/responsive-web/client-web/ondemand.s.abcdef1234567890a.js"

    private class FakeX(val answer: (HttpUrl, Map<String, String>) -> XResponse) : XHttp {
        val asked = ArrayList<String>()
        val cookies = ArrayList<String?>()
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            asked.add(url.toString())
            if (url.host == "x.com") cookies.add(headers["Cookie"])
            return answer(url, headers)
        }
    }

    @Test
    fun aHomePageWithItsSignerDerivesFromTwoRequests() = runBlocking {
        val x = FakeX { url, _ -> if (url.host == "x.com") XResponse(200, page) else XResponse(200, script) }
        val signer = SigningBootstrap(x).derive("auth_token=a; ct0=b")
        assertEquals(listOf("https://x.com/home", bundle), x.asked)
        assertEquals(listOf<String?>("auth_token=a; ct0=b"), x.cookies)
        assertEquals(48, signer.keyBytes.size)
    }

    @Test
    fun aHomePageWithoutTheKeyFallsBackToTheSearchShell() = runBlocking {
        val x = FakeX { url, _ ->
            when (url.encodedPath) {
                "/home" -> XResponse(200, "<html><body>logged out</body></html>")
                "/search" -> XResponse(200, page)
                else -> XResponse(200, script)
            }
        }
        SigningBootstrap(x).derive(null)
        assertEquals(listOf("https://x.com/home", "https://x.com/search?q=AI&f=live", bundle), x.asked)
    }

    @Test
    fun publicRoutesAnswering403StopAtTheSignInPageWithoutTheSession() = runBlocking {
        val x = FakeX { url, _ ->
            when (url.encodedPath) {
                "/home", "/search" -> XResponse(403, "challenge")
                "/i/flow/login" -> XResponse(200, page)
                else -> XResponse(200, script)
            }
        }
        SigningBootstrap(x).derive("auth_token=a")
        assertEquals(listOf("https://x.com/home", "https://x.com/search?q=AI&f=live", "https://x.com/i/flow/login", bundle), x.asked)
        assertEquals(listOf("auth_token=a", "auth_token=a", null), x.cookies)
    }

    @Test
    fun theSignInPageSignerIsFoundThroughTheChunkMapOfTheRuntimeItLoads() = runBlocking {
        // As X answered CI's runners on 3 November 2026: /home and /search refused,
        // and a sign-in page that names the signer only in its webpack runtime.
        val runtime = "https://abs.twimg.com/responsive-web/client-web/runtime.abc123.js"
        val signInPage = page.replace("<script src=\"$bundle\"></script>", "<script src=\"$runtime\"></script>")
        val x = FakeX { url, _ ->
            when (url.toString()) {
                runtime -> XResponse(200, """(()=>{var n={20113:"ondemand.s",3:"main"},h={3:"9f8e7d",20113:"abcdef1234567890"};})();""")
                bundle -> XResponse(200, script)
                else -> if (url.encodedPath == "/i/flow/login") XResponse(200, signInPage) else XResponse(403, "challenge")
            }
        }
        SigningBootstrap(x).derive(null)
        assertEquals(listOf("https://x.com/home", "https://x.com/search?q=AI&f=live", "https://x.com/i/flow/login", runtime, bundle), x.asked)
    }

    @Test
    fun aFailedBootstrapSaysWhatEachPageLacked() {
        val keyOnly = "<html><head><title>X</title>" +
            "<meta name=\"twitter-site-verification\" content=\"${java.util.Base64.getEncoder().encodeToString(ByteArray(48))}\">" +
            "</head></html>"
        val x = FakeX { url, _ ->
            when (url.encodedPath) {
                "/home" -> XResponse(403, "challenge")
                "/search" -> XResponse(200, "<html><head><title>Just a moment</title></head></html>")
                else -> XResponse(200, keyOnly)
            }
        }
        val failure = assertFailsWith<SigningUnavailableException> { runBlocking { SigningBootstrap(x).derive(null) } }
        val message = failure.message.orEmpty()
        listOf("/home: HTTP 403", "/search: no verification key", "\"Just a moment\"", "/i/flow/login: 0 loading frames").forEach {
            assertTrue(it in message, "$it in $message")
        }
    }

    @Test
    fun aRedirectAwayFromXIsRefused() {
        val x = FakeX { _, _ -> XResponse(302, "", mapOf("Location" to listOf("https://evil.example/home"))) }
        assertFailsWith<SigningUnavailableException> { runBlocking { SigningBootstrap(x).derive(null) } }
    }

    @Test
    fun theSignerIsFoundThroughItsImporterWithoutEvaluatingScripts() = runBlocking {
        val entry = "https://abs.twimg.com/responsive-web/client-web/main.abc123.js"
        val importer = "https://abs.twimg.com/responsive-web/client-web/startup-dynamic.def456.js"
        val signerUrl = "https://abs.twimg.com/responsive-web/client-web/sign.o.ghi789.js"
        val pageWithoutBundle = page.replace("<script src=\"$bundle\"></script>", "<script src=\"$entry\"></script>")
        val x = FakeX { url, _ ->
            when (url.toString()) {
                entry -> XResponse(200, "import(\"./startup-dynamic.def456.js\");")
                importer -> XResponse(200, "const s = \"./sign.o.ghi789.js\";")
                signerUrl -> XResponse(200, script)
                else -> XResponse(200, pageWithoutBundle)
            }
        }
        SigningBootstrap(x).derive(null)
        assertEquals(listOf("https://x.com/home", entry, importer, signerUrl), x.asked)
    }
}
