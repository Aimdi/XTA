package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivAuthTest {
    @Test
    fun pkceMakesAVerifierAndItsS256Challenge() {
        val pkce = PixivLogin.pkce()
        assertTrue(pkce.verifier.length >= 32)
        assertFalse('=' in pkce.challenge)
        val digest = MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray())
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), pkce.challenge)
        assertNotEquals(pkce.verifier, PixivLogin.pkce().verifier, "each attempt has its own")
    }

    @Test
    fun theLoginPageCarriesThePkceChallenge() {
        val url = PixivLogin.loginUrl("abc123")
        assertEquals("app-api.pixiv.net" to "/web/v1/login", url.host to url.encodedPath)
        assertEquals(listOf("abc123", "S256", "pixiv-android"), listOf("code_challenge", "code_challenge_method", "client").map(url::queryParameter))
    }

    @Test
    fun theCodeComesFromPixivsCallbackOrItsAppScheme() {
        assertEquals("auth-code-1", PixivLogin.codeFrom("${PixivLogin.REDIRECT_URI}?code=auth-code-1&via=login"))
        assertEquals("auth-code-2", PixivLogin.codeFrom("pixiv://account?code=auth-code-2"))
        assertEquals("auth-code-3", PixivLogin.codeFrom("pixiv://account/login?code=auth-code-3"))
        assertNull(PixivLogin.codeFrom("https://www.pixiv.net/"))
        assertNull(PixivLogin.codeFrom("https://example.com/?code=x"))
        assertNull(PixivLogin.codeFrom(PixivLogin.REDIRECT_URI), "no code")
    }

    @Test
    fun aRefusedSignInIsReadFromTheRedirect() {
        assertTrue(PixivLogin.deniedIn("${PixivLogin.REDIRECT_URI}?error=access_denied"))
        assertTrue(PixivLogin.deniedIn("pixiv://account/login?error=access_denied"))
        assertFalse(PixivLogin.deniedIn("pixiv://account/login?code=x"))
        assertFalse(PixivLogin.deniedIn("https://www.pixiv.net/?error=x"))
    }

    @Test
    fun theCodeExchangeIsSignedLikeEveryTokenRequestWhichXtaLeftOut() = runBlocking {
        val http = FakePixivHttp {
            tokenAnswer("access-new", "refresh-new", """{"id": "99", "name": "Artist", "account": "artist", "is_premium": true}""")
        }
        val tokens = pixiv(http, MemoryPixivCredentials()).exchangeCode("the-code", "verifier-xyz")

        val call = http.asked.single()
        assertEquals(PixivLogin.TOKEN_URL, call.url.toString())
        assertEquals("authorization_code", call.form["grant_type"])
        assertEquals("the-code" to "verifier-xyz", call.form["code"] to call.form["code_verifier"])
        assertEquals(PixivLogin.REDIRECT_URI, call.form["redirect_uri"])
        assertEquals(PixivApp().clientId, call.form["client_id"])
        assertTrue(call.body.orEmpty().contains("redirect_uri=https%3A%2F%2Fapp-api.pixiv.net"), "the form is encoded")
        assertEquals("2026-08-06T01:02:03+00:00", call.headers["X-Client-Time"])
        assertEquals(64 / 2, call.headers["X-Client-Hash"].orEmpty().length, "an MD5 in hex")
        assertEquals(listOf("access-new", "refresh-new"), listOf(tokens.accessToken, tokens.refreshToken))
        assertEquals(3600L, tokens.expiresInSeconds)
        assertEquals(99L to "Artist", tokens.user.id to tokens.user.name)
    }

    @Test
    fun aCodePixivRefusesIsUnauthorizedAndAnAnswerWithoutTokensABadResponse() = runBlocking {
        val refused = assertFailsWith<NetworkRequestException> {
            pixiv(FakePixivHttp { answer("", 401) }, MemoryPixivCredentials()).exchangeCode("bad", "v")
        }
        assertEquals(PixivFailure.UNAUTHORIZED, PixivFailure.of(refused))
        val empty = assertFailsWith<NetworkRequestException> {
            pixiv(FakePixivHttp { answer("""{"access_token": "only"}""") }, MemoryPixivCredentials()).exchangeCode("c", "v")
        }
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(empty))
    }

    @Test
    fun signingInPutsTheTokensAndTheirAccountInUse() = runBlocking {
        val credentials = MemoryPixivCredentials(PixivCredentials.account(5, "refresh-old"))
        val http = FakePixivHttp { answer(illustsJson()) }
        val client = pixiv(http, credentials)
        val user = client.applyLoginTokens(PixivLoginTokens("access-new", "refresh-new", 3600, PixivAuthUser(99, "Artist", "artist", isPremium = true)))
        client.following()

        assertEquals(99L, user.id)
        val held = credentials.credentials
        assertEquals(listOf("access-new", "refresh-new"), listOf(held.accessToken, held.refreshToken))
        assertEquals(99L to true, held.userId to held.isPremium)
        assertEquals(NOW + 3_540_000, held.accessExpiresAt)
        assertEquals("Bearer access-new", http.asked.single().headers["Authorization"], "no refresh for a fresh sign-in")
    }

    @Test
    fun anAnswerNamingNoAccountLeavesNoOtherAccountsIdBeside() {
        val credentials = MemoryPixivCredentials(PixivCredentials.account(5, "refresh-old"))
        pixiv(FakePixivHttp { answer("") }, credentials).applyLoginTokens(PixivLoginTokens("a", "r", 3600, PixivAuthUser.parse(J(null))))
        assertEquals(0L, credentials.credentials.userId)
    }

    @Test
    fun aPastedTokenBelongsToNoKnownAccountUntilChecked() {
        val pasted = PixivCredentials.pasted("refresh-x")
        assertEquals(0L to false, pasted.userId to pasted.isPremium)
        assertTrue(pasted.signedIn)
        assertFalse(PixivCredentials.NONE.signedIn)
    }

    @Test
    fun credentialsNeverPrintTheirTokens() {
        val printed = listOf(
            PixivCredentials(refreshToken = "secret-refresh", accessToken = "secret-access", userId = 9).toString(),
            PixivLoginTokens("secret-access", "secret-refresh", 3600, PixivAuthUser(9, "", "")).toString(),
            PixivPkce("secret-verifier", "challenge").toString(),
        )
        assertTrue(printed.none { "secret" in it }, printed.toString())
    }

    @Test
    fun theTokenResponsesUserReadsAQuotedIdAndPremium() {
        val user = PixivAuthUser.parse(J.parse("""{"id": "123", "name": " Reader ", "account": "r", "is_premium": true, "profile_image_urls": {"px_170x170": "https://i.pximg.net/170.jpg", "px_50x50": "https://i.pximg.net/50.jpg"}}"""))
        assertEquals(PixivAuthUser(123, "Reader", "r", isPremium = true, avatarUrl = "https://i.pximg.net/170.jpg"), user)
        for (json in listOf("null", "\"user\"", """{"is_premium": "yes"}""")) {
            val unknown = PixivAuthUser.parse(J.parse(json))
            assertEquals(Triple(0L, false, ""), Triple(unknown.id, unknown.isPremium, unknown.displayName), json)
        }
    }
}
