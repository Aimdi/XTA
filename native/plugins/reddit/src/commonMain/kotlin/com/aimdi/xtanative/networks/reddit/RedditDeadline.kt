package com.aimdi.xtanative.networks.reddit

import java.net.SocketTimeoutException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The requests that outlive a reader's patience. The calls are made on their
 * own scope because XTA's [com.aimdi.xtanative.core.x.http.XHttp] blocks
 * inside OkHttp: a plain `withTimeout` cannot end that wait, only the wait for
 * its answer. A call given up on still finishes on its own, no later than
 * OkHttp's 30-second call timeout.
 */
private val requests = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * [request]'s answer, or a [SocketTimeoutException] after [millis] whatever
 * [what] names: Reddit's reads wait at most twenty seconds and its token
 * requests fifteen, as XTA's `_send` and `_tokenTimeout` hold them.
 */
internal suspend fun <T> withDeadline(millis: Long, what: Any, request: suspend () -> T): T {
    val work = requests.async { request() }
    try {
        return withTimeoutOrNull(millis) { work.await() }
            ?: throw SocketTimeoutException("$what: no answer in $millis ms")
    } finally {
        // A reader who left must not keep waiting; the blocking call itself
        // has no cancellation through XHttp, so it ends on OkHttp's terms.
        work.cancel()
    }
}

/**
 * What one read the reader waits on may spend in all, across every route and
 * fallback it walks: each request under it waits no longer than what is left,
 * and once it is spent the requests still to come fail at once instead of
 * holding a spinner for another twenty seconds each.
 */
internal class RedditBudget(private val endsAtNanos: Long) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RedditBudget>

    val leftMillis: Long get() = (endsAtNanos - System.nanoTime()) / 1_000_000
}

/** [block] under a budget of [millis]; inside one already, the outer budget holds. */
internal suspend fun <T> withinBudget(millis: Long, block: suspend () -> T): T =
    if (coroutineContext[RedditBudget] != null) block() else withContext(RedditBudget(System.nanoTime() + millis * 1_000_000)) { block() }

/**
 * How long the next request to [what] may wait: [millis], or less when the
 * read it belongs to has less left. A spent budget is a timeout before
 * anything is asked.
 */
internal suspend fun allowance(millis: Long, what: Any): Long {
    val left = coroutineContext[RedditBudget]?.leftMillis ?: return millis
    if (left <= 0) throw SocketTimeoutException("$what: the read's time ran out before it was asked")
    return minOf(millis, left)
}
