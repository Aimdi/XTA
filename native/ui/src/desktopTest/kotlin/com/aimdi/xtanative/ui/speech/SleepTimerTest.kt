package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechReader
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SettingsStore
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SleepTimerTest {
    private val minute = 60_000L

    @Test
    fun aTimerIsDueWhenItsMinutesHavePassedAndNotBefore() {
        val timer = SleepTimer.of(15, now = 1_000)
        assertEquals(SleepTimer(1_000 + 15 * minute), timer)
        assertFalse(timer.isDue(1_000))
        assertFalse(timer.isDue(1_000 + 15 * minute - 1))
        assertTrue(timer.isDue(1_000 + 15 * minute))
        assertTrue(timer.isDue(Long.MAX_VALUE))
    }

    @Test
    fun theMinutesShownCountAMinuteBegunSoTheLastMinuteReadsOne() {
        val timer = SleepTimer.of(30, now = 0)
        assertEquals(30, timer.minutesLeft(0))
        assertEquals(30, timer.minutesLeft(1))
        assertEquals(29, timer.minutesLeft(minute))
        assertEquals(1, timer.minutesLeft(30 * minute - 1))
        assertEquals(0, timer.minutesLeft(30 * minute))
        assertEquals(0, timer.minutesLeft(31 * minute))
    }

    @Test
    fun itsKeeperWaitsUntilTheMinutesChangeOrTheTimerIsDue() {
        val timer = SleepTimer.of(15, now = 0)
        assertEquals(minute, timer.nextChange(0))
        assertEquals(minute - 20_000, timer.nextChange(20_000))
        assertEquals(1, timer.nextChange(15 * minute - 1))
        assertEquals(0, timer.nextChange(15 * minute))
        // Waiting as long as told, from any moment, lands exactly on the end and never past it.
        var now = 12_345L
        var waits = 0
        while (!timer.isDue(now)) {
            now += timer.nextChange(now)
            waits++
        }
        assertEquals(15 * minute, now)
        assertEquals(15, waits)
    }

    @Test
    fun theBarOffersAQuarterHalfAndWholeHour() {
        assertEquals(listOf(15, 30, 60), SleepTimer.CHOICES)
    }

    private class Engine : SpeechEngine {
        val spoken = ArrayList<String>()
        var hold: CompletableDeferred<Unit>? = CompletableDeferred()
        private var current: CompletableDeferred<UtteranceOutcome>? = null
        override val maxChunkChars = 3500

        override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean = true

        override suspend fun say(chunk: String): UtteranceOutcome {
            spoken += chunk
            val held = hold ?: return UtteranceOutcome.COMPLETED
            val utterance = CompletableDeferred<UtteranceOutcome>().also { current = it }
            held.invokeOnCompletion { utterance.complete(UtteranceOutcome.COMPLETED) }
            return utterance.await()
        }

        override suspend fun stop() {
            current?.complete(UtteranceOutcome.CANCELLED)
        }
    }

    private fun settings() = SettingsStore(StoreDir(Files.createTempDirectory("sleep-test").toFile()))

    private fun timed(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun until(condition: () -> Boolean) {
        while (!condition()) delay(5)
    }

    /** A speech whose time passes only as [minutes] are let through: each wait of its timer takes one. */
    private class Timed(engine: Engine, settings: SettingsStore) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val now = AtomicLong()
        val minutes = Channel<Unit>(Channel.UNLIMITED)
        val waiting = AtomicInteger()
        val abandoned = AtomicInteger()
        val speech = AppSpeech(
            SpeechReader(listOf(engine)), settings, scope, hasEngine = true,
            clock = now::get,
            wait = { millis ->
                waiting.incrementAndGet()
                try {
                    minutes.receive()
                } catch (error: CancellationException) {
                    abandoned.incrementAndGet()
                    throw error
                }
                now.addAndGet(millis)
            },
        )
    }

    @Test
    fun whenTheTimeIsUpTheReadingIsHeldNotStoppedAndTheTimerIsGone(): Unit = timed {
        val engine = Engine()
        val timed = Timed(engine, settings())
        val speech = timed.speech
        speech.speak("Maya", "Words to fall asleep to.")
        until { speech.playback.value.speaking }
        speech.sleepIn(15)
        assertEquals(15, speech.sleepMinutes.value)
        timed.minutes.send(Unit)
        until { speech.sleepMinutes.value == 14 }
        assertFalse(speech.playback.value.paused)
        repeat(14) { timed.minutes.send(Unit) }
        until { speech.playback.value.paused }
        assertNull(speech.sleepMinutes.value)
        assertTrue(speech.isReading("Maya"), "the reading is still at hand, to go on with")
        assertEquals(15 * 60_000L, timed.now.get())
        engine.hold = null
        speech.resume()
        until { !speech.playback.value.speaking }
        assertEquals(listOf("Words to fall asleep to.", "Words to fall asleep to."), engine.spoken)
        timed.scope.cancel()
    }

    @Test
    fun stoppingTheReadingTurnsTheTimerOff(): Unit = timed {
        val timed = Timed(Engine(), settings())
        val speech = timed.speech
        speech.speak("Maya", "Words.")
        until { speech.playback.value.speaking }
        speech.sleepIn(30)
        assertEquals(30, speech.sleepMinutes.value)
        until { timed.waiting.get() == 1 }
        speech.stop()
        assertNull(speech.sleepMinutes.value)
        until { timed.abandoned.get() == 1 }
        until { !speech.playback.value.speaking }
        timed.scope.cancel()
    }

    @Test
    fun aReadingThatEndsOrIsReplacedLeavesNoTimerAndOffTurnsItOff(): Unit = timed {
        val engine = Engine()
        val timed = Timed(engine, settings())
        val speech = timed.speech
        speech.speak("Maya", "Words.")
        until { speech.playback.value.speaking }
        speech.sleepIn(60)
        speech.sleepIn(null)
        assertNull(speech.sleepMinutes.value)
        speech.sleepIn(60)
        speech.speak("Noor", "Other words.")
        assertNull(speech.sleepMinutes.value, "another reading starts without the timer of the one before")
        until { speech.isReading("Noor") }
        speech.sleepIn(15)
        assertEquals(15, speech.sleepMinutes.value)
        engine.hold!!.complete(Unit)
        until { !speech.playback.value.speaking }
        until { speech.sleepMinutes.value == null }
        timed.scope.cancel()
    }

    @Test
    fun aNewTimeReplacesTheOneRunningAndNothingReadTakesNoTimer(): Unit = timed {
        val timed = Timed(Engine(), settings())
        val speech = timed.speech
        speech.sleepIn(15)
        assertNull(speech.sleepMinutes.value, "nothing is read")
        speech.speak("Maya", "Words.")
        until { speech.playback.value.speaking }
        speech.sleepIn(15)
        speech.sleepIn(60)
        assertEquals(60, speech.sleepMinutes.value)
        timed.minutes.send(Unit)
        until { speech.sleepMinutes.value == 59 }
        repeat(20) { timed.minutes.send(Unit) }
        until { speech.sleepMinutes.value == 39 }
        assertFalse(speech.playback.value.paused, "the first timer's quarter of an hour is long past, and it holds nothing")
        speech.stop()
        timed.scope.cancel()
    }
}
