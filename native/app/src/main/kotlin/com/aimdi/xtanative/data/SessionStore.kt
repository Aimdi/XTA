package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.session.Session
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The session on disk, in app-private storage that Android backs up nowhere
 * (allowBackup is off): it holds X cookies.
 */
class SessionStore(private val file: File, private val initial: Session = Session()) {
    private val current = MutableStateFlow<Session?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<Session?> = current.asStateFlow()

    private val mutex = Mutex()

    suspend fun load() = mutex.withLock {
        if (current.value != null) return@withLock
        current.value = withContext(Dispatchers.IO) {
            runCatching { Session.decode(file.readText()) }.getOrNull()
        } ?: initial
    }

    suspend fun update(transform: (Session) -> Session) = mutex.withLock {
        val next = transform(current.value ?: initial)
        current.value = next
        withContext(Dispatchers.IO) { write(Session.encode(next)) }
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
