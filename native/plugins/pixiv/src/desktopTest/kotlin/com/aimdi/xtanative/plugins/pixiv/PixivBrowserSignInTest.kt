package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.networks.pixiv.PixivLogin
import com.aimdi.xtanative.networks.pixiv.PixivPkce
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.tokenAnswer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class PixivBrowserSignInTest {
    private var clock = 1_000_000L
    private var made = 0
    private val signIn = PixivBrowserSignIn(now = { clock }, fresh = { made++; PixivPkce("verifier-$made", "challenge-$made") })
    private val user = PixivAuthUser(9, "Reader", "reader")

    @Test
    fun startingOpensPixivsPageForAFreshChallengeAndWaits() {
        val url = signIn.start()
        assertTrue(url.startsWith(PixivLogin.LOGIN_URL), url)
        assertTrue("code_challenge=challenge-1" in url && "code_challenge_method=S256" in url, url)
        assertTrue(signIn.state.value.waiting)
        assertTrue(signIn.waiting())
        assertTrue("challenge-2" in signIn.start(), "another start is another sign-in")
    }

    @Test
    fun theCodeIsFoundInWhatEverTheReaderPastes() {
        assertEquals("abcDEF_123-x", PixivBrowserSignIn.codeIn("pixiv://account/login?code=abcDEF_123-x&via=login"))
        assertEquals("abcDEF_123-x", PixivBrowserSignIn.codeIn("  \"pixiv://account/login?code=abcDEF_123-x\"\n"))
        assertEquals("c0de", PixivBrowserSignIn.codeIn("${PixivLogin.REDIRECT_URI}?state=s&code=c0de"))
        assertEquals("Gq3_fK29xYz-ab", PixivBrowserSignIn.codeIn("Gq3_fK29xYz-ab"))
        assertNull(PixivBrowserSignIn.codeIn("pixiv://account/login?error=access_denied"))
        assertNull(PixivBrowserSignIn.codeIn("https://www.pixiv.net/artworks/1?code=nope"))
        assertNull(PixivBrowserSignIn.codeIn("short"))
        assertNull(PixivBrowserSignIn.codeIn("two words here"))
        assertNull(PixivBrowserSignIn.codeIn(""))
    }

    @Test
    fun aCodeHandedBackIsTradedWithTheWaitingVerifierOnce() = runBlocking {
        signIn.start()
        val traded = mutableListOf<Pair<String, String>>()
        val outcome = signIn.handBack("pixiv://account/login?code=the-code&via=login") { code, verifier -> user.also { traded += code to verifier } }
        assertEquals(PixivCodeOutcome.SignedIn(user), outcome)
        assertEquals(listOf("the-code" to "verifier-1"), traded)
        assertEquals(PixivBrowserSignInState(waiting = false, trading = false, finished = 1), signIn.state.value)
        assertEquals(PixivCodeOutcome.Expired, signIn.handBack("pixiv://account/login?code=again") { _, _ -> user }, "a verifier signs in once")
    }

    @Test
    fun withoutAStartedSignInOrWithoutACodeNothingIsTraded() = runBlocking {
        assertEquals(PixivCodeOutcome.Expired, signIn.handBack("pixiv://account/login?code=the-code") { _, _ -> error("not traded") })
        signIn.start()
        assertEquals(PixivCodeOutcome.NoCode, signIn.handBack("hello there") { _, _ -> error("not traded") })
        assertTrue(signIn.waiting(), "a paste without a code leaves the sign-in waiting")
    }

    @Test
    fun aSignInWaitsTenMinutesAtMost() = runBlocking {
        signIn.start()
        clock += PixivBrowserSignIn.LIFETIME_MS
        assertTrue(signIn.waiting())
        clock += 1
        assertFalse(signIn.waiting())
        assertFalse(signIn.state.value.waiting)
        assertEquals(PixivCodeOutcome.Expired, signIn.handBack("pixiv://account/login?code=late") { _, _ -> error("not traded") })
    }

    @Test
    fun aRefusedCodeLeavesTheSignInWaitingForAnother() = runBlocking {
        signIn.start()
        val refused = signIn.handBack("bad-code-123") { _, _ -> throw IOException("refused") }
        assertIs<PixivCodeOutcome.Failed>(refused)
        assertTrue(signIn.state.value.waiting && !signIn.state.value.trading)
        assertEquals(PixivCodeOutcome.SignedIn(user), signIn.handBack("good-code-123") { _, verifier -> user.also { assertEquals("verifier-1", verifier) } })
    }

    @Test
    fun aSecondCodeWhileOneIsTradedIsTurnedAway() = runBlocking {
        signIn.start()
        val release = CompletableDeferred<Unit>()
        val first = async { signIn.handBack("first-code-1") { _, _ -> release.await(); user } }
        while (!signIn.state.value.trading) yield()
        assertEquals(PixivCodeOutcome.Busy, signIn.handBack("second-code-2") { _, _ -> error("not traded") })
        release.complete(Unit)
        assertEquals(PixivCodeOutcome.SignedIn(user), first.await())
    }

    @Test
    fun theBrowsersAddressSignsTheRuntimeInThroughPixivsTokenEndpoint() {
        val http = FakePixivHttp { asked ->
            if (asked.url.toString() == PixivLogin.TOKEN_URL) tokenAnswer("access-new", "refresh-new", """{"id": "42", "name": "Artist", "account": "artist"}""") else answer("{}")
        }
        withRuntime(PixivTestHost(http = http)) { runtime ->
            val url = runtime.browserSignIn.start()
            val outcome = runtime.browserSignIn.handBack("pixiv://account/login?code=from-browser&via=login") { code, verifier -> PixivSession.signIn(runtime, code, verifier) }
            assertIs<PixivCodeOutcome.SignedIn>(outcome)
            val call = http.asked.single { it.url.toString() == PixivLogin.TOKEN_URL }
            assertEquals("authorization_code" to "from-browser", call.form["grant_type"] to call.form["code"])
            val verifier = call.form["code_verifier"].orEmpty()
            assertEquals(PixivLogin.loginUrl(challengeOf(verifier)).toString(), url, "the verifier is the one whose challenge the page was given")
            assertTrue(runtime.signedIn)
            assertEquals(42L, runtime.ownId)
            assertEquals("Signed in as Artist", signInOutcomeText(outcome, "en"))
        }
    }

    @Test
    fun whatTheReaderIsToldOfEachOutcome() {
        assertEquals("That holds no sign-in code", signInOutcomeText(PixivCodeOutcome.NoCode, "en"))
        assertEquals("This sign-in has expired. Open Pixiv's sign-in page again.", signInOutcomeText(PixivCodeOutcome.Expired, "en"))
        assertNull(signInOutcomeText(PixivCodeOutcome.Busy, "en"))
        assertTrue(signInOutcomeText(PixivCodeOutcome.Failed(IOException()), "en").orEmpty().startsWith("Could not sign in to Pixiv: "))
    }

    private fun challengeOf(verifier: String): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
}
