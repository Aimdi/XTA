package com.aimdi.xtanative.store

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One file of the store, read whole and replaced whole: the new text goes to
 * a file beside it first and then takes its name, so a crash mid-write never
 * leaves half a file. Reads and writes of one file take turns.
 */
class StoreFile(private val file: File) {
    private val mutex = Mutex()

    /** The text, or null when nothing was written yet. */
    suspend fun read(): String? = mutex.withLock {
        withContext(Dispatchers.IO) { file.takeIf { it.isFile }?.readText() }
    }

    suspend fun write(text: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, file.name + ".part")
            partial.writeText(text)
            if (!partial.renameTo(file)) {
                file.delete()
                partial.renameTo(file)
            }
        }
    }

    suspend fun delete() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
    }
}

/** The directory the app gives the store; each name is always the same [StoreFile]. */
class StoreDir(val root: File) {
    private val files = HashMap<String, StoreFile>()

    /** [name] may hold one level of folder ("saved/folders.json"). */
    @Synchronized
    fun file(name: String): StoreFile = files.getOrPut(name) { StoreFile(File(root, name)) }
}
