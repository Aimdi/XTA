package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.InstalledVoice
import com.aimdi.xtanative.services.speech.OfflineVoices
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.services.speech.VoiceArchives
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceSynthesizer
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SpeechPartsTest {
    private class Synthesis(private val clips: File) : VoiceSynthesis {
        val loads = AtomicInteger()
        val disposals = AtomicInteger()
        val played = AtomicInteger()

        override suspend fun load(installed: InstalledVoice, engineFolder: File?): VoiceSynthesizer {
            loads.incrementAndGet()
            return object : VoiceSynthesizer {
                override suspend fun synthesize(text: String, speed: Double): String = File.createTempFile("clip", ".wav", clips).path

                override suspend fun dispose() {
                    disposals.incrementAndGet()
                }
            }
        }

        override val player: ClipPlayer = object : ClipPlayer {
            override suspend fun play(path: String): UtteranceOutcome {
                played.incrementAndGet()
                return UtteranceOutcome.COMPLETED
            }

            override suspend fun stop() {}
        }
    }

    /** The system's text-to-speech, as Android has it. */
    private class SystemEngine : SpeechEngine {
        val spoken = AtomicInteger()
        override val maxChunkChars = 3500

        override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean = true

        override suspend fun say(chunk: String): UtteranceOutcome {
            spoken.incrementAndGet()
            return UtteranceOutcome.COMPLETED
        }

        override suspend fun stop() {}
    }

    private class Platform(override val engine: SpeechEngine?, override val synthesis: VoiceSynthesis, override val voiceFolder: File) : SpeechPlatform {
        override val voices: SystemVoices? = null
        override val session: SpeechSession = object : SpeechSession {}
        override val voiceEngine: VoiceEnginePackage? = null
        override val engineBundled: Boolean = true
    }

    private fun folder(name: String): File = Files.createTempDirectory(name).toFile()

    /** Every catalogue voice's files in place, as a finished download leaves them. */
    private fun installedVoices(): File = folder("voices").also { root ->
        OfflineVoices.CATALOG.forEach { voice ->
            val directory = VoiceArchives.voiceDirectory(File(root, "voices"), voice)
            voice.requiredFiles.forEach { File(directory, it).apply { parentFile.mkdirs(); writeText("x") } }
        }
    }

    private suspend fun turnedOff(): SettingsStore = SettingsStore(StoreDir(folder("settings"))).also { it.set(SpeechPrefs.offlineVoice, false) }

    private suspend fun until(condition: () -> Boolean) {
        while (!condition()) delay(5)
    }

    private fun timed(block: suspend () -> Unit): Unit = runBlocking { withTimeout(10_000) { block() } }

    @Test
    fun theDesktopReadsWithADownloadedVoiceThoughTheStoredSettingIsOff(): Unit = timed {
        val synthesis = Synthesis(folder("clips"))
        val settings = turnedOff()
        val parts = SpeechParts.of(settings) { Platform(engine = null, synthesis, installedVoices()) }
        parts.speech.speak("Maya", "Hello, how are you today?")
        until { synthesis.played.get() > 0 }
        assertEquals(1, synthesis.loads.get())
        settings.set(SpeechPrefs.offlineVoice, true)
        settings.set(SpeechPrefs.offlineVoice, false)
        delay(200)
        assertEquals(0, synthesis.disposals.get(), "the setting being off must not let go of the only voice that reads")
    }

    @Test
    fun androidKeepsTheSettingAndReadsWithTheSystemEngine(): Unit = timed {
        val synthesis = Synthesis(folder("clips"))
        val system = SystemEngine()
        val parts = SpeechParts.of(turnedOff()) { Platform(engine = system, synthesis, installedVoices()) }
        parts.speech.speak("Maya", "Hello, how are you today?")
        until { system.spoken.get() > 0 }
        assertEquals(0, synthesis.loads.get())
        assertTrue(synthesis.played.get() == 0)
    }
}
