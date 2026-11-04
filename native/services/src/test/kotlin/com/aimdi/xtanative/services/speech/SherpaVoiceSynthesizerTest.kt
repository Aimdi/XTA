package com.aimdi.xtanative.services.speech

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SherpaVoiceSynthesizerTest {
    private val folder: File = Files.createTempDirectory("sherpa-voice").toFile()
    private val clips = File(folder, "clips")
    private val voice = InstalledVoice(OfflineVoices.byId("de-thorsten-emotional")!!, File(folder, "de"))

    /** sherpa-onnx as the test sets it: what it was asked, on which thread, and a model it can refuse. */
    private class Engine(private val model: Long = 7, private val samples: Int = 22_050) : SherpaEngine {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        val threads: MutableSet<String> = Collections.synchronizedSet(HashSet())
        val configs = ArrayList<SherpaConfig>()
        var hold: CountDownLatch? = null
        val speaking = CountDownLatch(1)

        override fun create(config: SherpaConfig): Long {
            threads += Thread.currentThread().name
            configs += config
            calls += "create"
            hold?.await(5, TimeUnit.SECONDS)
            return model
        }

        override fun speak(model: Long, text: String, speakerId: Int, speed: Float, out: File): Int {
            threads += Thread.currentThread().name
            speaking.countDown()
            hold?.await(5, TimeUnit.SECONDS)
            calls += "speak:$model:$text:$speakerId:$speed"
            if (samples > 0) out.writeText(text)
            return samples
        }

        override fun destroy(model: Long) {
            calls += "destroy:$model"
        }
    }

    private suspend fun load(engine: Engine): VoiceSynthesizer = SherpaVoiceSynthesizer.load(voice, clips) { engine }

    @Test
    fun aVoiceLoadsWithItsFilesAndSpeaksIntoClips(): Unit = runBlocking {
        clips.mkdirs()
        File(clips, "left-behind.wav").writeText("old")
        val engine = Engine()
        val synthesizer = load(engine)
        val path = synthesizer.synthesize("Guten Tag.", 1.5)

        assertFalse(File(clips, "left-behind.wav").exists())
        assertEquals(clips, File(path).parentFile)
        assertEquals("Guten Tag.", File(path).readText())
        val config = engine.configs.single()
        assertEquals(File(voice.path, "de_DE-thorsten_emotional-medium.onnx").path, config.model)
        assertEquals(File(voice.path, "espeak-ng-data").path, config.dataDir)
        assertEquals(2, config.threads)
        assertEquals(listOf("create", "speak:7:Guten Tag.:4:1.5"), engine.calls)
        assertEquals(setOf("offline-voice"), engine.threads)
    }

    @Test
    fun requestsAreAnsweredInOrderOnTheVoicesOwnThread(): Unit = runBlocking {
        val engine = Engine()
        val synthesizer = load(engine)
        val texts = (1..6).map { "Satz $it." }
        val paths = texts.map { async(Dispatchers.Default) { synthesizer.synthesize(it, 1.0) } }.awaitAll()

        assertEquals(texts, paths.map { File(it).readText() })
        assertEquals(paths.size, paths.toSet().size)
        assertEquals(setOf("offline-voice"), engine.threads)
    }

    @Test
    fun aVoiceTheEngineCannotLoadFails(): Unit = runBlocking {
        assertFailsWith<IllegalStateException> { load(Engine(model = 0)) }
        assertFailsWith<IllegalStateException> { SherpaVoiceSynthesizer.load(voice, clips) { throw IllegalStateException("no engine") } }
    }

    @Test
    fun aSentenceThatMakesNoSoundFailsAndLeavesNoClip(): Unit = runBlocking {
        val synthesizer = load(Engine(samples = 0))
        assertFailsWith<IllegalStateException> { synthesizer.synthesize("…", 1.0) }
        assertEquals(0, clips.list()?.size)
    }

    @Test
    fun unloadingWaitsForTheSentenceUnderWayAndRefusesTheRest(): Unit = runBlocking {
        val engine = Engine()
        val synthesizer = load(engine)
        val hold = CountDownLatch(1).also { engine.hold = it }
        val first = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { synthesizer.synthesize("Eins.", 1.0) }
        val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { runCatching { synthesizer.synthesize("Zwei.", 1.0) } }
        assertTrue(engine.speaking.await(5, TimeUnit.SECONDS))
        synthesizer.dispose()
        synthesizer.dispose()
        hold.countDown()

        assertEquals("Eins.", File(first.await()).readText())
        assertTrue(second.await().isFailure)
        assertTrue(runCatching { synthesizer.synthesize("Drei.", 1.0) }.isFailure)
        withTimeout(5_000) { while ("destroy:7" !in engine.calls) delay(5) }
        assertEquals(listOf("create", "speak:7:Eins.:4:1.0", "destroy:7"), engine.calls)
    }

    @Test
    fun aLoadGivenUpOnFreesTheModelOnceItIsThere(): Unit = runBlocking {
        val engine = Engine()
        val hold = CountDownLatch(1).also { engine.hold = it }
        val loading = async(Dispatchers.Default) { load(engine) }
        withTimeout(5_000) { while ("create" !in engine.calls) delay(5) }
        loading.cancel()
        hold.countDown()

        withTimeout(5_000) { while ("destroy:7" !in engine.calls) delay(5) }
        assertEquals(listOf("create", "destroy:7"), engine.calls)
    }

    @Test
    fun theShimReadsNulTerminatedUtf8AndNumbersTheKindsAsItDoes() {
        assertContentEquals(byteArrayOf(0x47, 0xc3.toByte(), 0xbc.toByte(), 0), SherpaJni.cString("Gü"))
        assertEquals(0, SherpaJni.kindOf(VoiceModelKind.VITS))
        assertEquals(1, SherpaJni.kindOf(VoiceModelKind.KOKORO))
    }
}
