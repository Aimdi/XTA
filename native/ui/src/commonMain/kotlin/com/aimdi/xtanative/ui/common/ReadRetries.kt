package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.XAnswers

/** One taxonomy of read failures, as XTA's read_failure_kind.dart names them for a notice and its sheet. */
enum class ReadFailureKind {
    CONNECTION,
    TIMED_OUT,
    SESSION,
    RATE_LIMITED,
    ENDPOINT_REFUSED,
    TRANSACTION_UNAVAILABLE,
    UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    UNKNOWN,
    ;

    companion object {
        fun of(problem: Problem): ReadFailureKind = when (problem) {
            Problem.Offline -> CONNECTION
            Problem.TimedOut -> TIMED_OUT
            Problem.NoAccount, Problem.NoWorkingAccount -> SESSION
            Problem.RateLimited, is Problem.RateLimitedUntil -> RATE_LIMITED
            Problem.EndpointRefused -> ENDPOINT_REFUSED
            Problem.Signing -> TRANSACTION_UNAVAILABLE
            is Problem.Refused -> when (problem.code) {
                401 -> SESSION
                429 -> RATE_LIMITED
                500, 502, 503, 504 -> SERVICE_UNAVAILABLE
                403, 404 -> UNAVAILABLE
                else -> UNKNOWN
            }
            is Problem.Answer -> UNKNOWN
            Problem.Other -> UNKNOWN
        }
    }
}

/** How a reading surface may retry a failure without being asked (XTA's ReadRetry). */
enum class ReadRetry {
    /** Transient: again on a growing, bounded backoff. */
    BACKOFF,

    /** Rate limited: once, after the known reset has passed. */
    AT_RESET,

    /** Needs the reader (sign in, or X refused the request itself): never on its own. */
    MANUAL,
}

/** Which failures retry on their own, and how (read_recovery.dart). */
object ReadRetries {
    /** The growing waits between automatic retries of a transient failure. */
    val DELAYS_MS: List<Long> = listOf(2_000, 5_000, 15_000, 30_000, 60_000)

    fun of(problem: Problem): ReadRetry = when {
        problem is Problem.Answer && problem.code in XAnswers.DEFINITIVE -> ReadRetry.MANUAL
        else -> when (ReadFailureKind.of(problem)) {
            ReadFailureKind.SESSION, ReadFailureKind.ENDPOINT_REFUSED -> ReadRetry.MANUAL
            ReadFailureKind.RATE_LIMITED -> ReadRetry.AT_RESET
            else -> ReadRetry.BACKOFF
        }
    }

    /** [problem] when it may be retried on its own, else null. */
    fun recoverable(problem: Problem?): Problem? = problem?.takeIf { of(it) != ReadRetry.MANUAL }
}

/**
 * What one reading surface may still retry on its own (XTA's ReadRetryBudget):
 * each step of [delays] once, and a single retry a second after a known
 * rate-limit reset. [renew] starts over, once a read went well, the
 * connection came back or the app returned to the front.
 */
class ReadRetryBudget(private val delays: List<Long> = ReadRetries.DELAYS_MS) {
    private var attempts = 0
    private var resetSpent = false

    /** The wait in milliseconds before the next automatic retry of [mode], or null when none is left; [resetsAt] is the rate limit's end. */
    fun waitFor(mode: ReadRetry, resetsAt: Long?, now: Long): Long? = when (mode) {
        ReadRetry.BACKOFF -> delays.getOrNull(attempts)
        ReadRetry.AT_RESET -> if (resetSpent || resetsAt == null) null else (resetsAt - now + SECOND).coerceAtLeast(SECOND)
        ReadRetry.MANUAL -> null
    }

    fun spend(mode: ReadRetry) {
        if (mode == ReadRetry.AT_RESET) resetSpent = true else attempts++
    }

    fun renew() {
        attempts = 0
        resetSpent = false
    }

    private companion object {
        const val SECOND = 1_000L
    }
}
