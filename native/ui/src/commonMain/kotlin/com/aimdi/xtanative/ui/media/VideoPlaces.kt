package com.aimdi.xtanative.ui.media

/**
 * Where the reader left each of the last [limit] videos, by address: a tile
 * scrolled away and back, or one that lost the feed's player to another,
 * plays on from there instead of from its start. A video seen to its end,
 * or hardly begun, is forgotten. Used on the main thread only.
 */
internal class VideoPlaceMemory(private val limit: Int = 64) {
    private val places = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > limit
    }

    /** [url] was left at [positionMs] of [durationMs] (0 when not known), [completed] when it had ended. */
    fun keep(url: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val worth = !completed && positionMs >= MIN_MS && (durationMs <= 0 || positionMs < durationMs - END_MS)
        if (worth) places[url] = positionMs else places.remove(url)
    }

    /** Where [url] starts: where it was left, else its beginning. */
    fun startOf(url: String): Long = places[url] ?: 0

    private companion object {
        /** Less than this played is no place to come back to. */
        const val MIN_MS = 1_000L

        /** This close to the end, it starts over. */
        const val END_MS = 1_000L
    }
}

/** The app's memory of where videos were left ([VideoPlaceMemory]). */
internal val VideoPlaces = VideoPlaceMemory()
