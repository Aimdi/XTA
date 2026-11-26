package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * libmpv as the continuity port (continuity_player.dart's
 * MediaKitContinuityPlayer, which XTA's podcasts and read-aloud clips play
 * through on Linux): sound alone, through the system's output, or none
 * where there is no sound card. A player mpv would not make is one that
 * failed, so its callers say so rather than wait.
 */
internal class MpvContinuityPlayer private constructor(private val player: MpvPlayer?) : ContinuityPlayer {
    override val frame: PlaybackFrame get() = player?.state?.value?.frame ?: PlaybackFrame(failed = true)

    override val changes: Flow<Unit> = player?.state?.map { } ?: emptyFlow()

    /** Opened paused, as media_kit's open(play: false); it returns once the source it had is forgotten. */
    override suspend fun open(url: String, startMs: Long) {
        player?.open(url, startMs, play = false)
        player?.settled()
    }

    override suspend fun play() {
        player?.play()
    }

    override suspend fun pause() {
        player?.pause()
    }

    override suspend fun seek(positionMs: Long) {
        player?.seek(positionMs)
    }

    override suspend fun setVolume(value: Double) {
        player?.setVolume(value)
    }

    override suspend fun setRate(value: Double) {
        player?.setSpeed(value.coerceIn(0.25, 4.0))
    }

    override suspend fun stop() {
        player?.stop()
        player?.settled()
    }

    override suspend fun dispose() {
        player?.close()
    }

    /** What mpv itself says of where it stands, for the log of a check that found it not playing. */
    suspend fun standing(): String =
        listOf("pause", "idle-active", "core-idle", "eof-reached", "paused-for-cache", "time-pos", "current-ao", "audio-codec-name")
            .map { name -> "$name=${player?.property(name)}" }.joinToString(", ")

    companion object {
        fun create(): ContinuityPlayer = MpvContinuityPlayer(MpvPlayer.create(MpvOptions.AUDIO, video = false, name = "mpv-audio"))
    }
}
