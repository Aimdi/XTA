package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.SavedFeed
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A feed as it was left, so a cold start opens on the same posts at the same
 * place, and loads more below them. The place is its own small file: it is
 * written each time the app goes to the background.
 */
class FeedCache(private val file: File) {
    private val placeFile = File(file.parentFile, file.nameWithoutExtension + "-place.json")

    suspend fun read(): SavedFeed? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) SavedFeed.decode(file.readText()) else null }.getOrNull()
    }

    suspend fun write(saved: SavedFeed) = withContext(Dispatchers.IO) { atomically(file, SavedFeed.encode(saved)) }

    suspend fun readPlace(): FeedPlace? = withContext(Dispatchers.IO) {
        runCatching { if (placeFile.exists()) FeedPlace.decode(placeFile.readText()) else null }.getOrNull()
    }

    suspend fun writePlace(place: FeedPlace) = withContext(Dispatchers.IO) { atomically(placeFile, FeedPlace.encode(place)) }

    private fun atomically(target: File, text: String) {
        runCatching {
            val partial = File(target.parentFile, target.name + ".part")
            partial.writeText(text)
            if (!partial.renameTo(target)) {
                target.delete()
                partial.renameTo(target)
            }
        }
    }

    companion object {
        /** More than a long read; past it the feed starts from the top next time. */
        const val KEPT = 600
    }
}
