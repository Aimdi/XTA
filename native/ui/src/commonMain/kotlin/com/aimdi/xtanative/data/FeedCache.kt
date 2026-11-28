package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.store.replaceWith
import java.io.File
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A feed as it was left, so a cold start opens on the same posts at the same
 * place, and loads more below them. The place is its own small file: it is
 * written each time the app goes to the background. Several screens can
 * share one cache, so the writes of each file take turns.
 *
 * What was read last is kept decoded while its file stays the same: the
 * unread counts read every feed again on each read mark, and a long feed is
 * megabytes of JSON.
 */
class FeedCache(private val file: File) {
    private val placeFile = File(file.parentFile, file.nameWithoutExtension + "-place.json")
    private val feedTurn = Mutex()
    private val placeTurn = Mutex()
    private val feed = DecodedFile(file, SavedFeed::decode)
    private val place = DecodedFile(placeFile, FeedPlace::decode)

    suspend fun read(): SavedFeed? = withContext(Dispatchers.IO) { feed.read() }

    suspend fun write(saved: SavedFeed) = feedTurn.withLock {
        withContext(Dispatchers.IO) { feed.write(SavedFeed.encode(saved)) }
    }

    suspend fun readPlace(): FeedPlace? = withContext(Dispatchers.IO) { place.read() }

    suspend fun writePlace(place: FeedPlace) = placeTurn.withLock {
        withContext(Dispatchers.IO) { this@FeedCache.place.write(FeedPlace.encode(place)) }
    }

    companion object {
        /** More than a long read; past it the feed starts from the top next time. */
        const val KEPT = 600
    }
}

/**
 * [file] as [decode] reads it, decoded again only once the file is another:
 * another file, size or time ([FileStamp]), or written here. The copy kept is
 * softly held, so memory running short lets it go.
 */
internal class DecodedFile<T : Any>(private val file: File, private val decode: (String) -> T?) {
    private class Kept<T>(val stamp: FileStamp, value: T) {
        val value = SoftReference(value)
    }

    private var kept: Kept<T>? = null
    private var writes = 0L

    fun read(): T? {
        val (generation, before) = synchronized(this) { writes to kept }
        val stamp = FileStamp.of(file) ?: return null
        before?.takeIf { it.stamp == stamp }?.value?.get()?.let { return it }
        val value = runCatching { decode(file.readText()) }.getOrNull() ?: return null
        synchronized(this) { if (writes == generation) kept = Kept(stamp, value) }
        return value
    }

    fun write(text: String) {
        synchronized(this) {
            writes++
            kept = null
        }
        runCatching { file.replaceWith(text) }
    }
}

/** What tells one version of a file from the next: each write replaces it whole, so its identity changes too. */
internal data class FileStamp(val key: Any?, val size: Long, val modified: Instant) {
    companion object {
        fun of(file: File): FileStamp? = runCatching {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            FileStamp(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime().toInstant())
        }.getOrNull()
    }
}
