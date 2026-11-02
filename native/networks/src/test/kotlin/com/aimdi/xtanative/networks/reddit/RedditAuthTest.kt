package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.net.SocketException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/** Signing in, as XTA's reddit_auth_test and reddit_read_session_test hold it: read scopes only, and a session every read shares. */
class RedditAuthTest {
    private val url = RedditAuth.authorizeUrl(clientId = "my_id", state = "st4te")

    @Test
    fun theLoginAsksForACodeAgainstTheRegisteredApp() {
        assertEquals("www.reddit.com", url.host)
        assertEquals("/api/v1/authorize.compact", url.encodedPath)
        assertEquals("my_id", url.queryParameter("client_id"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(RedditAuth.REDIRECT_URI, url.queryParameter("redirect_uri"))
        assertEquals("st4te", url.queryParameter("state"))
        assertEquals("permanent", url.queryParameter("duration"), "or the reader would sign in again hourly")
    }

    @Test
    fun theLoginAsksForReadScopesOnly() {
        val scopes = url.queryParameter("scope")!!.split(',')

        assertTrue(scopes.containsAll(listOf("read", "identity")))
        listOf("submit", "vote", "edit", "subscribe", "save", "report").forEach { assertFalse(it in scopes, it) }
    }

    @Test
    fun theCodeRedditSentBackIsTakenOnlyWithTheStateItWasAskedWith() {
        assertEquals("abc123", RedditAuth.codeFrom("${RedditAuth.REDIRECT_URI}?state=st4te&code=abc123", expectedState = "st4te"))
        assertNull(RedditAuth.codeFrom("${RedditAuth.REDIRECT_URI}?state=somebody_else&code=abc123", expectedState = "st4te"), "no code can be injected")
        listOf("https://www.reddit.com/login", "https://www.reddit.com/api/v1/authorize.compact?client_id=my_id", "https://accounts.google.com/signin")
            .forEach { assertNull(RedditAuth.codeFrom(it, expectedState = "st4te"), it) }
    }

    @Test
    fun aRefusalIsRecognisedSoDecliningClosesQuietly() {
        val denied = "${RedditAuth.REDIRECT_URI}?state=st4te&error=access_denied"

        assertTrue(RedditAuth.deniedIn(denied))
        assertNull(RedditAuth.codeFrom(denied, expectedState = "st4te"))
        assertFalse(RedditAuth.deniedIn("https://www.reddit.com/login"))
    }

    @Test
    fun anOauthStateIsRandomAndSafeInAQuery() {
        val state = RedditAuth.oauthState()

        assertEquals(43, state.length, "32 bytes, base64url without padding")
        assertTrue(state.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(state, RedditAuth.oauthState())
    }

    @Test
    fun theCodeIsExchangedWithTheDocumentedGrantAndTheClientIdAsBasicAuth() = runBlocking {
        val fake = FakeReddit { page("""{"refresh_token":"refresh_me","access_token":"tok"}""") }
        val token = RedditAuth(fake).exchangeCode(clientId = "my_id", code = "abc123")

        val sent = fake.asked.single()
        assertEquals("refresh_me", token)
        assertEquals("https://www.reddit.com/api/v1/access_token", sent.url.toString())
        assertEquals("authorization_code", sent.form["grant_type"])
        assertEquals("abc123", sent.form["code"])
        assertEquals(RedditAuth.REDIRECT_URI, sent.form["redirect_uri"])
        assertEquals("Basic " + Base64.getEncoder().encodeToString("my_id:".toByteArray()), sent.headers["Authorization"], "an installed app has no secret")
        assertEquals(RedditClient.API_USER_AGENT, sent.headers["User-Agent"])
    }

    @Test
    fun aRefreshTokenBuysAnAccessTokenWithoutAskingTheReaderAgain() = runBlocking {
        val fake = FakeReddit { page("""{"access_token":"fresh","expires_in":3600}""") }

        assertEquals("fresh", RedditAuth(fake).accessToken(clientId = "my_id", refreshToken = "refresh_me"))
        assertEquals("refresh_token", fake.asked.single().form["grant_type"])
        assertEquals("refresh_me", fake.asked.single().form["refresh_token"])
    }

    @Test
    fun signingInStillNeedsAClientId() {
        assertFailsWith<IllegalArgumentException> { runBlocking { RedditAuth(FakeReddit { page("{}") }).exchangeCode(clientId = "  ", code = "abc123") } }
    }

    @Test
    fun aRejectedClientIdOrARevokedSessionIsUnauthorised() {
        val rejected = assertFailsWith<NetworkRequestException> {
            runBlocking { RedditAuth(FakeReddit { page("""{"error":401}""", 401) }).exchangeCode(clientId = "wrong", code = "abc123") }
        }
        val revoked = assertFailsWith<NetworkRequestException> {
            runBlocking { RedditAuth(FakeReddit { page("""{"error":"invalid_grant"}""", 400) }).accessToken(clientId = "id", refreshToken = "stale") }
        }

        assertEquals(401, rejected.code)
        assertEquals(401, revoked.code, "invalid_grant ends the session, whatever the status")
    }

    @Test
    fun anAnswerWithoutTheTokenOrNotJsonIsUnreadableNotACrash() {
        val partial = assertFailsWith<NetworkRequestException> {
            runBlocking { RedditAuth(FakeReddit { page("""{"access_token":"only_this"}""") }).exchangeCode(clientId = "my_id", code = "abc123") }
        }
        val blockPage = assertFailsWith<NetworkRequestException> {
            runBlocking { RedditAuth(FakeReddit { page("<html>blocked</html>") }).accessToken(clientId = "my_id", refreshToken = "r") }
        }

        assertEquals("No refresh_token in the token response", partial.message)
        assertEquals("The token endpoint answered with something that is not JSON", blockPage.message)
    }

    @Test
    fun theAccountNameIsReadWithTheAccessTokenAndIsNullWhenRedditDoesNotSay() = runBlocking {
        val fake = FakeReddit { page("""{"name":"spez"}""") }

        assertEquals("spez", RedditAuth(fake).accountName("tok"))
        assertEquals("https://oauth.reddit.com/api/v1/me", fake.asked.single().url.toString())
        assertEquals("Bearer tok", fake.asked.single().headers["Authorization"])
        assertNull(RedditAuth(FakeReddit { page("", 403) }).accountName("tok"))
        assertNull(RedditAuth(FakeReddit { throw SocketException("offline") }).accountName("tok"))
    }

    private fun minting(answer: () -> XResponse = { page("""{"access_token":"access_abc","expires_in":3600}""") }) =
        FakeReddit { answer() }

    @Test
    fun theAccountFreeRouteNeverMintsAToken() = runBlocking {
        val fake = minting()
        val resolved = RedditSignIn(RedditAuth(fake)).resolve(clientId = "cid", refreshToken = "refresh_me", preferPublic = true)

        assertEquals(ResolvedRedditSession(RedditSession(clientId = "cid", preferPublic = true)), resolved)
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun aRefreshTokenGivesTheSessionAnAccessToken() = runBlocking {
        val fake = minting()
        val resolved = RedditSignIn(RedditAuth(fake)).resolve(clientId = "cid", refreshToken = "refresh_me", preferPublic = false)

        assertEquals(RedditSession(clientId = "cid", userToken = "access_abc"), resolved.session)
        assertFalse(resolved.signedOut)
        assertEquals("www.reddit.com", fake.asked.single().url.host)
    }

    @Test
    fun withoutARefreshTokenTheClientIdIsKeptForAppOnlyReads() = runBlocking {
        val fake = minting()

        assertEquals(RedditSession(clientId = "cid"), RedditSignIn(RedditAuth(fake)).resolve("cid", null, false).session)
        assertEquals(RedditSession(clientId = ""), RedditSignIn(RedditAuth(fake)).resolve("", "refresh_me", false).session, "signing in needs a client id")
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot() = runBlocking {
        val dead = RedditSignIn(RedditAuth(minting { page("""{"error":"invalid_grant"}""", 401) })).resolve("cid", "stale", false)
        val offline = RedditSignIn(RedditAuth(minting { throw SocketException("offline") })).resolve("cid", "refresh_me", false)
        val throttled = RedditSignIn(RedditAuth(minting { page("", 429) })).resolve("cid", "refresh_me", false)

        assertEquals(ResolvedRedditSession(RedditSession(clientId = "cid"), signedOut = true), dead)
        assertEquals(ResolvedRedditSession(RedditSession(clientId = "cid")), offline, "being offline at the wrong moment must not sign anyone out")
        assertFalse(throttled.signedOut)
    }

    @Test
    fun oneAccessTokenServesEveryReadForFiftyMinutes() = runBlocking {
        var now = 0L
        var minted = 0
        val signIn = RedditSignIn(RedditAuth(FakeReddit { page("""{"access_token":"token-${++minted}"}""") }), clock = { now })

        val tokens = (1..3).map { async { signIn.resolve("cid", "refresh", false).session.userToken } }.awaitAll()
        assertEquals(listOf("token-1", "token-1", "token-1"), tokens, "reads asking at once share one mint")

        now += RedditSignIn.TOKEN_TTL_MILLIS
        assertEquals("token-2", signIn.resolve("cid", "refresh", false).session.userToken)
        assertEquals("token-3", signIn.resolve("cid", "another", false).session.userToken, "another sign-in is another token")
    }
}
