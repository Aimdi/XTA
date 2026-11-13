package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * A value kept whole in one file of the store, shared by this module's
 * device stores: read on first use, then held in [state]. A change is written
 * back whole before it is shown, so the screens never see what the disk does
 * not hold, and a failed write leaves both as they were. A file that cannot
 * be read (damaged, or written by a newer build) is copied aside to
 * `<name>.unreadable` before anything replaces it.
 */
internal class StoredJson<T : Any>(
    private val dir: StoreDir,
    private val name: String,
    private val empty: T,
    private val decode: (J) -> T?,
    private val encode: (T) -> JsonElement,
) {
    private val lock = Mutex()
    private val current = MutableStateFlow<T?>(null)

    /** Null until the file is first read. */
    val state: StateFlow<T?> = current.asStateFlow()

    suspend fun get(): T = lock.withLock { loaded() }

    /** Replaces the value with what [change] makes of it; returning the same instance writes nothing. */
    suspend fun update(change: (T) -> T): T = updateWith { old -> change(old).let { it to it } }

    /** Like [update], also handing back what [change] says alongside the new value. */
    suspend fun <R> updateWith(change: (T) -> Pair<T, R>): R = lock.withLock {
        val old = loaded()
        val (next, result) = change(old)
        if (next !== old) {
            dir.file(name).write(encode(next).toString())
            current.value = next
        }
        result
    }

    private suspend fun loaded(): T = current.value ?: read().also { current.value = it }

    private suspend fun read(): T {
        val text = dir.file(name).read() ?: return empty
        val value = decode(J.parse(text))
        if (value == null) dir.file("$name.unreadable").write(text)
        return value ?: empty
    }
}

/** Every format XTA wrote a time in: ISO-8601 with or without a zone, and SQLite's `yyyy-MM-dd HH:mm:ss` (UTC). */
internal fun parseTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value, sqliteTime).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrNull()
}

private val sqliteTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** [millis] as ISO-8601 in UTC, which XTA's `DateTime.parse` reads back. */
internal fun isoTime(millis: Long): String = Instant.ofEpochMilli(millis).toString()

/** The lowercase hex SHA-256 of [text] in UTF-8. */
internal fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/** The strings of [json], skipping what is not one. */
internal fun strings(json: J): List<String> = json.list.mapNotNull { it.string }
