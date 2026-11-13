package com.aimdi.xtanative.core.x

/**
 * X answered, but not with what was asked. A connection that failed before
 * any answer is an [java.io.IOException] instead.
 */
sealed class XReadException(message: String) : Exception(message)

/** [code] is X's HTTP status. */
open class XRequestException(val code: Int, message: String) : XReadException(message)

/**
 * Every account was rate-limited on this endpoint. [resetsAt] is when the
 * first of them frees up (epoch milliseconds), when X said for each.
 */
class XRateLimitedException(val resetsAt: Long? = null) : XReadException("Every account is rate-limited here for now")

/** There is no X account to read with, and X did not answer a guest either: XTA's backup has to be imported first. */
open class XNoAccountException(why: String = "No X account") : XReadException(why)

/**
 * Every account was refused with 404, and each had been refused before while
 * another account was answered: their sign-ins, not X, are what is broken.
 */
class XNoWorkingAccountException : XNoAccountException("Every account's sign-in looks broken")

/**
 * Every account was refused with 404 on [endpoint] and none had been before.
 * X answers a rotated query id or a stale signature that way too, so no
 * account is blamed.
 */
class XEndpointRefusedException(val endpoint: String) : XRequestException(404, "X refused $endpoint to every account")

/**
 * X has no account by that name for the reader, or will not show it: [reason]
 * is X's, such as `Suspended`; null when X sent no account at all.
 */
class XUserUnavailableException(val screenName: String, val reason: String?) :
    XRequestException(404, "No account @$screenName to show" + (reason?.let { " ($it)" } ?: ""))

/** X sent no community [id]: it does not exist, or is not shown to the account that asked. */
class XCommunityUnavailableException(val id: String) : XRequestException(404, "Community $id is unavailable")

/** X answered with an error in place of what was asked: [errorCode] and the message are X's own. */
class XAnswerException(val errorCode: Int?, message: String) : XReadException(message)

/** X would not translate a post; [kind] says why, so a screen can say it in the reader's language. */
class XTranslationException(val kind: Kind, message: String) : XReadException(message) {
    enum class Kind { UNSUPPORTED_LANGUAGE, BANNED, TOO_MANY_REQUESTS, FAILED, NOT_READY, OTHER }
}
