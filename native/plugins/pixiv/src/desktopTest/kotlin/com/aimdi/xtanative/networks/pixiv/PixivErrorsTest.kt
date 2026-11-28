package com.aimdi.xtanative.networks.pixiv

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException

/** What a failed read is called: a refusal without a token is never a rejected token, and a bad answer never "offline". */
class PixivErrorsTest {
    @Test
    fun aRequestWithoutATokenThatPixivRefusesIsNotTheAccountsFault(): Unit = runBlocking {
        for (status in listOf(401, 403)) {
            val http = FakePixivHttp { answer("<html>blocked</html>", status) }
            val signedOut = MemoryPixivCredentials(PixivCredentials.NONE)
            val error = assertFailsWith<PixivAnonymousRefusal> { PixivDiscoveryApi(pixiv(http, signedOut)).walkthrough() }
            assertEquals(status, error.code)
            assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error), "HTTP $status")
            assertTrue(http.tokenCalls.isEmpty())
        }
    }

    @Test
    fun aSignedInReadPixivRefusesStillReadsAsTheAccount(): Unit = runBlocking {
        val http = FakePixivHttp { answer("{}", 403) }
        val error = assertFailsWith<com.aimdi.xtanative.networks.NetworkRequestException> { pixiv(http).following() }
        assertEquals(PixivFailure.UNAUTHORIZED, PixivFailure.of(error))
    }

    @Test
    fun onlyAMissingAnswerIsTheNetworksAParseBugIsABadAnswer() {
        assertEquals(PixivFailure.NETWORK, PixivFailure.of(UnknownHostException("app-api.pixiv.net")))
        assertEquals(PixivFailure.NETWORK, PixivFailure.of(SocketTimeoutException("no answer")))
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(SerializationException("unexpected token")))
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(IllegalStateException("no field")))
        assertEquals(PixivFailure.NOT_SIGNED_IN, PixivFailure.of(PixivNoAccountException()))
    }
}
