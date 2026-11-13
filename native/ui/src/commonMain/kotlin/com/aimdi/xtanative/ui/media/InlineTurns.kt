package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A tile's turn at a player: whose it is, and what it plays. */
internal data class Turn<K, V>(val key: K, val value: V)

/**
 * Whose a player shared by tiles is (XTA's video pool, kept here to one
 * player a feed). A video asked for last, by a tap or by coming into view,
 * takes it from whoever has it, and one that lost it waits for another ask;
 * a looping preview (a GIF) has it only while no video does, and the latest
 * preview gets it back once the video is gone, as XTA's GIFs loop on beside a
 * video. While the holder plays full screen ([pin]) no one takes it, and a
 * drop meanwhile waits for [unpin].
 */
internal class InlineTurns<K : Any, V : Any> {
    private val previews = LinkedHashMap<K, V>()
    private var video: Turn<K, V>? = null
    private val current = MutableStateFlow<Turn<K, V>?>(null)

    val holder: StateFlow<Turn<K, V>?> = current.asStateFlow()

    var pinned: Boolean = false
        private set

    /** A video wants it now: it takes it, unless the holder is full screen. */
    fun ask(key: K, value: V) {
        if (pinned) return
        previews.remove(key)
        video = Turn(key, value)
        settle()
    }

    /** A preview wants it, whenever no video has it. */
    fun want(key: K, value: V) {
        previews.remove(key)
        previews[key] = value
        settle()
    }

    /** [key] is gone, or wants it no more. */
    fun drop(key: K) {
        previews.remove(key)
        if (video?.key == key) video = null
        settle()
    }

    /** The holder [key] went full screen: it stays the holder until [unpin]. */
    fun pin(key: K) {
        if (current.value?.key == key) pinned = true
    }

    fun unpin(key: K) {
        if (!pinned || current.value?.key != key) return
        pinned = false
        settle()
    }

    private fun settle() {
        if (pinned) return
        current.value = video ?: previews.entries.lastOrNull()?.let { Turn(it.key, it.value) }
    }
}

/**
 * A player let go of once neither its place on screen nor the full screen
 * shows it: the full screen keeps one whose page closed under it, as XTA's
 * fullscreen route keeps the tile's player (_video.dart's _openFullscreen).
 */
internal class KeptPlayer(private val release: () -> Unit) {
    private var placed = true
    private var fullscreen = false
    private var released = false

    fun enteredFullscreen() {
        fullscreen = true
    }

    fun leftFullscreen() {
        fullscreen = false
        if (!placed) letGo()
    }

    /** Its place left the screen. */
    fun left() {
        placed = false
        if (!fullscreen) letGo()
    }

    private fun letGo() {
        if (released) return
        released = true
        release()
    }
}
