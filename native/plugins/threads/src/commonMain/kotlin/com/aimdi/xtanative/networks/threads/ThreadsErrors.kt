package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.NetworkFailure
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** Why a Threads read could not be served, in the terms a screen explains it (XTA's ThreadsErrorKind). */
enum class ThreadsFailure {
    /** Nothing to read with: no session for a session read, no RSSHub instance, no account to read. */
    NOT_CONFIGURED,

    /** Meta, or the instance, answered with an error or with something that is not what was asked. */
    UNREACHABLE,

    /** No such account or post, or nothing could be read for it. */
    NO_SUCH_FEED,

    /** Meta is refusing for now. Asking again within seconds is how a throttle becomes a ban. */
    THROTTLED,

    /** The pasted session was refused (401, 403). */
    UNAUTHORIZED,

    /** Meta parked the session (`login_required`), or it is cooling down after a throttle. */
    SESSION_SUSPENDED,
}

/** How a screen may retry a failure without being asked (XTA's ReadRetry). */
enum class ThreadsRetry {
    /** Transient: again on a growing, bounded backoff. */
    BACKOFF,

    /** Once, after [ThreadsException.retryAt]: when Meta said to come back. */
    AT_RESET,

    /** Only when the reader asks: a setup is missing or the session is lost. */
    MANUAL,
}

/**
 * A Threads read that could not be served. [code] is the HTTP status when the
 * server answered with one; a connection that failed before any answer stays
 * the [IOException] the transport threw. [retryAt] is the end of the wait Meta
 * asked for, when it asked for one.
 */
class ThreadsException(
    val failure: ThreadsFailure,
    message: String,
    override val code: Int? = null,
    override val retryAt: Instant? = null,
) : Exception(message), NetworkFailure {
    /** Which network failed, so a screen can name it. */
    override val network: String get() = NETWORK

    /**
     * A throttle waits for its cooldown, and without one for the reader; a
     * missing setup or a lost session waits for the reader; a host that did not
     * answer, or answered with nothing, is tried again on the usual backoff.
     */
    val retry: ThreadsRetry
        get() = when {
            failure == ThreadsFailure.UNREACHABLE || failure == ThreadsFailure.NO_SUCH_FEED -> ThreadsRetry.BACKOFF
            retryAt != null -> ThreadsRetry.AT_RESET
            else -> ThreadsRetry.MANUAL
        }

    companion object {
        const val NETWORK = "threads"
    }
}

/** How [error], thrown by a Threads read, may be retried: a connection that failed is tried again on the backoff. */
fun threadsRetryOf(error: Throwable): ThreadsRetry = (error as? ThreadsException)?.retry ?: ThreadsRetry.BACKOFF

/** A failure XTA's fallbacks step over: what Meta answered, or a connection that failed. */
internal fun Throwable.isReadFailure(): Boolean = this is ThreadsException || this is IOException

/** [block]'s value, or null when it failed in any way but cancellation. */
internal suspend fun <T> attempt(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
