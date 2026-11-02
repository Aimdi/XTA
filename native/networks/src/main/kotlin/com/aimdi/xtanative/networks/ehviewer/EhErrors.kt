package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.networks.NetworkRequestException

/** Why a read failed, as XTA tells the reader: one message for each. */
enum class EhErrorKind {
    /** No answer: no connection, or it broke off. */
    NETWORK,

    /** 401 or 403: the cookies are wrong or ExHentai turned the account away. */
    UNAUTHORIZED,

    /** 429. */
    RATE_LIMITED,

    /** 404, or a page no preview sheet lists. */
    NOT_FOUND,

    /** Another status, or a page that did not read as what was asked for. */
    BAD_RESPONSE,

    /** The site's "Your IP address has been temporarily banned" page, for too many page loads. */
    BANNED,

    /** The site's content warning, shown because [EhSettings.adultConsent] was not given. */
    CONTENT_WARNING,
    ;

    companion object {
        /** The kind of any failure a read can throw. */
        fun of(error: Throwable): EhErrorKind = when (error) {
            is EhException -> error.kind
            is NetworkRequestException -> when (error.code) {
                401, 403 -> UNAUTHORIZED
                404 -> NOT_FOUND
                429 -> RATE_LIMITED
                else -> BAD_RESPONSE
            }
            else -> NETWORK
        }
    }
}

/**
 * The site answered, but not with the page asked for: a ban, a content
 * warning, or a page that parsed as nothing. A non-success status is a
 * [NetworkRequestException] instead.
 */
class EhException(val kind: EhErrorKind, message: String) : Exception(message)
