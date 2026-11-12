package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.StateFlow

/** A platform's player as tiles take turns at it: XTA's controls' calls, and opening a video, playing it, stopping it and letting it go. */
internal interface PlayerCore : VideoPlayback {
    /** Plays [source] from its [VideoSource.startMs], in place of what it played before; it waits for [setActive] to start. */
    fun open(source: VideoSource)

    /** Plays once ready, or pauses. */
    fun setActive(active: Boolean)

    /** Stops what it plays; [open] plays another. */
    fun stop()

    fun release()
}

/**
 * One player tiles take turns at ([InlineTurns] says whose it is), made when
 * first wanted: a feed's ([shared]), kept for the next tile, or a tile's own,
 * let go once nobody wants it. While its feed is left ([pause]) what plays
 * stops, unless it is full screen or Background play ([backgroundPlay]) keeps
 * a video heard, and it plays again on return. A video that loses the
 * player, or is let go with it, is kept where it was ([places]), and opens
 * there when its turn comes again.
 */
internal class PlayerTurns<P : PlayerCore>(
    private val make: () -> P,
    private val backgroundPlay: Boolean,
    val shared: Boolean,
    private val places: VideoPlaceMemory = VideoPlaces,
) {
    private val turns = InlineTurns<Any, VideoSource>()
    private var made: P? = null
    private var opened: Any? = null
    private var openedSource: VideoSource? = null
    private var away = false
    private var resumeOnReturn = false
    private var released = false

    val holder: StateFlow<Turn<Any, VideoSource>?> get() = turns.holder

    /** In a feed ([shared]), which of its tiles is seen most, and so plays by itself. */
    val seen = MostSeen<Any>()

    /** A video wants the player now: it takes it from whoever has it. */
    fun ask(key: Any, source: VideoSource) {
        turns.ask(key, source)
        sync()
    }

    /** A GIF or a preview wants it, whenever no video has it. */
    fun want(key: Any, source: VideoSource) {
        turns.want(key, source)
        sync()
    }

    fun drop(key: Any) {
        turns.drop(key)
        sync()
    }

    /** The holder [key] went full screen: no one takes the player, and nothing lets it go, until [unpin]. */
    fun pin(key: Any) = turns.pin(key)

    fun unpin(key: Any) {
        turns.unpin(key)
        sync()
    }

    /** The player, while [key] has it. */
    fun playerOf(key: Any): P? = made?.takeIf { opened == key }

    /** Its feed or tile is gone: the player goes too, once it is no longer full screen. */
    fun release() {
        released = true
        sync()
    }

    /** The feed is no longer seen. */
    fun pause() {
        away = true
        val player = made ?: return
        val now = player.state.value
        if (turns.pinned || !now.active) return
        val heard = turns.holder.value?.value?.gif == false && !now.muted
        if (!VideoRules.pausesWhenLeft(backgroundPlay, heard)) return
        resumeOnReturn = true
        player.setActive(false)
    }

    fun resume() {
        away = false
        if (!resumeOnReturn) return
        resumeOnReturn = false
        made?.setActive(true)
    }

    /** Opens the holder's video, else stops, or lets the player go when it is no one's to keep. */
    private fun sync() {
        if (released && !turns.pinned) return letGo()
        val turn = turns.holder.value
        if (turn?.key == opened) return
        keepPlace()
        opened = turn?.key
        openedSource = turn?.value
        resumeOnReturn = false
        when {
            turn != null -> (made ?: make().also { made = it }).apply {
                open(turn.value.let { if (it.gif) it else it.copy(startMs = places.startOf(it.url)) })
                setActive(!away)
            }
            shared -> made?.stop()
            else -> letGo()
        }
    }

    private fun letGo() {
        keepPlace()
        made?.release()
        made = null
        opened = null
        openedSource = null
    }

    /** Where the video open now stands, kept for when its turn comes again; a GIF just loops from its start. */
    private fun keepPlace() {
        val source = openedSource?.takeIf { !it.gif } ?: return
        val now = made?.state?.value ?: return
        // A player still opening reads 0: what it opened with stays.
        if (now.url.isEmpty() || (!now.started && now.positionMs == 0L)) return
        places.keep(source.url, now.positionMs, now.durationMs, now.completed)
    }
}
