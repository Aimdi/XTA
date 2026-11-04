package com.aimdi.xtanative.networks.threads

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * What one read of Threads may still try again without the reader asking, as
 * XTA's ReadRetryBudget spends it (read_recovery.dart), by Meta's advice
 * ([threadsRetryOf]): a passing failure once after each of the growing
 * [delays]; one Meta asked to wait out once, a second after its reset, when
 * that comes within [longestWait] (a longer wait is the reader's to sit out,
 * told why); one only the reader can mend never.
 */
class ThreadsRetryBudget(private val delays: List<Long> = DELAYS_MILLIS, private val longestWait: Long = delays.last()) {
    private var attempts = 0
    private var resetSpent = false

    /** How long to wait before [error]'s read is tried again at [now] (epoch milliseconds), that try spent; null when it is not tried again. */
    fun waitFor(error: Throwable, now: Long): Long? = when (threadsRetryOf(error)) {
        ThreadsRetry.BACKOFF -> delays.getOrNull(attempts)?.also { attempts++ }
        ThreadsRetry.AT_RESET -> resetWait(error, now)?.also { resetSpent = true }
        ThreadsRetry.MANUAL -> null
    }

    private fun resetWait(error: Throwable, now: Long): Long? {
        if (resetSpent) return null
        val reset = (error as? ThreadsException)?.retryAt?.toEpochMilli() ?: return null
        return maxOf(SECOND, reset - now + SECOND).takeIf { it <= longestWait }
    }

    companion object {
        /** XTA's readRetryDelays. */
        val DELAYS_MILLIS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)

        private const val SECOND = 1_000L

        /**
         * [read], tried again as Meta advises and a new budget allows, waiting
         * with [wait]. A failure the budget does not wait for is thrown, and
         * so is one met while [online] says there is no connection: the
         * connection coming back is what reads again then.
         */
        suspend fun <T> retrying(
            online: suspend () -> Boolean,
            clock: () -> Long = System::currentTimeMillis,
            wait: suspend (Long) -> Unit = { delay(it) },
            read: suspend () -> T,
        ): T {
            val budget = ThreadsRetryBudget()
            while (true) {
                val failure = try {
                    return read()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
                if (!online()) throw failure
                wait(budget.waitFor(failure, clock()) ?: throw failure)
            }
        }
    }
}
