package com.aimdi.xtanative.services.translation

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.services.ai.TranslationCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The translations the reader's service gave, kept on the device
 * (reader_translation_store.dart): the most recent [maxEntries], no more
 * than [maxChars] characters in all, each for as long as it was written
 * for. [load] and [save] read and replace the one file they are kept in; a
 * damaged file only costs a new request.
 */
class KeptTranslations(
    private val load: suspend () -> String?,
    private val save: suspend (String) -> Unit,
    private val maxEntries: Int = 32,
    private val maxChars: Int = 500_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslationCache {
    private data class Entry(val value: String, val until: Long)

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false

    override suspend fun read(key: String): String? = mutex.withLock {
        loadOnce()
        val entry = entries.remove(key) ?: return null
        if (clock() >= entry.until) return null
        entries[key] = entry
        entry.value
    }

    override suspend fun write(key: String, value: String, ttlMillis: Long) = mutex.withLock {
        loadOnce()
        entries.remove(key)
        entries[key] = Entry(value, clock() + ttlMillis)
        trim()
        runCatching { save(encode()) }
        Unit
    }

    /** Forgets every translation kept (Settings › Translation's Clear). */
    suspend fun clear() = mutex.withLock {
        loaded = true
        entries.clear()
        runCatching { save(encode()) }
        Unit
    }

    private suspend fun loadOnce() {
        if (loaded) return
        loaded = true
        val root = J.parse(runCatching { load() }.getOrNull().orEmpty())
        if (root["version"].int != 1) return
        val now = clock()
        root["entries"].keys.forEach { key ->
            val row = root["entries"][key]
            val value = row["value"].string
            val until = row["until"].long
            if (value != null && until != null && until > now) entries[key] = Entry(value, until)
        }
        trim()
    }

    private fun trim() {
        var chars = entries.entries.sumOf { it.key.length + it.value.value.length }
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && (entries.size > maxEntries || chars > maxChars)) {
            val oldest = iterator.next()
            chars -= oldest.key.length + oldest.value.value.length
            iterator.remove()
        }
    }

    private fun encode(): String = buildJsonObject {
        put("version", 1)
        putJsonObject("entries") {
            entries.forEach { (key, entry) ->
                putJsonObject(key) {
                    put("value", entry.value)
                    put("until", entry.until)
                }
            }
        }
    }.toString()
}
