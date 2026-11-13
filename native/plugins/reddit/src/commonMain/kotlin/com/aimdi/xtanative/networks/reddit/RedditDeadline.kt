package com.aimdi.xtanative.networks.reddit

import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
