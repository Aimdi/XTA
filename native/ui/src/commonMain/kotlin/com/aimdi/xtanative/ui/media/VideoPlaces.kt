package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.json.J
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where the reader left each of the last [limit] videos, by address: a tile
 * scrolled away and back, or one that lost the feed's player to another,
 * plays on from there instead of from its start. A video seen to its end,
 * or hardly begun, is forgotten. A long video's place outlives the app
 * once the memory is given a file ([keepIn]): the ones left [LASTING_MS] in
 * or later, so a film or a talk goes on where it stopped, while the clips
 * of a feed leave nothing on the device.
 */
internal class VideoPlaceMemory(private val limit: Int = 64) {
    private val places = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > limit
    }

    /** [url] was left at [positionMs] of [durationMs] (0 when not known), [completed] when it had ended. */
    fun keep(url: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val worth = !completed && positionMs >= MIN_MS && (durationMs <= 0 || positionMs < durationMs - END_MS)
        val changed = synchronized(places) {
            val before = places[url]
            if (worth) places[url] = positionMs else places.remove(url)
            maxOf(before ?: 0, if (worth) positionMs else 0) >= LASTING_MS
        }
        if (changed) written()
    }

    /** Where [url] starts: where it was left, else its beginning. */
    fun startOf(url: String): Long = synchronized(places) { places[url] } ?: 0

    private var file: File? = null
    private var scope: CoroutineScope? = null
    private var writing: Job? = null

    /**
     * Takes up the long videos' places [file] holds and writes them there
     * from now on, off the caller's thread, a moment after each change.
     */
    fun keepIn(file: File, scope: CoroutineScope) {
        this.file = file
        this.scope = scope
        scope.launch(Dispatchers.IO) { runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()?.let(::restore) }
    }

    /** The long videos' places, oldest first, as the file holds them. */
    fun lasting(): String = synchronized(places) {
        buildJsonObject { places.filterValues { it >= LASTING_MS }.forEach { (url, at) -> put(url, at) } }.toString()
    }

    /** Takes up the places of [text] ([lasting]'s) under those of this run; anything else in it is left out. */
    fun restore(text: String) {
        val json = J.parse(text)
        val stored = json.keys.mapNotNull { url -> json[url].long?.takeIf { it >= LASTING_MS }?.let { url to it } }
        synchronized(places) {
            val current = places.toMap()
            places.clear()
            stored.forEach { (url, at) -> places[url] = at }
            places.putAll(current)
        }
    }

    private fun written() {
        val to = file ?: return
        writing?.cancel()
        writing = scope?.launch(Dispatchers.IO) {
            delay(WRITE_AFTER_MS)
            runCatching { to.writeText(lasting()) }
        }
    }

    private companion object {
        /** Less than this played is no place to come back to. */
        const val MIN_MS = 1_000L

        /** This close to the end, it starts over. */
        const val END_MS = 1_000L

        /** From here on a place is worth keeping across starts of the app. */
        const val LASTING_MS = 30_000L

        const val WRITE_AFTER_MS = 2_000L
    }
}

/** The app's memory of where videos were left ([VideoPlaceMemory]). */
internal val VideoPlaces = VideoPlaceMemory()
