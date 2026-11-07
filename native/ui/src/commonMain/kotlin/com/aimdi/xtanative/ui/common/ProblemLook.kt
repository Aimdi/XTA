package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.data.Problem

/**
 * XTA's full-page read failures (ui/errors.dart), each with its mark, its
 * words and its buttons: Add account where another account would help (never
 * where X refused the request itself), Diagnostics where signing failed, and
 * Retry everywhere, raised where XTA raises it.
 */
enum class ProblemLook(val emoji: String?, val addAccount: Boolean = false, val diagnostics: Boolean = false, val raisedRetry: Boolean = false) {
    NO_ACCOUNT("🔑", addAccount = true),
    RATE_LIMITED("⏳", addAccount = true),
    NO_WORKING_ACCOUNT("🤷", addAccount = true),
    ENDPOINT_REFUSED("🚧"),
    SIGNING("🧾", diagnostics = true),
    OFFLINE("🔌", raisedRetry = true),
    TIMED_OUT("⏱️", raisedRetry = true),
    SERVICE_UNAVAILABLE("🛠️"),
    REQUEST_UNAVAILABLE("🚫"),
    HTTP("🌐"),
    OTHER(null, raisedRetry = true),
    ;

    companion object {
        fun of(problem: Problem): ProblemLook = when (problem) {
            Problem.NoAccount -> NO_ACCOUNT
            Problem.RateLimited, is Problem.RateLimitedUntil -> RATE_LIMITED
            Problem.NoWorkingAccount -> NO_WORKING_ACCOUNT
            Problem.EndpointRefused -> ENDPOINT_REFUSED
            Problem.Signing -> SIGNING
            Problem.Offline -> OFFLINE
            Problem.TimedOut -> TIMED_OUT
            is Problem.Refused -> ofStatus(problem.code)
            is Problem.Answer -> OTHER
            Problem.Other -> OTHER
        }

        /** X's answer by its status, as XTA reads an HttpException: 401 a sign-in X no longer takes. */
        private fun ofStatus(code: Int): ProblemLook = when (code) {
            429 -> RATE_LIMITED
            401 -> NO_WORKING_ACCOUNT
            500, 502, 503, 504 -> SERVICE_UNAVAILABLE
            403, 404 -> REQUEST_UNAVAILABLE
            else -> HTTP
        }
    }
}

/** The wait a rate-limited Retry counts down, as XTA's RateLimitRetryButton shows it. */
object RetryCountdown {
    /** Whole seconds left until [resetsAt], rounded up; 0 once it has passed. */
    fun secondsLeft(resetsAt: Long, now: Long): Int = if (resetsAt <= now) 0 else ((resetsAt - now + 999) / 1000).toInt()

    /** `m:ss`, as XTA's formatRetryCountdown writes a wait. */
    fun format(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    /** When [problem] can be retried: its reset for a rate limit X dated, else null (now). */
    fun resetOf(problem: Problem): Long? = (problem as? Problem.RateLimitedUntil)?.resetsAt
}
