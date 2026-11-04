package com.aimdi.xtanative.ui.media

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A libmpv player and where it stands ([state]). What the app asks of it
 * runs in order on the player's thread, where its state changes too, each
 * change reaching [onChange] there with the state before it. A source opens
 * with [open], which answers the number its state and frames know it by.
 */
internal class MpvPlayer private constructor(options: List<Pair<String, String>>, video: Boolean, name: String) {
    private val mutable = MutableStateFlow(MpvState())
    val state: StateFlow<MpvState> = mutable.asStateFlow()
    private val loads = AtomicLong(0)

    @Volatile
    var onChange: ((before: MpvState, after: MpvState) -> Unit)? = null

    /** Hears a source's first frame become drawable (it is [MpvState.ready] and its size known): its frames, to draw it at once, paused or not. */
    @Volatile
    var onReady: (() -> Unit)? = null

    val mpv: Mpv? = Mpv.create(options, name, video, ::received)?.also { mpv ->
        mpv.post { player -> MpvProperty.entries.forEach { player.observe(it.id, it.key, it.format) } }
    }

    /** Opens [url] from [startMs], playing at once when [play]; the source's number. */
    fun open(url: String, startMs: Long, play: Boolean): Long {
        val load = loads.incrementAndGet()
        post { player ->
            change { it.opening(load, startMs) }
            player.set("pause", !play)
            player.set("start", MpvOptions.start(startMs))
            val entry = player.command("loadfile", url, "replace")
            if (entry < 0) println("XTA Native: video: mpv did not open the source: ${MpvJni.errorText(entry.toInt())}")
            change { it.opened(load, entry) }
        }
        return load
    }

    fun play() = post { it.set("pause", false) }

    fun pause() = post { it.set("pause", true) }

    fun seek(positionMs: Long) = post { player ->
        val target = positionMs.coerceAtLeast(0)
        change { it.copy(positionMs = target) }
        player.set("time-pos", target / 1000.0)
    }

    fun setSpeed(speed: Double) = post { it.set("speed", speed) }

    fun setVolume(volume: Double) = post { it.set("volume", volume.coerceIn(0.0, 100.0)) }

    fun setMuted(muted: Boolean) = post { it.set("mute", muted) }

    fun setLoop(loop: Boolean) = post { it.set("loop-file", MpvOptions.loopValue(loop)) }

    /** Shows the file's first subtitle track, or hides subtitles. */
    fun setSubtitles(shown: Boolean) = post { player ->
        val track = state.value.subtitleTrack
        if (shown && track != 0) player.set("sid", track.toString())
        player.set("sub-visibility", shown)
    }

    /** Unloads what is open: nothing plays until the next [open]. */
    fun stop() = post { player ->
        change { it.stopped() }
        player.command("stop")
    }

    /** Ends a player without video; a video's ends with its frames ([MpvFrames]), which draw with it until then. */
    fun close() {
        mpv?.close()
    }

    /** Waits (a few seconds at most) until what was asked before has run, its state changes made. */
    suspend fun settled() {
        property("idle-active")
    }

    /** What mpv says [name] is, once what was asked before has run; null when it has no value or the player ended. */
    suspend fun property(name: String): String? {
        val answer = CompletableDeferred<String?>()
        if (mpv?.post { answer.complete(it.get(name)) } != true) return null
        return withTimeoutOrNull(SETTLE_MS) { answer.await() }
    }

    private fun post(command: (Mpv) -> Unit) {
        mpv?.post(command)
    }

    /** On the player's thread only, so changes come one at a time and in order. */
    private fun change(transform: (MpvState) -> MpvState) {
        val before = mutable.value
        val after = transform(before)
        if (after == before) return
        mutable.value = after
        if (after.drawable && !before.drawable) onReady?.invoke()
        onChange?.invoke(before, after)
    }

    /**
     * An event, applied; a file loaded, its video set up and a playback
     * restart read again what a change might not have said yet.
     */
    private fun received(player: Mpv, event: MpvEvent) {
        change { it.after(event) }
        if (!state.value.started) return
        when (event.id) {
            MpvEvent.FILE_LOADED -> change { refreshed(player, it, FILE_PROPERTIES).copy(subtitleTrack = subtitleTrack(player)) }
            MpvEvent.VIDEO_RECONFIG -> change { refreshed(player, it, SIZE).copy(rotation = MpvState.rotationOf(player.get("video-dec-params/rotate"))) }
            MpvEvent.PLAYBACK_RESTART -> change { refreshed(player, it, listOf(MpvProperty.POSITION)) }
        }
    }

    companion object {
        private const val SETTLE_MS = 5_000L

        /**
         * What a new file is read again for: mpv tells only of a change, so a
         * file as long as the one before (another quality of a video) would
         * keep the length the app forgot on opening it.
         */
        private val FILE_PROPERTIES = listOf(MpvProperty.DURATION, MpvProperty.WIDTH, MpvProperty.HEIGHT, MpvProperty.POSITION)

        /**
         * The video's size, known once mpv has set its video up
         * (VIDEO_RECONFIG), which can be after its first frame; its turn is
         * the decoder's, as mpv itself turns nothing ([MpvOptions.video]).
         */
        private val SIZE = listOf(MpvProperty.WIDTH, MpvProperty.HEIGHT)

        /** A player of [options], drawing [video] or sound alone; null when mpv would not start one. */
        fun create(options: List<Pair<String, String>>, video: Boolean, name: String): MpvPlayer? =
            MpvPlayer(options, video, name).takeIf { it.mpv != null }

        private fun refreshed(player: Mpv, state: MpvState, properties: List<MpvProperty>): MpvState =
            properties.fold(state) { now, property -> now.with(property, player.get(property.key)?.toDoubleOrNull() ?: Double.NaN) }

        /** The id of the file's first subtitle track, 0 when it has none. */
        private fun subtitleTrack(player: Mpv): Int {
            val count = player.get("track-list/count")?.toIntOrNull() ?: 0
            return (0 until count).firstNotNullOfOrNull { index ->
                if (player.get("track-list/$index/type") == "sub") player.get("track-list/$index/id")?.toIntOrNull() else null
            } ?: 0
        }
    }
}
