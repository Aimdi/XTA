package com.aimdi.xtanative.core.x.http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl

class CredentialGuardTest {
    @Test
    fun aHopStaysOnTheSiteWithinItsDomainAndNeverDownToHttp() {
        assertTrue(CredentialGuard.sameSite("x.com", true, "x.com", true))
        assertTrue(CredentialGuard.sameSite("x.com", true, "api.x.com", true))
        assertTrue(CredentialGuard.sameSite("api.x.com", true, "x.com", true))
        assertFalse(CredentialGuard.sameSite("x.com", true, "elsewhere.example", true))
        assertFalse(CredentialGuard.sameSite("x.com", true, "x.com", false), "https down to http")
        assertFalse(CredentialGuard.sameSite("x.com", true, "evilx.com", true))
    }

    @Test
    fun aRedirectToAnotherHostArrivesWithoutTheCookie() = runBlocking {
        val seen = mutableListOf<String?>()
        val elsewhere = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                seen += exchange.requestHeaders.getFirst("Cookie")
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            start()
        }
        val site = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                seen += exchange.requestHeaders.getFirst("Cookie")
                // Another host name for the same machine: to the client, another site.
                exchange.responseHeaders.add("Location", "http://localhost:${elsewhere.address.port}/landed")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }
        try {
            val answer = OkHttpXHttp().get("http://127.0.0.1:${site.address.port}/start".toHttpUrl(), mapOf("Cookie" to "auth_token=secret"), followRedirects = true)
            assertEquals(200, answer.code)
            assertEquals(listOf("auth_token=secret", null), seen, "the site it was sent to has it; the one it was sent on to does not")
        } finally {
            site.stop(0)
            elsewhere.stop(0)
        }
    }
}
