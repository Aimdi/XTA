package com.aimdi.xtanative.store

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
fun File.replaceWith(text: String) = replaceWith(text.toByteArray())

/**
 * [replaceWith] for bytes. Each write stages in its own temp file, so two
 * writers never share one half-written file; the result is readable by the
 * owner only where the file system has POSIX permissions.
 */
fun File.replaceWith(bytes: ByteArray) = replaceThrough { it.write(bytes) }

/** [replaceWith] by a copy of [source]'s bytes, streamed rather than held whole. */
fun File.replaceWithCopyOf(source: File) = replaceThrough { out -> source.inputStream().use { it.copyTo(out) } }

private fun File.replaceThrough(write: (FileOutputStream) -> Unit) {
    val folder = absoluteFile.parentFile.apply { mkdirs() }
    val partial = Files.createTempFile(folder.toPath(), ".$name.", ".part")
    try {
        FileOutputStream(partial.toFile()).use {
            write(it)
            it.fd.sync()
        }
        moveOver(partial, toPath())
    } catch (failure: Throwable) {
        Files.deleteIfExists(partial)
        throw failure
    }
}

private fun moveOver(from: Path, to: Path) {
    try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
    }
}
