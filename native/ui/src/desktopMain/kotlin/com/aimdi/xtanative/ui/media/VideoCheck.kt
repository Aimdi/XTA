package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CI's check that videos play in the desktop app, as [com.aimdi.xtanative.ui.speech.VoiceCheck]
 * is the voices': the desktop job's runner (a virtual display, no sound
 * card) asks for it with XTA_NATIVE_VIDEO_CHECK naming a video, which then
 * plays over the window in the viewer's player and under its controls,
 * and then again as a podcast plays, sound alone. Each step is a line in
 * the log, the last frame stays for the screenshot, and without libmpv it
 * says only that videos open in the system's player.
 */
object VideoCheck {
    private val asked = AtomicReference<String?>(null)

    fun ask(url: String) = asked.set(url)

    /** Over the window while the check runs; nothing when it was not asked for. */
    @Composable
    fun Shown() {
        val url = remember { asked.getAndSet(null) } ?: return
        if (!MpvEngine.ready) {
            LaunchedEffect(Unit) {
                say("engine missing: ${(MpvEngine.state as? MpvEngineState.Missing)?.reason}")
                say("done")
            }
            return
        }
        val playback = remember { MpvVideoPlayback(ViewerVideo(url, emptyList(), gif = false, loop = false, muted = false, prefetchSeconds = 0)) }
        DisposableEffect(playback) { onDispose { playback.release() } }
        LaunchedEffect(playback) { run(url, playback) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            EngineVideo(playback, poster = null, alt = null, controls = true, onDownload = null)
        }
    }

    private fun say(line: String) = println("XTA Native: video check: $line")

    private suspend fun run(url: String, playback: MpvVideoPlayback) {
        (MpvEngine.state as? MpvEngineState.Ready)?.let { say("engine ${it.library}, client API ${MpvEngines.versionText(it.apiVersion)}") }
        say("playing $url")
        val asked = System.nanoTime()
        playback.setActive(true)
        val first = withTimeoutOrNull(START_MS) { playback.state.first { it.started || it.failed } }
        if (first?.started != true) {
            say(if (first?.failed == true) "the video failed before its first frame" else "no first frame within ${START_MS / 1000} s")
            say("done")
            return
        }
        say("first frame ${playback.property("dwidth")}x${playback.property("dheight")} after ${(System.nanoTime() - asked) / 1_000_000} ms")
        val end = withTimeoutOrNull(PLAY_MS) { playback.state.first { it.completed || it.failed } }
        val now = playback.state.value
        say("frames ${playback.frames?.handedCount} drawn, ${playback.frames?.shownCount} shown; at ${now.positionMs} ms of ${now.durationMs} ms; " + if (end?.completed == true) "completed" else "not completed")
        say("sound through ${playback.property("current-ao") ?: "no output"}")
        podcast(url)
        say("done")
    }

    /** The same file as a podcast plays it: sound alone, through the continuity port. */
    private suspend fun podcast(url: String) {
        val player = MpvContinuityPlayer.create()
        try {
            player.open(url)
            player.play()
            withTimeoutOrNull(PLAY_MS) { player.changes.first { player.frame.failed || player.frame.positionMs >= HEARD_MS } }
            val frame = player.frame
            say(if (frame.failed) "podcast failed" else "podcast at ${frame.positionMs} ms of ${frame.durationMs} ms, " + if (frame.playing) "playing" else "not playing")
            player.stop()
        } finally {
            player.dispose()
        }
    }

    private const val START_MS = 30_000L
    private const val PLAY_MS = 60_000L
    private const val HEARD_MS = 1_500L
}
