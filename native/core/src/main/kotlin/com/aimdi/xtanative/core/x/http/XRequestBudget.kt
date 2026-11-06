package com.aimdi.xtanative.core.x.http

import java.net.SocketTimeoutException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One deadline a whole read shares, as XTA's RequestBudget: trying the
 * accounts, deriving a signer, the retry and the guest fallback all spend it,
 * so no read waits longer than it was given. It rides in the coroutine
 * context, where [OkHttpXHttp] reads what is left of it for each call.
 */
class XRequestBudget(val timeoutMillis: Long) : AbstractCoroutineContextElement(Key) {
    private val start = System.nanoTime()

    /** What is left of the deadline; zero or less once it passed. */
    val remainingMillis: Long get() = timeoutMillis - (System.nanoTime() - start) / 1_000_000

    companion object Key : CoroutineContext.Key<XRequestBudget> {
        /** XTA's xRequestTimeout: no read of X waits longer. */
        const val READ_MILLIS = 30_000L

        /** XTA's TwitterHeaders.initializationTimeout: deriving a signer gives up sooner. */
        const val SIGNING_MILLIS = 12_000L

        /** XTA's endpointRegistryTimeout. */
        const val REGISTRY_MILLIS = 10_000L
    }
}

/**
 * [block] within [millis], or within what is left of the budget it runs in
 * when that is less. Past the deadline it is a [SocketTimeoutException] (a
 * timeout, as XTA's TimeoutException) naming [what], not a cancellation.
 */
suspend fun <T> withRequestBudget(millis: Long, what: String, block: suspend () -> T): T {
    val outer = coroutineContext[XRequestBudget]
    val budget = XRequestBudget(outer?.remainingMillis?.coerceAtMost(millis) ?: millis)
    if (budget.timeoutMillis < 1) throw SocketTimeoutException("$what: the request's time is spent")
    val done = withTimeoutOrNull(budget.timeoutMillis) { withContext(budget) { Done(block()) } }
        ?: throw SocketTimeoutException("$what: no answer in ${budget.timeoutMillis} ms")
    return done.value
}

private class Done<T>(val value: T)
