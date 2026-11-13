package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XEndpointRefusedException
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XNoWorkingAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import java.io.InterruptedIOException

/** Why a read brought nothing, in the terms a screen explains (XTA's read failure kinds). */
sealed interface Problem {
    data object NoAccount : Problem
    data object RateLimited : Problem
    data object Offline : Problem
    data object Signing : Problem
    data class Refused(val code: Int) : Problem
    data object Other : Problem

    /** Rate-limited until [resetsAt] (epoch milliseconds), when X said so for every account it asked. */
    data class RateLimitedUntil(val resetsAt: Long) : Problem

    /** Every account was refused, each again after another account was answered: their sign-ins look broken. */
    data object NoWorkingAccount : Problem

    /** X refused this part of it to every account, which a rotated query id or a stale signature does: not the accounts. */
    data object EndpointRefused : Problem

    /** The read took longer than the app waits. */
    data object TimedOut : Problem

    /**
     * X answered with one of its own error codes, the ones XTA explains
     * (private, missing, suspended, forbidden, a guest token it threw away).
     * [code] is X's, not an HTTP status.
     */
    data class Answer(val code: Int) : Problem

    companion object {
        fun of(error: Throwable): Problem = when (error) {
            is XNoWorkingAccountException -> NoWorkingAccount
            is XNoAccountException -> NoAccount
            is XRateLimitedException -> error.resetsAt?.let(::RateLimitedUntil) ?: RateLimited
            is XEndpointRefusedException -> EndpointRefused
            is XRequestException -> Refused(error.code)
            is SigningUnavailableException -> Signing
            is XAnswerException -> error.errorCode?.takeIf { it in XAnswers.EXPLAINED }?.let(::Answer) ?: Other
            is InterruptedIOException -> TimedOut
            is IOException -> Offline
            else -> Other
        }

        /**
         * The one to show for several: a rate limit says the most about what
         * to do, with the first reset among them, unless one of them gave none.
         */
        fun of(errors: List<Throwable>): Problem? {
            val problems = errors.map { of(it) }
            val limits = problems.filter { it.isRateLimit }
            val resets = limits.mapNotNull { (it as? RateLimitedUntil)?.resetsAt }
            return when {
                limits.isEmpty() -> problems.firstOrNull()
                resets.size < limits.size -> RateLimited
                else -> RateLimitedUntil(resets.min())
            }
        }
    }
}

/** Whether X is rate-limiting the read, with a known reset or not. */
val Problem.isRateLimit: Boolean get() = this == Problem.RateLimited || this is Problem.RateLimitedUntil

/**
 * X's own error codes a screen explains, as XTA's describeTwitterErrorCode,
 * and the ones a retry cannot change (read_failure_kind.dart).
 */
object XAnswers {
    /** Codes XTA writes a sentence for. */
    val EXPLAINED = setOf(22, 34, 50, 63, 200, 239)

    /** Codes a retry cannot change: private, missing, suspended, forbidden. A thrown-away guest token (239) is tried again. */
    val DEFINITIVE = setOf(-1, 22, 34, 50, 63, 200)

    /** The mark XTA draws beside [code], or null for a code it does not explain. */
    fun emoji(code: Int): String? = when (code) {
        22 -> "🔒"
        34 -> "🤔"
        50 -> "🕵️"
        63 -> "👮"
        200 -> "⛔"
        239 -> "💩"
        else -> null
    }
}

/**
 * The same failure in the six kinds the plugin API was first built with, which
 * a plugin's own words for a failure switch over: XTA's later kinds read as
 * the nearest of those.
 */
internal val Problem.inFirstKinds: Problem get() = when (this) {
    is Problem.RateLimitedUntil -> Problem.RateLimited
    Problem.NoWorkingAccount -> Problem.NoAccount
    Problem.EndpointRefused -> Problem.Refused(404)
    Problem.TimedOut -> Problem.Offline
    is Problem.Answer -> Problem.Other
    else -> this
}
