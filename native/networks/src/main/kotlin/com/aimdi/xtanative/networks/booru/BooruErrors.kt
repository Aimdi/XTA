package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.NetworkRequestException

/** Why a booru read failed, which tells the reader what to do next, as XTA's messages do. */
enum class BooruErrorKind {
    /** No answer: the host could not be reached. */
    NETWORK,

    /** A 401, or a 403 or "Missing authentication" although credentials went out: they were refused. */
    UNAUTHORIZED,

    /** A 403 with no credentials sent: the host wants a login, or a bot check stands in front of it. */
    FORBIDDEN,

    RATE_LIMITED,

    /** A 503: the host is overloaded, or limits requests that way (e621). */
    UNAVAILABLE,

    /** The host told a guest it needs credentials (Rule34). */
    NEEDS_CREDENTIALS,

    /** The host pages no further: a 410, or gelbooru.com's "Too deep". A post list simply ends there. */
    TOO_DEEP,

    /** A 422: the search has more tags than the host takes, even shared out (Danbooru: two for guests and Members). */
    TAG_LIMIT,

    /** A 500 saying the host's database gave up on the search: too many posts to sort (Danbooru's `order:score` on a big tag). */
    TIMED_OUT,

    NOT_FOUND,

    /** Any other refusal, or an answer that cannot be read. */
    BAD_RESPONSE,
    ;

    companion object {
        /** The kind of [error], as a booru read threw it. */
        fun of(error: Throwable): BooruErrorKind = when (error) {
            is NetworkRequestException -> (error.cause as? BooruRefusal)?.kind ?: BAD_RESPONSE
            else -> NETWORK
        }
    }
}

/**
 * Why a booru refused a read, carried as the cause of the [NetworkRequestException] it throws:
 * that exception names the network and the HTTP status, and has no room for the kind XTA tells
 * apart. [allowedTags] is how many tags one search may have, when the host said.
 */
class BooruRefusal(val kind: BooruErrorKind, val allowedTags: Int? = null) :
    Exception(kind.name + (allowedTags?.let { " (at most $it tags)" } ?: ""), null, false, false)

/** The exception a booru read throws for a refusal of [kind]; credentials in [message] are masked. */
internal fun booruRefusal(kind: BooruErrorKind, code: Int, message: String, allowedTags: Int? = null): NetworkRequestException =
    NetworkRequestException(BooruClient.NETWORK, code, BooruEndpoints.redact(message)).apply { initCause(BooruRefusal(kind, allowedTags)) }

internal val NetworkRequestException.refusal: BooruRefusal? get() = cause as? BooruRefusal
