package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException

/** Why a read brought nothing, in the terms a screen explains. */
sealed interface Problem {
    data object NoAccount : Problem
    data object RateLimited : Problem
    data object Offline : Problem
    data object Signing : Problem
    data class Refused(val code: Int) : Problem
    data object Other : Problem

    companion object {
        fun of(error: Throwable): Problem = when (error) {
            is XNoAccountException -> NoAccount
            is XRateLimitedException -> RateLimited
            is XRequestException -> Refused(error.code)
            is SigningUnavailableException -> Signing
            is IOException -> Offline
            else -> Other
        }

        /** The one to show for several: a rate limit says the most about what to do. */
        fun of(errors: List<Throwable>): Problem? =
            errors.map { of(it) }.let { problems -> problems.firstOrNull { it == RateLimited } ?: problems.firstOrNull() }
    }
}
