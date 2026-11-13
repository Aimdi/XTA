package com.aimdi.xtanative.networks.threads

import java.time.Instant

/** Why a followed account could not be read, in the words its row says it. */
enum class ThreadsTrouble {
    /** Meta has no such account: renamed, deleted, or mistyped. */
    MISSING,

    /** The account is private: a guest is shown none of its threads. */
    PRIVATE,

    /** Meta is refusing for now, or the session is cooling down. */
    THROTTLED,

    /** Meta sent a guest to its sign-in page, or refused the session. */
    SIGN_IN,

    /** Meta, or the instance, did not answer, or answered with something unreadable. */
    NETWORK;

    companion object {
        fun of(error: Throwable): ThreadsTrouble = when ((error as? ThreadsException)?.failure) {
            ThreadsFailure.NO_SUCH_FEED -> MISSING
            ThreadsFailure.PRIVATE_ACCOUNT -> PRIVATE
            ThreadsFailure.THROTTLED, ThreadsFailure.SESSION_SUSPENDED -> THROTTLED
            ThreadsFailure.SIGN_IN_REQUIRED, ThreadsFailure.UNAUTHORIZED -> SIGN_IN
            else -> NETWORK
        }

        /** A failure that is the account's answer for a while: asking again within minutes would only hear it again. */
        fun settles(error: Throwable): Boolean = of(error).let { it == MISSING || it == PRIVATE }
    }
}

/** A followed account whose last read failed: who, why, when, and when Meta said to come back. */
data class ThreadsAccountFailure(
    val handle: String,
    val trouble: ThreadsTrouble,
    val at: Instant,
    val retryAt: Instant? = null,
) {
    companion object {
        fun of(handle: String, error: Throwable, at: Instant) =
            ThreadsAccountFailure(handle, ThreadsTrouble.of(error), at, (error as? ThreadsException)?.retryAt)
    }
}
