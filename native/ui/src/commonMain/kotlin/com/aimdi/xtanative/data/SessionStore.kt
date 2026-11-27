package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.keepUnreadable
import com.aimdi.xtanative.store.replaceWith
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The session on disk, in the app's private folder: Android backs it up
 * nowhere (allowBackup is off), and on the desktop only its user can open the
 * folder. It holds X cookies.
 */
class SessionStore(private val file: File, private val initial: Session = Session()) {
    private val current = MutableStateFlow<Session?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<Session?> = current.asStateFlow()

    private val failed = MutableStateFlow<Throwable?>(null)

    /**
     * Why the file there could not be read, while the app runs on an empty
     * session in its place (XTA's "Unable to refresh the subscriptions"); null
     * once a read or a write has succeeded. No file at all is a first start.
     */
    val failure: StateFlow<Throwable?> = failed.asStateFlow()

    private val mutex = Mutex()

    /**
     * Whether the file is there but neither it nor a copy of it could be read
     * (no permission, a disk error): changes then stay in memory, and nothing
     * is written over what it holds until [retry] reads it.
     */
    private var untouchable = false

    suspend fun load() = mutex.withLock { loaded() }

    /** Reads the file again after a [failure], keeping what is shown unless it now reads. */
    suspend fun retry() = mutex.withLock {
        if (failed.value == null) return@withLock
        val read = withContext(Dispatchers.IO) { read() }
        taken(read)
        read.getOrNull()?.let { current.value = it }
    }

    /**
     * The session changed by [transform], taken at once and written; the file
     * is read first when nothing has read it yet. A write that fails (a full
     * disk) keeps it for this run rather than ending it: the next change
     * writes it whole again.
     */
    suspend fun update(transform: (Session) -> Session) = mutex.withLock {
        val before = loaded()
        val next = transform(before)
        // A change that changes nothing writes nothing, and wakes no one who reads the session.
        if (next == before) return@withLock
        current.value = next
        if (untouchable) return@withLock
        try {
            withContext(Dispatchers.IO) { write(Session.encode(next)) }
            failed.value = null
        } catch (_: IOException) {
        }
    }

    private suspend fun loaded(): Session {
        current.value?.let { return it }
        val read = withContext(Dispatchers.IO) { read() }
        taken(read)
        return (read.getOrNull() ?: initial).also { current.value = it }
    }

    private fun taken(read: Result<Session?>) {
        failed.value = read.exceptionOrNull()
        untouchable = read.exceptionOrNull() is Untouchable
    }

    /**
     * The session the file holds; a file that cannot be understood (damaged,
     * or written by a newer build) is first copied aside to
     * `session.json.unreadable-<millis>`, so the empty session the app runs on
     * in its place never writes over the reader's follows, groups and
     * accounts. One that cannot be read at all, or copied, is [Untouchable].
     */
    private fun read(): Result<Session?> = when {
        !file.exists() -> Result.success(null)
        else -> runCatching {
            val text = try {
                file.readText()
            } catch (e: IOException) {
                throw Untouchable(e)
            }
            Session.decode(text) ?: run {
                try {
                    file.keepUnreadable()
                } catch (e: IOException) {
                    throw Untouchable(e)
                }
                throw IOException("Unreadable session file")
            }
        }
    }

    private fun write(text: String) = file.replaceWith(text)

    /** The file could not be read, nor kept aside: nothing may write over it. */
    private class Untouchable(cause: IOException) : IOException(cause.message, cause)
}
