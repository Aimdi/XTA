package com.aimdi.xtanative.services.util

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the last of a burst of calls made under one id, once a quiet spell
 * has passed without another, as XTA's Debouncer and its debounce timers do:
 * a new call under the same id replaces the waiting one.
 */
class Debouncer(private val scope: CoroutineScope) {
    private val waiting = ConcurrentHashMap<String, Job>()

    fun debounce(id: String, delayMillis: Long, action: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            waiting.remove(id, coroutineContext[Job])
            action()
        }
        waiting.put(id, job)?.cancel()
        job.start()
    }

    /** Drops the call waiting under [id], if there is one. */
    fun cancel(id: String) {
        waiting.remove(id)?.cancel()
    }

    /** Whether a call is waiting under [id]. */
    fun isWaiting(id: String): Boolean = waiting[id]?.isActive == true

    companion object {
        /** Typing in search: the query is sent once the reader pauses (search.dart). */
        const val SEARCH_MILLIS = 750L

        /** The place in an article is written once scrolling settles (article_reading_store.dart). */
        const val READING_PLACE_MILLIS = 400L
    }
}
