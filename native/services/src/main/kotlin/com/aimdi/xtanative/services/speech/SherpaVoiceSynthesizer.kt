package com.aimdi.xtanative.services.speech

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.future.await

/**
 * The calls into sherpa-onnx's C API a loaded voice makes: [SherpaJni] in
 * the apps, a fake in tests. Each blocks, and a model is only ever used from
 * the thread that loaded it.
 */
interface SherpaEngine {
    /** Loads the voice [config] describes; 0 when it cannot. */
    fun create(config: SherpaConfig): Long

    /** Speaks [text] with [model] into a WAV file at [out]; the samples written, 0 when there were none. */
    fun speak(model: Long, text: String, speakerId: Int, speed: Float, out: File): Int

    fun destroy(model: Long)
}

/**
 * A downloaded voice loaded into sherpa-onnx (sherpa_voice_synthesizer.dart).
 * sherpa-onnx synthesises with a blocking call, so the model lives on a
 * thread of its own for as long as the voice is in use, as XTA keeps it in an
 * isolate: requests are answered in order with the path of a WAV file in
 * [clips].
 */
class SherpaVoiceSynthesizer private constructor(
    private val engine: SherpaEngine,
    private val model: Long,
    private val speakerId: Int,
    private val clips: File,
    private val thread: ExecutorService,
) : VoiceSynthesizer {
    private val lock = Any()
    private val next = AtomicInteger()

    @Volatile
    private var alive = true

    override suspend fun synthesize(text: String, speed: Double): String {
        val out = File(clips, "clip-${System.identityHashCode(this)}-${next.getAndIncrement()}.wav")
        val samples = onThread { engine.speak(model, text, speakerId, speed.toFloat(), out) }.await()
        if (samples <= 0) {
            out.delete()
            throw IllegalStateException("Synthesis failed")
        }
        return out.path
    }

    /**
     * Frees the model once what it is synthesising now is done, and ends its
     * thread; requests waiting behind it fail. Freeing it mid-synthesis
     * would pull the model from under the call.
     */
    override suspend fun dispose() {
        synchronized(lock) {
            if (!alive) return
            alive = false
            thread.execute { engine.destroy(model) }
            thread.shutdown()
        }
    }

    /** [work] queued on the voice's thread, refused once the voice is unloaded, even while it waits. */
    private fun <T> onThread(work: () -> T): CompletableFuture<T> = synchronized(lock) {
        check(alive) { STOPPED }
        CompletableFuture.supplyAsync({
            check(alive) { STOPPED }
            work()
        }, thread)
    }

    companion object {
        private const val STOPPED = "Voice unloaded"

        /**
         * Starts the voice's thread and loads [installed] on it with what
         * [engine] opens there (the native libraries load on it too); throws
         * when it cannot. [clips] is emptied first, so clips a killed process
         * left behind do not pile up (offline_speech_setup.dart).
         */
        suspend fun load(installed: InstalledVoice, clips: File, threads: Int = 2, engine: () -> SherpaEngine): VoiceSynthesizer {
            val thread = Executors.newSingleThreadExecutor { work -> Thread(work, "offline-voice").apply { isDaemon = true } }
            val loaded = AtomicReference<Pair<SherpaEngine, Long>?>()
            try {
                val (sherpa, model) = CompletableFuture.supplyAsync({ open(installed, clips, threads, engine).also(loaded::set) }, thread).await()
                check(model != 0L) { "Voice failed to load" }
                return SherpaVoiceSynthesizer(sherpa, model, installed.voice.speakerId, clips, thread)
            } catch (error: Throwable) {
                // Behind the load on the same thread: a model its caller gave up on is freed once it is there.
                thread.execute { loaded.get()?.let { (sherpa, model) -> if (model != 0L) sherpa.destroy(model) } }
                thread.shutdown()
                throw error
            }
        }

        private fun open(installed: InstalledVoice, clips: File, threads: Int, engine: () -> SherpaEngine): Pair<SherpaEngine, Long> {
            clips.deleteRecursively()
            clips.mkdirs()
            val sherpa = engine()
            return sherpa to sherpa.create(SherpaConfig.of(installed, threads))
        }
    }
}
