package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** How a read ended, in XTA's words (read_activity.dart's ReadOutcome). */
enum class XReadOutcome(val word: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    FAILED("failed"),
    TIMED_OUT("timedOut"),
    CANCELLED("cancelled"),
}

/**
 * The reads asked of X lately, as XTA's ReadActivityLog keeps them for
 * Diagnostics' "Recent read operations": which read, when it started, how it
 * ended (a failure by its category) and how long it took. Only that: never
 * what was asked for, what X answered, nor an error's text. The [capacity]
 * newest are kept, in the order they started.
 */
class XReadLog(private val capacity: Int = 60, private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val what: String, val startedAt: Long, private val started: Long) {
        @Volatile var outcome = XReadOutcome.PENDING
        @Volatile var failure: String? = null
        @Volatile private var ended: Long? = null

        fun end(how: XReadOutcome, why: String? = null) {
            if (outcome != XReadOutcome.PENDING) return
            failure = why
            ended = System.nanoTime()
            outcome = how
        }

        fun line(): String {
            val millis = ((ended ?: System.nanoTime()) - started) / 1_000_000
            return "${Instant.ofEpochMilli(startedAt)} $what ${outcome.word}${failure?.let { " ($it)" }.orEmpty()} ${millis}ms"
        }
    }

    private val entries = ArrayDeque<Entry>()

    /** [read] run as one read named [what] (an endpoint's name), and kept with how it ended. */
    suspend fun <T> track(what: String, read: suspend () -> T): T {
        val entry = begin(what)
        try {
            return read().also { entry.end(XReadOutcome.COMPLETED) }
        } catch (error: Throwable) {
            when (error) {
                is CancellationException -> entry.end(XReadOutcome.CANCELLED)
                is InterruptedIOException -> entry.end(XReadOutcome.TIMED_OUT)
                else -> entry.end(XReadOutcome.FAILED, failureOf(error))
            }
            throw error
        }
    }

    /** One line a read, oldest first, as XTA's report lists them. */
    @Synchronized
    fun snapshot(): List<String> = entries.map { it.line() }

    @Synchronized
    private fun begin(what: String): Entry = Entry(what, clock(), System.nanoTime()).also { entry ->
        if (capacity <= 0) return@also
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    companion object {
        /**
         * A failure's category for the report, with X's HTTP status where it
         * answered one (`rateLimited 429`, `unavailable 403`), as XTA's
         * readFailureLabel writes it; anything else names its type.
         */
        fun failureOf(error: Throwable): String = when (error) {
            is XRateLimitedException -> "rateLimited"
            is XNoAccountException -> "session"
            is XEndpointRefusedException -> "endpointRefused"
            is XRequestException -> statusOf(error)
            is XAnswerException -> if (error.errorCode in SESSION_ERRORS) "session" else unknown(error)
            is SigningUnavailableException -> "transactionUnavailable"
            is InterruptedIOException -> "timedOut"
            is IOException -> "connection"
            else -> unknown(error)
        }

        private fun statusOf(error: XRequestException): String = when (error.code) {
            429 -> "rateLimited 429"
            401 -> "session 401"
            500, 502, 503, 504 -> "serviceUnavailable ${error.code}"
            403, 404 -> "unavailable ${error.code}"
            else -> unknown(error)
        }

        private fun unknown(error: Throwable) = "unknown ${error::class.simpleName}"

        /** X's own codes for a sign-in it no longer takes (XTA's read_failure_kind.dart). */
        private val SESSION_ERRORS = setOf(32, 89, 215)
    }
}
