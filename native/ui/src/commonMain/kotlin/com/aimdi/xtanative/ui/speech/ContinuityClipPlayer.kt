package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Plays synthesised clips through the podcast player's port
 * (clip_player.dart's ContinuityClipPlayer): Media3's player on Android. It
 * is made on first use, on [context] (Android's main thread, which the player
 * wants), so nothing loads a player until a downloaded voice speaks. A clip
 * that has not started within [startTimeoutMs] counts as failed.
 */
class ContinuityClipPlayer(
    private val create: () -> ContinuityPlayer,
    private val startTimeoutMs: Long = 10_000,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : ClipPlayer {
    private val making = Mutex()
    private var player: ContinuityPlayer? = null
    private val pending = AtomicReference<CompletableDeferred<UtteranceOutcome>?>(null)

    override suspend fun play(path: String): UtteranceOutcome {
        settle(UtteranceOutcome.CANCELLED)
        val player = player()
        val mine = CompletableDeferred<UtteranceOutcome>().also { pending.set(it) }
        return coroutineScope {
            val started = AtomicBoolean(false)
            // Listening before the player starts, so no change of its is missed.
            launch(start = CoroutineStart.UNDISPATCHED) {
                player.changes.collect {
                    val frame = player.frame
                    if (frame.playing) started.set(true)
                    if (frame.failed) settle(UtteranceOutcome.FAILED, mine)
                    if (started.get() && frame.completed) settle(UtteranceOutcome.COMPLETED, mine)
                }
            }
            launch {
                delay(startTimeoutMs)
                if (!started.get()) settle(UtteranceOutcome.FAILED, mine)
            }
            // Not waited for: a player that never answers is the watchdog's business.
            launch { if (!start(player, path)) settle(UtteranceOutcome.FAILED, mine) }
            mine.await().also { coroutineContext.cancelChildren() }
        }
    }

    private suspend fun player(): ContinuityPlayer = making.withLock { player ?: withContext(context) { create() }.also { player = it } }

    private suspend fun start(player: ContinuityPlayer, path: String): Boolean = try {
        withContext(context) {
            player.open(path)
            player.play()
        }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    /** Ends the clip being waited for, or, given [only], only when it is that one. */
    private fun settle(outcome: UtteranceOutcome, only: CompletableDeferred<UtteranceOutcome>? = null) {
        val current = pending.get() ?: return
        if (only != null && current !== only) return
        if (pending.compareAndSet(current, null)) current.complete(outcome)
    }

    override suspend fun stop() {
        settle(UtteranceOutcome.CANCELLED)
        val player = player ?: return
        try {
            withContext(context) { player.stop() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }
}
