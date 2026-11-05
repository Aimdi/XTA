package com.aimdi.xtanative.networks.reddit

import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The deadline every request shares, as XTA's `RedditClient._send` holds it:
 * a Reddit that stops answering must not hold a screen indefinitely.
 */
class RedditTransportTest {
    private fun transport(fake: FakeReddit, timeoutMillis: Long = 50) =
        RedditTransport(fake, RedditClient.WEB_USER_AGENT, clock = { 0L }, timeoutMillis = timeoutMillis)

    @Test
    fun aStalledReadIsANetworkFailureAndARetrySucceeds() = runBlocking {
        var calls = 0
        val fake = FakeReddit {
            if (++calls == 1) delay(5_000)
            page("""{"kind":"Listing","data":{"children":[]}}""")
        }
        val transport = transport(fake)
        val url = "https://www.reddit.com/r/dartlang/hot.json".toHttpUrl()

        assertFailsWith<SocketTimeoutException> { transport.read(url) }
        assertEquals(200, transport.read(url).code, "the deadline must release the caller, not the connection")
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun aStalledAppOnlyTokenIsANetworkFailureAndARetrySucceeds() = runBlocking {
        var calls = 0
        val fake = FakeReddit {
            if (++calls == 1) delay(5_000)
            page(tokenBody())
        }
        val transport = transport(fake)

        assertFailsWith<SocketTimeoutException> { transport.authorize("cid") }
        assertEquals("tok_123", transport.authorize("cid"), "a timed-out mint must not be cached")
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun aStalledFormPostIsANetworkFailureToo() = runBlocking {
        val fake = FakeReddit { delay(5_000); page("") }
        val transport = transport(fake)

        assertFailsWith<SocketTimeoutException> {
            transport.submit("https://www.reddit.com/over18".toHttpUrl(), mapOf("over18" to "yes"))
        }
        assertEquals(1, fake.asked.size, "the form was sent once, then given up on")
    }

    /**
     * OkHttp blocks inside [com.aimdi.xtanative.core.x.http.XHttp], so the
     * deadline has to end the reader's wait even when the call itself cannot
     * be cancelled; this fake sleeps like OkHttp does rather than delaying
     * cooperatively.
     */
    @Test
    fun aBlockingAnswerIsGivenUpOnAtTheDeadline() = runBlocking {
        val transport = transport(FakeReddit { Thread.sleep(2_000); page("") })
        val started = System.currentTimeMillis()

        assertFailsWith<SocketTimeoutException> {
            transport.read("https://www.reddit.com/r/dartlang/hot.json".toHttpUrl())
        }
        assertTrue(System.currentTimeMillis() - started < 1_000, "the reader must stop waiting even when the call itself blocks")
    }
}
