package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The reader's private notes on Threads accounts, by handle, as XTA keeps a
 * note on an X profile (profile_note.dart): in the plugin's folder, never
 * told to Meta. Empty until the file has been read.
 */
internal class ThreadsAccountNotes(private val file: StoreFile) {
    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<Map<String, String>> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var read = false

    suspend fun load(): Map<String, String> = mutex.withLock { loaded() }

    /** Keeps [note] on [handle]; a blank one removes it. */
    suspend fun keep(handle: String, note: String) = mutex.withLock {
        val next = with(loaded(), handle, note)
        file.write(encode(next))
        mutable.value = next
    }

    private suspend fun loaded(): Map<String, String> {
        if (!read) {
            mutable.value = decode(file.read())
            read = true
        }
        return mutable.value
    }

    companion object {
        fun key(handle: String): String = handle.trim().removePrefix("@").lowercase()

        /** [notes] with [note] on [handle], trimmed; a blank note or handle leaves no note there. */
        fun with(notes: Map<String, String>, handle: String, note: String): Map<String, String> {
            val key = key(handle)
            if (key.isEmpty()) return notes
            val text = note.trim()
            return if (text.isEmpty()) notes - key else notes + (key to text)
        }

        fun encode(notes: Map<String, String>): String = JsonObject(notes.mapValues { JsonPrimitive(it.value) }).toString()

        /** What [text] keeps; anything unreadable is no note. */
        fun decode(text: String?): Map<String, String> {
            val root = J.parse(text.orEmpty())
            return root.keys.mapNotNull { handle -> root[handle].string?.trim()?.ifEmpty { null }?.let { key(handle) to it } }.toMap()
        }
    }
}
