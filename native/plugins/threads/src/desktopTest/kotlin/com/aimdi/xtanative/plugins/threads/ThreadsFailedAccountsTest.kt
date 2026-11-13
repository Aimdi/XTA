package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsAccountFailure
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsTrouble
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the failed accounts' list says of each account. */
class ThreadsFailedAccountsTest {
    private val at = Instant.parse("2026-11-12T10:00:00Z")

    private fun said(error: Exception) = ThreadsFailures.trouble(ThreadsAccountFailure.of("zuck", error, at), "en", ZoneOffset.UTC)

    @Test
    fun eachKindOfFailureIsSaidInItsOwnWords() {
        assertEquals("Not found — renamed, deleted or mistyped?", said(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404", 404)))
        assertEquals("Private — Threads shows a guest none of its posts", said(ThreadsException(ThreadsFailure.PRIVATE_ACCOUNT, "private")))
        assertEquals("Could not reach Threads", said(IOException("reset")))
        assertEquals("Threads shows this page only to signed-in readers right now", said(ThreadsException(ThreadsFailure.SIGN_IN_REQUIRED, "login")))
    }

    @Test
    fun aThrottleSaysWhenItsWaitEnds() {
        val throttle = ThreadsException(ThreadsFailure.THROTTLED, "429", 429, retryAt = Instant.parse("2026-11-12T10:15:00Z"))
        assertEquals(ThreadsTrouble.THROTTLED, ThreadsTrouble.of(throttle))
        assertEquals("Threads is rate limiting — try again after 10:15 AM", said(throttle).replace(' ', ' '))
    }

    @Test
    fun onlyAMissingOrPrivateAccountSettlesForItsWindow() {
        assertEquals(true, ThreadsTrouble.settles(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404")))
        assertEquals(true, ThreadsTrouble.settles(ThreadsException(ThreadsFailure.PRIVATE_ACCOUNT, "private")))
        assertEquals(false, ThreadsTrouble.settles(ThreadsException(ThreadsFailure.THROTTLED, "429")))
        assertEquals(false, ThreadsTrouble.settles(IOException("timeout")))
    }

    @Test
    fun retryWaitsOnlyForAThrottleWhoseEndIsAhead() {
        val until = Instant.parse("2026-11-12T10:15:00Z")
        val throttle = ThreadsException(ThreadsFailure.THROTTLED, "429", 429, retryAt = until)
        assertEquals(until, ThreadsFailures.retryBlockedUntil(throttle, at))
        assertNull(ThreadsFailures.retryBlockedUntil(throttle, until.plusSeconds(1)))
        assertNull(ThreadsFailures.retryBlockedUntil(IOException("reset"), at))
    }

    @Test
    fun aSessionThatCannotBeTestedSaysWhyBeforeMetaIsAsked() {
        val whole = "sessionid=s1; csrftoken=c1; ds_user_id=7; mid=m1; ig_did=d1"
        assertNull(ThreadsFailures.untestable(whole, "", "en"))
        assertNull(ThreadsFailures.untestable("", "IGT:2:abc", "en"))
        assertEquals("Paste the cookies or a Bearer token first", ThreadsFailures.untestable("", "", "en"))
        assertEquals("A Bearer token starts with IGT:2:", ThreadsFailures.untestable("", "abc", "en"))
        assertEquals(
            "Missing sessionid, ds_user_id — copy the whole Cookie header from a signed-in threads.com tab",
            ThreadsFailures.untestable("csrftoken=c1; mid=m1; ig_did=d1", "", "en"),
        )
        assertEquals("That is a Bearer token — paste it in the Bearer field below", ThreadsFailures.untestable("IGT:2:abc", "", "en"))
    }

    @Test
    fun theSignInPageIsAScreenOfItsOwn() {
        assertEquals(ThreadsRoute.SignIn, ThreadsRoutes.parse(ThreadsRoutes.SIGN_IN))
    }
}

