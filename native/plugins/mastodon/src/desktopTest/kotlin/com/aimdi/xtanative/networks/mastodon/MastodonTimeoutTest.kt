package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** How long a walk waits for an instance: its own limit, kept even when OkHttp's blocking call cannot be cut short. */
class MastodonTimeoutTest {
    /**
     * Walk B reads an account (lookup, then statuses) on its only instance,
     * allowed a second; walk A meanwhile moves on to its fallback instance,
     * allowed 100 ms. XTA kept the limit in one field of the client, so B's
     * statuses, asked after A had moved on, got A's 100 ms and B failed.
     */
    @Test
    fun aWalksTimeLimitIsItsOwnWhereXtaSharedOneAcrossConcurrentReads() = runBlocking {
        val fake = FakeMastodon {
            when {
                it.host == "a-miss.social" -> answer(404)
                it.host == "a-slow.social" -> {
                    delay(2_000)
                    ok(accountJson("1", "x"))
                }
                it.path == "/api/v1/accounts/lookup" -> {
                    delay(50)
                    ok(accountJson("2", "reader", "reader@b.example"))
                }
                else -> {
                    delay(300)
                    ok(pageJson(3))
                }
            }
        }
        val client = MastodonClient(fake, timeoutMillis = 1_000, fallbackTimeoutMillis = 100)
        val b = async { client.accountPostsAnywhere(listOf("https://b-one.social"), "reader@b.example") }
        delay(20)
        val a = async { runCatching { client.lookupAnywhere(listOf("https://a-miss.social", "https://a-slow.social"), "x@a.example") } }

        assertEquals(3, b.await().size)
        assertEquals(MastodonFailure.NETWORK, a.await().exceptionOrNull()?.let(MastodonFailure::of), "A's fallback instance ran out of its time")
    }

    @Test
    fun aHangingInstanceIsLeftBehindOverRealHttp() = runBlocking {
        val profile = """{"id":"9","username":"reader","acct":"reader@x.example","display_name":"Reader","note":"","url":"https://x.example/@reader"}"""
        val slow = server {
            Thread.sleep(1_500)
            respond(it, profile)
        }
        val fast = server { respond(it, profile) }
        try {
            val client = MastodonClient(OkHttpXHttp(), timeoutMillis = 200, fallbackTimeoutMillis = 2_000)
            val started = System.nanoTime()
            val found = client.lookupAnywhere(listOf("http://127.0.0.1:${slow.address.port}", "http://127.0.0.1:${fast.address.port}"), "reader@x.example")
            val waited = (System.nanoTime() - started) / 1_000_000
            assertEquals("reader@x.example", found.acct)
            assertTrue(waited < 1_200, "the walk waited $waited ms for the hanging instance")
        } finally {
            slow.stop(0)
            fast.stop(0)
        }
    }

    private fun server(handle: (HttpExchange) -> Unit): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            try {
                handle(exchange)
            } finally {
                exchange.close()
            }
        }
        executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
        start()
    }

    private fun respond(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
