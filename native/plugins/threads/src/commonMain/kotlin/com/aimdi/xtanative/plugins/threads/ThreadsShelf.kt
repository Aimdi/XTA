package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.media.VideoQuality
import com.aimdi.xtanative.ui.plugins.MediaItem

/** A post's pictures and videos as the viewer opens them, each video's qualities, and the [post] its Open post opens, where it offers one. */
internal data class ThreadsViewed(val items: List<MediaItem>, val qualities: List<List<VideoQuality>>, val post: ThreadsPost? = null)

/**
 * What a screen hands the next one it opens (the card that was tapped, the
 * media to view), the last few kept: a route names them by key, as it
 * cannot carry them. After a restart a key names nothing, and the screen
 * reads what its route says instead.
 */
internal class ThreadsShelf(private val capacity: Int = 64) {
    private val kept = LinkedHashMap<String, Any>()
    private var last = 0

    @Synchronized
    fun put(value: Any): String {
        val key = (++last).toString()
        kept[key] = value
        while (kept.size > capacity) kept.remove(kept.keys.first())
        return key
    }

    @Synchronized
    fun post(key: String?): ThreadsPost? = key?.let { kept[it] } as? ThreadsPost

    @Synchronized
    fun viewed(key: String?): ThreadsViewed? = key?.let { kept[it] } as? ThreadsViewed
}
