package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException

/** The network name Pixiv's refusals carry, so a screen can say who refused. */
const val PIXIV_NETWORK = "pixiv"

/** Pixivision's: Pixiv's magazine is a site of its own, read without any Pixiv account. */
const val PIXIVISION_NETWORK = "pixivision"

/** No Pixiv account is signed in, so there is no token to read with. */
class PixivNoAccountException : Exception("No Pixiv account is signed in")

/**
 * What went wrong with a Pixiv read, in the six kinds XTA words for the
 * reader. Pixiv's own quirks are already undone in the codes the client
 * throws: a rate limit Pixiv answers with a 400 or 403 arrives as 429, and a
 * token it refuses with a 400 as 401.
 */
enum class PixivFailure {
    /** No account is signed in. */
    NOT_SIGNED_IN,

    /** No answer came: offline, or the connection broke. */
    NETWORK,

    /** Pixiv turned the account away: an ended session, a revoked token or a locked account. */
    UNAUTHORIZED,
    RATE_LIMITED,
    NOT_FOUND,

    /** Pixiv answered, but not with what was asked. */
    BAD_RESPONSE,
    ;

    companion object {
        fun of(error: Throwable): PixivFailure = when (error) {
            is PixivNoAccountException -> NOT_SIGNED_IN
            is NetworkRequestException -> ofAnswer(error)
            else -> NETWORK
        }

        /** Pixivision holds no token and keeps no limits of Pixiv's, so its refusals are only missing pages or bad answers. */
        private fun ofAnswer(error: NetworkRequestException): PixivFailure = when {
            error.code == 404 -> NOT_FOUND
            error.network == PIXIVISION_NETWORK -> BAD_RESPONSE
            error.code == 429 -> RATE_LIMITED
            error.code == 401 || error.code == 403 -> UNAUTHORIZED
            else -> BAD_RESPONSE
        }
    }
}

/** Pixiv answered with success but without what was asked: a failed read all the same. */
internal fun unusableAnswer(what: String) = NetworkRequestException(PIXIV_NETWORK, 200, what)
