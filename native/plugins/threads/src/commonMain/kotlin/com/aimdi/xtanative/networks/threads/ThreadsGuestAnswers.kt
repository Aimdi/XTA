package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Shares identical guest requests (the same page, the same GraphQL form)
 * between the screens that ask them, so Meta is asked once:
 *
 * - a request already on its way is joined, never sent twice: by a read of
 *   the same lane at once, by a read of the other lane once it has left (a
 *   read the reader waits on never waits behind Home's queue);
 * - an answer one lane was given in the last [RECENT_MILLIS] serves the other
 *   lane: Home takes what an opened profile was just answered, and a profile
 *   what Home was just answered. A lane's own repeat (a pull to refresh, a
 *   retry) is asked again.
 *
 * Only guest reads come here: a session read is never shared or reused.
 */
internal class ThreadsGuestAnswers(private val clock: () -> Long) {
    private class Leaving(val background: Boolean, val answer: CompletableDeferred<XResponse> = CompletableDeferred()) {
        @Volatile
        var departed = false
    }

    private class Answered(val at: Long, val background: Boolean, val response: XResponse)

    private val leaving = HashMap<String, Leaving>()
    private val answered = LinkedHashMap<String, Answered>()

    /** How many requests were answered without being sent: joined on their way, or taken from the other lane's recent answer. */
    @Volatile
    var shared = 0
        private set

    /**
     * [request]'s answer for [key], sent by this caller once [depart] lets it
     * leave unless another read of it is joined or its answer reused.
     */
    suspend fun send(key: String, background: Boolean, depart: suspend () -> Unit, request: suspend () -> XResponse): XResponse {
        while (true) {
            when (val found = find(key, background, registering = true)) {
                is Found.Reuse -> return found.response
                is Found.Join -> joined(found.leaving)?.let { return it }
                is Found.Own -> leave(key, background, found.leaving, depart, request)?.let { return it }
            }
        }
    }

    /** Sends [key]'s request as this caller's own, tracked as [mine] when others may join it; null when the read it joined was given up. */
    private suspend fun leave(key: String, background: Boolean, mine: Leaving?, depart: suspend () -> Unit, request: suspend () -> XResponse): XResponse? {
        try {
            depart()
            mine?.departed = true
            // While this one waited its turn, the other lane may have sent the same request or been answered it.
            when (val found = if (mine == null) find(key, background, registering = false) else recent(key, background)?.let { Found.Reuse(it) }) {
                is Found.Reuse -> return settle(key, background, mine, found.response, keep = false)
                is Found.Join -> return joined(found.leaving)
                else -> Unit
            }
            return settle(key, background, mine, request(), keep = true)
        } catch (e: Throwable) {
            synchronized(this) { if (mine != null && leaving[key] === mine) leaving.remove(key) }
            if (e is CancellationException) mine?.answer?.cancel(e) else mine?.answer?.completeExceptionally(e)
            throw e
        }
    }

    private fun settle(key: String, background: Boolean, mine: Leaving?, response: XResponse, keep: Boolean): XResponse {
        synchronized(this) {
            if (mine != null && leaving[key] === mine) leaving.remove(key)
            if (keep && response.code == 200) {
                answered.remove(key)
                answered[key] = Answered(clock(), background, response)
                while (answered.size > KEPT) answered.remove(answered.keys.first())
            }
        }
        mine?.answer?.complete(response)
        return response
    }

    /** [leaving]'s answer; null when the caller that sent it was cancelled, so this one asks in its place. */
    private suspend fun joined(leaving: Leaving): XResponse? = try {
        leaving.answer.await().also { synchronized(this) { shared++ } }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }

    private sealed interface Found {
        class Reuse(val response: XResponse) : Found
        class Join(val leaving: Leaving) : Found
        class Own(val leaving: Leaving?) : Found
    }

    private fun find(key: String, background: Boolean, registering: Boolean): Found = synchronized(this) {
        recent(key, background)?.let { return Found.Reuse(it) }
        val going = leaving[key]
        when {
            going != null && (going.departed || going.background == background) -> Found.Join(going)
            going != null || !registering -> Found.Own(null)
            else -> Found.Own(Leaving(background).also { leaving[key] = it })
        }
    }

    /** The other lane's answer to [key] from the last [RECENT_MILLIS]; null when there is none that fresh. */
    private fun recent(key: String, background: Boolean): XResponse? = synchronized(this) {
        val kept = answered[key]?.takeIf { it.background != background && clock() - it.at in 0..RECENT_MILLIS } ?: return null
        shared++
        kept.response
    }

    companion object {
        /** How long one lane's answer serves the other. */
        const val RECENT_MILLIS = 60_000L

        /** How many answers are held for that minute: a page is large, and only the last few are asked again. */
        private const val KEPT = 8
    }
}
