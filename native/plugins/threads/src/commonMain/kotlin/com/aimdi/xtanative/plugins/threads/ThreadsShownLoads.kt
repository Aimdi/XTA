package com.aimdi.xtanative.plugins.threads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Loads that end with the screen that started them: Home's feed is read in
 * the plugin's scope, so that it outlives a rotation, but once Home is no
 * longer shown the accounts still waiting are not asked. A screen gone for
 * less than [graceMillis] (a rotation) is not gone. A load started while
 * Home is not shown (a follow from a profile) runs to its end.
 */
internal class ThreadsShownLoads(private val graceMillis: Long = GRACE_MILLIS) {
    private val shown = MutableStateFlow(0)

    /** Home is shown; the answer is called when it no longer is. */
    fun show(): () -> Unit {
        shown.update { it + 1 }
        return { shown.update { count -> maxOf(0, count - 1) } }
    }

    /** [read], given up when the screen that was shown as it started goes; null then. */
    suspend fun <T> whileShown(read: suspend () -> T): T? {
        if (shown.value == 0) return read()
        return coroutineScope {
            val reading = async { read() }
            val watch = launch {
                do {
                    shown.first { it == 0 }
                    delay(graceMillis)
                } while (shown.value > 0)
                reading.cancel()
            }
            try {
                reading.await()
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                null
            } finally {
                watch.cancel()
            }
        }
    }

    private companion object {
        const val GRACE_MILLIS = 2_000L
    }
}
