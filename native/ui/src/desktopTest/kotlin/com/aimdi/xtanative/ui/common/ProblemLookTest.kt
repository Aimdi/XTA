package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.core.x.XEndpointRefusedException
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XNoWorkingAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.inFirstKinds
import com.aimdi.xtanative.data.isRateLimit
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's read failure kinds (read_failure_kind.dart) and the full-page errors that say them (ui/errors.dart). */
class ProblemLookTest {
    @Test
    fun eachFailureIsTheKindXtaNamesIt() {
        assertEquals(Problem.NoWorkingAccount, Problem.of(XNoWorkingAccountException()), "before the plain no-account it extends")
        assertEquals(Problem.NoAccount, Problem.of(XNoAccountException()))
        assertEquals(Problem.RateLimitedUntil(5_000), Problem.of(XRateLimitedException(5_000)))
        assertEquals(Problem.RateLimited, Problem.of(XRateLimitedException()), "another account can still be tried: no countdown")
        assertEquals(Problem.EndpointRefused, Problem.of(XEndpointRefusedException("UserTweets")))
        assertEquals(Problem.Refused(404), Problem.of(XUserUnavailableException("gone", null)))
        assertEquals(Problem.Signing, Problem.of(SigningUnavailableException("")))
        assertEquals(Problem.TimedOut, Problem.of(SocketTimeoutException("timeout")))
        assertEquals(Problem.Offline, Problem.of(UnknownHostException("x.com")))
        assertEquals(Problem.Other, Problem.of(IllegalStateException()))
    }

    @Test
    fun aRateLimitSaysTheMostAndCountsDownToItsFirstReset() {
        val several = listOf(IOException(), XRateLimitedException(9_000), XRateLimitedException(4_000))
        assertEquals(Problem.RateLimitedUntil(4_000), Problem.of(several))
        assertEquals(Problem.RateLimited, Problem.of(several + XRateLimitedException()), "one of them gave no reset")
        assertEquals(Problem.Offline, Problem.of(listOf(IOException(), IllegalStateException())))
        assertNull(Problem.of(emptyList()))
        assertTrue(Problem.RateLimitedUntil(1).isRateLimit)
        assertFalse(Problem.Offline.isRateLimit)
    }

    @Test
    fun pluginsKeepTheKindsTheirApiWasBuiltWith() {
        assertEquals(Problem.RateLimited, Problem.RateLimitedUntil(4_000).inFirstKinds)
        assertEquals(Problem.NoAccount, Problem.NoWorkingAccount.inFirstKinds)
        assertEquals(Problem.Refused(404), Problem.EndpointRefused.inFirstKinds)
        assertEquals(Problem.Offline, Problem.TimedOut.inFirstKinds)
        assertEquals(Problem.Refused(503), Problem.Refused(503).inFirstKinds)
    }

    @Test
    fun theScreensAreXtas() {
        assertEquals(ProblemLook.NO_ACCOUNT, ProblemLook.of(Problem.NoAccount))
        assertEquals(ProblemLook.RATE_LIMITED, ProblemLook.of(Problem.RateLimitedUntil(1)))
        assertEquals(ProblemLook.NO_WORKING_ACCOUNT, ProblemLook.of(Problem.Refused(401)), "a sign-in X no longer takes")
        assertEquals(ProblemLook.SERVICE_UNAVAILABLE, ProblemLook.of(Problem.Refused(503)))
        assertEquals(ProblemLook.REQUEST_UNAVAILABLE, ProblemLook.of(Problem.Refused(403)))
        assertEquals(ProblemLook.HTTP, ProblemLook.of(Problem.Refused(400)))
        assertEquals(ProblemLook.TIMED_OUT, ProblemLook.of(Problem.TimedOut))
    }

    @Test
    fun anotherAccountIsOfferedOnlyWhereItWouldHelp() {
        val offered = ProblemLook.entries.filter { it.addAccount }
        assertEquals(listOf(ProblemLook.NO_ACCOUNT, ProblemLook.RATE_LIMITED, ProblemLook.NO_WORKING_ACCOUNT), offered)
        assertFalse(ProblemLook.ENDPOINT_REFUSED.addAccount, "X refused the request itself: the accounts are fine")
        assertEquals(listOf(ProblemLook.SIGNING), ProblemLook.entries.filter { it.diagnostics })
    }

    @Test
    fun theRetryCountsDownInMinutesAndSeconds() {
        assertEquals(90, RetryCountdown.secondsLeft(resetsAt = 100_000, now = 10_500), "rounded up")
        assertEquals(0, RetryCountdown.secondsLeft(resetsAt = 100_000, now = 100_000))
        assertEquals(0, RetryCountdown.secondsLeft(resetsAt = 100_000, now = 200_000))
        assertEquals("1:30", RetryCountdown.format(90))
        assertEquals("0:05", RetryCountdown.format(5))
        assertEquals("15:00", RetryCountdown.format(900))
        assertEquals(4_000L, RetryCountdown.resetOf(Problem.RateLimitedUntil(4_000)))
        assertNull(RetryCountdown.resetOf(Problem.RateLimited))
        assertNull(RetryCountdown.resetOf(Problem.Refused(429)))
    }

    @Test
    fun noConnectionAndATimeoutAreScreensOfTheirOwn() {
        assertEquals(ProblemLook.OFFLINE, ProblemLook.of(Problem.of(IOException())))
        assertEquals(ProblemLook.TIMED_OUT, ProblemLook.of(Problem.of(SocketTimeoutException())))
        assertEquals(ProblemLook.HTTP, ProblemLook.of(Problem.of(XRequestException(418, ""))))
    }
}
