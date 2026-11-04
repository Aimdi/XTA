package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.VoiceDownloadStore
import com.aimdi.xtanative.services.speech.VoiceInstall
import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.AudioSessions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CI's check that downloaded voices speak, as XTA's voice-engine job proves
 * its engine on an emulator (docs/specs/downloadable-voice-engine.md): the
 * English voice downloads from the runner's server through the stores the
 * settings use (the engine first where it downloads, every pin checked),
 * loads, and reads a sentence into a clip; a reading then plays through the
 * app's reader, and the media session shows a podcast's controls for the
 * check to press. Each step is a line in the log. It runs only when a CI
 * build or start asks ([ask]): -PvoiceServer with the activity's
 * `xta.voice_check`, or XTA_NATIVE_VOICE_CHECK on the desktop.
 */
object VoiceCheck {
    /** What the check reads and names its session with: CI's data, in no build a reader gets. */
    const val SENTENCE = "The engine was downloaded, and it reads this sentence."
    private const val READING = "This is the voice check. It reads a few sentences aloud. The media session shows while it does."
    private const val TITLE = "Voice check"

    private val asked = AtomicBoolean(false)

    fun ask() = asked.set(true)

    /** Whether the check was asked for and not yet taken. */
    fun take(): Boolean = asked.getAndSet(false)

    suspend fun run(parts: SpeechParts, sessions: AudioSessions?, log: (String) -> Unit = ::println) {
        val say = { line: String -> log("XTA Native: voice check: $line") }
        val voices = parts.voices
        val english = voices.catalog.first { it.language == "en" }
        say("engine ${voices.engine.engine?.abi ?: if (voices.engine.bundled) "in the app" else "none"}, synthesis ${parts.synthesisAvailable}")
        voices.download(english)
        say("engine ${nameOf(voices.engine.state.value)}, voice ${english.id} ${nameOf(voices.installOf(english.id))}")
        say(clipBytes(parts, voices, say)?.let { "clip $it bytes" } ?: "no clip")
        read(parts.speech, say)
        if (sessions != null) controls(sessions, say) else say("no media session here")
        say("done")
    }

    fun nameOf(install: VoiceInstall): String = when (install) {
        VoiceInstall.Absent -> "absent"
        is VoiceInstall.Downloading -> "downloading"
        VoiceInstall.Installing -> "installing"
        is VoiceInstall.Ready -> "ready"
        is VoiceInstall.Failed -> "failed (${install.reason})"
    }

    /** The size of the clip the voice makes of [SENTENCE], or null when it made none. */
    private suspend fun clipBytes(parts: SpeechParts, voices: VoiceDownloadStore, say: (String) -> Unit): Long? {
        val installed = voices.installedFor("en") ?: return null
        val synthesis = parts.synthesis ?: return null
        return runCatching {
            val synthesizer = synthesis.load(installed, voices.engine.loadFrom)
            try {
                File(synthesizer.synthesize(SENTENCE, 1.0)).length()
            } finally {
                synthesizer.dispose()
            }
        }.onFailure { say("the voice did not read: $it") }.getOrNull()
    }

    /** A reading through the app's reader, from start to end, and what the bar says after it. */
    private suspend fun read(speech: AppSpeech, say: (String) -> Unit) {
        speech.speak(TITLE, READING)
        val started = withTimeoutOrNull(60_000) { speech.playback.first { it.speaking } } != null
        say(if (started) "reading" else "reading did not start: ${speech.notice.value}")
        if (!started) return
        withTimeoutOrNull(120_000) { speech.playback.first { !it.speaking } }
        say("reading ended: ${speech.notice.value ?: "as it should"}")
    }

    /** A podcast's binding, playing, until the check stops it from outside (a minute at most). */
    private suspend fun controls(sessions: AudioSessions, say: (String) -> Unit) {
        val stopped = CompletableDeferred<Unit>()
        val binding = AudioSessionBinding(
            onPlay = {
                say("played")
                sessions.update(playing = true, positionMs = 1_000, durationMs = 60_000)
            },
            onPause = {
                say("paused")
                sessions.update(playing = false, positionMs = 1_000, durationMs = 60_000)
            },
            onStop = {
                say("stopped")
                stopped.complete(Unit)
            },
        )
        sessions.bind(TITLE, artist = null, binding = binding)
        sessions.update(playing = true, positionMs = 0, durationMs = 60_000)
        say("session shown")
        if (withTimeoutOrNull(60_000) { stopped.await() } == null) say("session not stopped")
        sessions.clear()
    }
}
