package com.aimdi.xtanative.store

import java.io.File
import java.io.FileOutputStream
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
        withContext(Dispatchers.IO) { file.replaceWith(text) }
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

/**
 * Replaces this file's text with [text] whole: written beside it and flushed
 * to the disk first, then given its name, so neither a crash nor a power
 * loss mid-save leaves it half written or empty.
 */
fun File.replaceWith(text: String) {
    parentFile?.mkdirs()
    val partial = File(parentFile, "$name.part")
    FileOutputStream(partial).use {
        it.write(text.toByteArray())
        it.fd.sync()
    }
    if (!partial.renameTo(this)) {
        delete()
        partial.renameTo(this)
    }
}
