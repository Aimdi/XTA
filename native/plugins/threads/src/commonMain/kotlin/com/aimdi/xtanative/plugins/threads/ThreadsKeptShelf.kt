package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a [ThreadsKeptShelf] held for a key, and when it was read. */
internal data class ThreadsKeptEntry<T>(val value: T, val readAt: Instant)

/**
 * The last answers of a kind of screen (profiles, conversations) kept on the
 * device, one file each under [folder] with an index of them, newest first,
 * so that one opened again shows at once what it showed last while Meta is
 * asked again. Bounded three ways: at most [cap] entries, none older than
 * [maxAgeMillis], and at most [maxBytes] in all; what falls out is deleted.
 * Only what a guest is shown is kept: never a cookie, a token or a session
 * value. A file that cannot be read or written keeps nothing, never fails a
 * read.
 */
internal class ThreadsKeptShelf<T>(
    private val file: (String) -> StoreFile,
    private val folder: String,
    private val cap: Int,
    private val maxAgeMillis: Long,
    private val maxBytes: Long,
    private val encode: (T) -> JsonElement,
    private val decode: (J) -> T?,
    private val clock: () -> Instant = Instant::now,
) {
    /** One kept entry in the index: its key, its file and its size, and when it was read. */
    private data class Row(val key: String, val name: String, val at: Instant, val bytes: Long)

    private val lock = Mutex()
    private var rows: List<Row>? = null

    /** What [key] showed last, and when; null when nothing that fresh was kept. */
    suspend fun of(key: String): ThreadsKeptEntry<T>? = lock.withLock {
        val row = loaded().firstOrNull { it.key == key }?.takeIf { fresh(it) } ?: return@withLock null
        val root = J.parse(file(path(row.name)).read().orEmpty())
        if (root["key"].string != key) return@withLock null
        decode(root["value"])?.let { ThreadsKeptEntry(it, row.at) }
    }

    /** [value] kept as [key]'s, read at [at], first; the oldest beyond the bounds let go. */
    suspend fun keep(key: String, value: T, at: Instant) = lock.withLock {
        val name = fileName(key)
        val text = JsonObject(mapOf("key" to JsonPrimitive(key), "value" to encode(value))).toString()
        val bytes = text.toByteArray().size.toLong()
        if (bytes > maxBytes) return@withLock
        val before = loaded()
        val kept = bounded(listOf(Row(key, name, at, bytes)) + before.filter { it.key != key })
        file(path(name)).write(text)
        save(before, kept)
    }

    /** Takes [entries] (newest first) as the shelf's own when it holds nothing yet: what an older file kept. */
    suspend fun adopt(entries: List<Pair<String, ThreadsKeptEntry<T>>>) = lock.withLock {
        if (loaded().isNotEmpty() || entries.isEmpty()) return@withLock
        val written = entries.distinctBy { it.first }.map { (key, entry) ->
            val name = fileName(key)
            val text = JsonObject(mapOf("key" to JsonPrimitive(key), "value" to encode(entry.value))).toString()
            file(path(name)).write(text)
            Row(key, name, entry.readAt, text.toByteArray().size.toLong())
        }
        save(emptyList(), bounded(written))
    }

    private fun fresh(row: Row): Boolean = clock().toEpochMilli() - row.at.toEpochMilli() <= maxAgeMillis

    private fun bounded(candidates: List<Row>): List<Row> {
        var total = 0L
        return candidates.filter(::fresh).take(cap).takeWhile { row -> (total + row.bytes <= maxBytes).also { if (it) total += row.bytes } }
    }

    private suspend fun save(before: List<Row>, kept: List<Row>) {
        rows = kept
        val names = kept.mapTo(HashSet()) { it.name }
        before.filter { it.name !in names }.forEach { file(path(it.name)).delete() }
        file(path(INDEX)).write(JsonObject(mapOf("v" to JsonPrimitive(VERSION), "entries" to JsonArray(kept.map(::rowJson)))).toString())
    }

    private suspend fun loaded(): List<Row> = rows ?: readIndex().also { rows = it }

    private suspend fun readIndex(): List<Row> {
        val root = J.parse(file(path(INDEX)).read().orEmpty())
        if (root["v"].long != VERSION.toLong()) return emptyList()
        return root["entries"].list.mapNotNull(::rowOf).distinctBy { it.key }
    }

    private fun rowJson(row: Row): JsonObject = JsonObject(
        mapOf("key" to JsonPrimitive(row.key), "file" to JsonPrimitive(row.name), "at" to JsonPrimitive(XtaTime.iso(row.at)), "bytes" to JsonPrimitive(row.bytes)),
    )

    private fun rowOf(row: J): Row? {
        val key = row["key"].string?.ifEmpty { null } ?: return null
        val name = row["file"].string?.takeIf { it == fileName(key) } ?: return null
        val at = XtaTime.parse(row["at"].string) ?: return null
        return Row(key, name, at, row["bytes"].long ?: 0)
    }

    private fun path(name: String) = "$folder/$name"

    companion object {
        private const val INDEX = "index.json"
        private const val VERSION = 1

        /** [key] as a file name: ASCII letters, digits, `_` and `-` kept, anything else `_`; the key inside the file tells apart two that meet. */
        fun fileName(key: String): String =
            key.take(80).map { if (it.isLetterOrDigit() && it.code < 128 || it in "_-") it else '_' }.joinToString("") + ".json"
    }
}
