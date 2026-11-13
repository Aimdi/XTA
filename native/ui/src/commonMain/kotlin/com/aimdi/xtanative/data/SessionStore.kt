package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.session.Session
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

    suspend fun load() = mutex.withLock {
        if (current.value != null) return@withLock
        val read = withContext(Dispatchers.IO) { read() }
        failed.value = read.exceptionOrNull()
        current.value = read.getOrNull() ?: initial
    }

    /** Reads the file again after a [failure], keeping what is shown unless it now reads. */
    suspend fun retry() = mutex.withLock {
        if (failed.value == null) return@withLock
        val read = withContext(Dispatchers.IO) { read() }
        failed.value = read.exceptionOrNull()
        read.getOrNull()?.let { current.value = it }
    }

    suspend fun update(transform: (Session) -> Session) = mutex.withLock {
        val next = transform(current.value ?: initial)
        current.value = next
        withContext(Dispatchers.IO) { write(Session.encode(next)) }
        failed.value = null
    }

    private fun read(): Result<Session?> = when {
        !file.exists() -> Result.success(null)
        else -> runCatching { Session.decode(file.readText()) ?: throw IOException("Unreadable session file") }
    }

    private fun write(text: String) {
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeText(text)
        if (!partial.renameTo(file)) {
            file.delete()
            partial.renameTo(file)
        }
    }
}
