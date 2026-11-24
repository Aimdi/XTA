package com.aimdi.xtanative.core.net

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.sun.net.httpserver.HttpServer
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Requests through stand-in proxies on loopback, to hosts that only the proxy ever "reaches". */
class ProxyRoutingTest {
    @AfterTest
    fun back() = AppProxy.use(AppProxy.PLATFORM)

    /** One request a stand-in proxy got: its first line and headers. */
    private data class Seen(val line: String, val headers: Map<String, String>)

    /** An HTTP proxy that wants [user]:[password] and answers each request with [answer] once logged in. */
    private class FakeHttpProxy(private val user: String, private val password: String, private val answer: (Seen) -> String) : AutoCloseable {
        val seen: MutableList<Seen> = Collections.synchronizedList(mutableListOf())
        private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        val port get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { serve(socket) }
                }
            }
        }

        private fun serve(socket: Socket) = socket.use {
            val input = it.getInputStream()
            while (true) {
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val request = Seen(lines[0], lines.drop(1).filter { ':' in it }.associate { l -> l.substringBefore(':').lowercase() to l.substringAfter(':').trim() })
                seen += request
                val login = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
                val reply = if (request.headers["proxy-authorization"] != login) {
                    "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"test\"\r\nContent-Length: 0\r\n\r\n"
                } else {
                    answer(request)
                }
                it.getOutputStream().write(reply.toByteArray())
                it.getOutputStream().flush()
                if (request.line.startsWith("CONNECT") && reply.startsWith("HTTP/1.1 200")) return
            }
        }

        override fun close() = server.close()
    }

    @Test
    fun aPlainRequestGoesThroughTheProxyWhichGetsItsLoginOnlyWhenItAsks() = runBlocking {
        FakeHttpProxy("me", "secret") { "HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\nvia proxy" }.use { proxy ->
            AppProxy.use(ManualProxyPolicy(ProxyRoute.Via(ProxyKind.HTTP, "127.0.0.1", proxy.port, ProxyCredentials("me", "secret"))))
            val answer = OkHttpXHttp().get("http://reader.test/page".toHttpUrl(), emptyMap(), followRedirects = false)
            assertEquals(200, answer.code)
            assertEquals("via proxy", answer.body)
            assertEquals(listOf("GET http://reader.test/page HTTP/1.1"), proxy.seen.map { it.line }.distinct())
            assertNull(proxy.seen.first().headers["proxy-authorization"], "no login before the proxy asked")
            assertTrue(proxy.seen.last().headers["proxy-authorization"]!!.startsWith("Basic "))
        }
    }

    @Test
    fun anHttpsRequestOpensATunnelWithTheLoginOnTheConnectAlone() {
        FakeHttpProxy("me", "secret") { "HTTP/1.1 200 Connection established\r\n\r\n" }.use { proxy ->
            AppProxy.use(ManualProxyPolicy(ProxyRoute.Via(ProxyKind.HTTP, "127.0.0.1", proxy.port, ProxyCredentials("me", "secret"))))
            // The stand-in closes the tunnel at once: the TLS handshake fails, after the proxy saw the CONNECT.
            assertFailsWith<IOException> { OkHttpXHttp.defaultClient.newCall(Request.Builder().url("https://reader.test/").build()).execute().close() }
            assertEquals("CONNECT reader.test:443 HTTP/1.1", proxy.seen.last().line)
            assertTrue(proxy.seen.last().headers["proxy-authorization"]!!.startsWith("Basic "))
        }
    }

    @Test
    fun aRedirectSentStraightArrivesWithoutTheProxysLogin() = runBlocking {
        val straight = mutableListOf<String?>()
        val local = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                straight += exchange.requestHeaders.getFirst("Proxy-Authorization")
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            start()
        }
        try {
            FakeHttpProxy("me", "secret") { "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${local.address.port}/landed\r\nContent-Length: 0\r\n\r\n" }.use { proxy ->
                AppProxy.use(ManualProxyPolicy(ProxyRoute.Via(ProxyKind.HTTP, "127.0.0.1", proxy.port, ProxyCredentials("me", "secret"))))
                val answer = OkHttpXHttp().get("http://reader.test/start".toHttpUrl(), emptyMap(), followRedirects = true)
                assertEquals(200, answer.code)
                assertEquals(listOf<String?>(null), straight, "loopback goes straight, and the proxy's login stays with the proxy")
            }
        } finally {
            local.stop(0)
        }
    }

    @Test
    fun aProxyTheAppCannotUseFailsTheRequestInsteadOfGoingStraight() {
        AppProxy.use(ManualProxyPolicy(ProxyRoute.Unusable))
        val error = assertFailsWith<IOException> { OkHttpXHttp.defaultClient.newCall(Request.Builder().url("http://reader.test/").build()).execute().close() }
        assertTrue("cannot be used" in error.message.orEmpty(), error.toString())
    }

    @Test
    fun socks5SendsTheHostNameToTheProxyWithItsLogin() {
        val got = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                input.readUnsignedByte() // version
                input.readFully(ByteArray(input.readUnsignedByte())) // methods
                output.write(byteArrayOf(5, 2)) // user and password
                input.readUnsignedByte()
                val user = String(ByteArray(input.readUnsignedByte()).also(input::readFully))
                val password = String(ByteArray(input.readUnsignedByte()).also(input::readFully))
                got += "login $user:$password"
                output.write(byteArrayOf(1, 0))
                input.readUnsignedByte(); input.readUnsignedByte(); input.readUnsignedByte()
                val type = input.readUnsignedByte()
                val host = String(ByteArray(input.readUnsignedByte()).also(input::readFully))
                val port = input.readUnsignedShort()
                got += "connect $type $host:$port"
                output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                readHead(input)?.let { got += it.substringBefore("\r\n") }
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nsocks".toByteArray())
                output.flush()
            }
        }
        val before = runCatching { Authenticator::class.java.getMethod("getDefault").invoke(null) as Authenticator? }.getOrNull()
        Authenticator.setDefault(AppProxy.socksAuthenticator)
        try {
            AppProxy.use(ManualProxyPolicy(ProxyRoute.Via(ProxyKind.SOCKS, "127.0.0.1", server.localPort, ProxyCredentials("me", "pw"))))
            val body = OkHttpXHttp.defaultClient.newCall(Request.Builder().url("http://reader.test:8080/x").build()).execute().use { it.body!!.string() }
            assertEquals("socks", body)
            assertEquals(listOf("login me:pw", "connect 3 reader.test:8080", "GET /x HTTP/1.1"), got.toList())
        } finally {
            Authenticator.setDefault(before)
            server.close()
        }
    }

    @Test
    fun theSocksLoginIsEmptyForAProxyWithoutOneNeverTheAccountName() {
        AppProxy.use(ManualProxyPolicy(ProxyRoute.Via(ProxyKind.SOCKS, "127.0.0.1", 1)))
        AppProxy.route("http://reader.test/")
        val ours = AppProxy.socksAuthenticator.requestPasswordAuthenticationInstance("127.0.0.1", null, 1, "SOCKS5", "SOCKS authentication", null, null, Authenticator.RequestorType.SERVER)
        assertEquals("", ours.userName)
        assertEquals(0, ours.password.size)
        assertNull(AppProxy.socksAuthenticator.requestPasswordAuthenticationInstance("site.test", null, 443, "https", "", "Basic", null, Authenticator.RequestorType.SERVER))
    }

    companion object {
        /** The head of an HTTP request (up to the blank line), or null when the stream ended. */
        fun readHead(input: InputStream): String? {
            val bytes = StringBuilder()
            while (!bytes.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return null
                bytes.append(b.toChar())
            }
            return bytes.toString()
        }
    }
}
