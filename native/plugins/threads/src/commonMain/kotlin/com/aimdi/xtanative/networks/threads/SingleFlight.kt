package com.aimdi.xtanative.networks.threads

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One read per key at a time: a caller asking for what another is already
 * reading waits for that answer instead of asking Meta again. Should the
 * reading caller be cancelled, a waiting one reads in its place.
 */
internal class SingleFlight<T> {
    private val inFlight = HashMap<String, CompletableDeferred<T>>()

    suspend fun run(key: String, read: suspend () -> T): T {
        while (true) {
            val (answer, reading) = synchronized(inFlight) {
                inFlight[key]?.let { it to false } ?: CompletableDeferred<T>().also { inFlight[key] = it }.let { it to true }
            }
            if (!reading) {
                try {
                    return answer.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }
            val outcome = runCatching { read() }
            synchronized(inFlight) { inFlight.remove(key) }
            outcome.onSuccess { answer.complete(it) }.onFailure { answer.completeExceptionally(it) }
            return outcome.getOrThrow()
        }
    }
}
