package com.aimdi.xtanative.services.speech

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/** The downloaded archive is not the one the catalog pinned. */
class VoiceChecksumMismatch : Exception("checksum mismatch")

/** The archive is damaged, unsafe, or lacks a file the voice needs. */
class VoiceArchiveInvalid(reason: String) : Exception(reason)

/** One entry of a tar archive, as the platform's decompressor reads it. */
interface ArchiveEntry {
    val name: String
    val isDirectory: Boolean
    val isSymbolicLink: Boolean

    /** Copies the entry's content to [out]. */
    fun writeTo(out: OutputStream)
}

/**
 * Reads a downloaded archive's entries in order: a bzip2 tar for a voice, an
 * xz tar for the engine. The JVM decodes neither, so each platform supplies
 * this (Commons Compress, or the system's). It throws on a damaged archive.
 */
fun interface ArchiveReader {
    fun read(archive: File, each: (ArchiveEntry) -> Unit)
}

/**
 * How a downloaded voice or engine is checked and put in place
 * (voice_archive.dart, voice_engine_archive.dart): the archive against its
 * pin first, then unpacked into a staging folder that is renamed into place
 * only once every file is there, so a half-installed voice never looks
 * installed. Blocking: run it off the main thread.
 */
object VoiceArchives {
    /** Where [voice] lives once installed under [root]. */
    fun voiceDirectory(root: File, voice: OfflineVoice): File = File(root, voice.id)

    /** SHA-256 of a file as lower-case hex, read in pieces. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /**
     * [name] relative to [archiveRoot]: empty for the root folder itself,
     * null for anything outside it or climbing out of it (`..`, an absolute
     * path).
     */
    fun entryPath(name: String, archiveRoot: String): String? {
        val normal = normalize(name.replace('\\', '/')) ?: return null
        if (normal == archiveRoot) return ""
        return normal.removePrefix("$archiveRoot/").takeIf { normal.startsWith("$archiveRoot/") }
    }

    /** A relative path with `.`, empty segments and `a/..` folded away; null when it is absolute or climbs out. */
    private fun normalize(path: String): String? {
        if (path.startsWith('/')) return null
        val parts = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    /**
     * Checks [archive] against [voice]'s pin and unpacks it to its folder
     * under [root]. A voice without a confirmed checksum is accepted from a
     * sound archive holding every file it needs.
     */
    fun install(archive: File, voice: OfflineVoice, root: File, reader: ArchiveReader) {
        val expected = voice.sha256
        if (expected != null && sha256(archive) != expected) throw VoiceChecksumMismatch()
        val staging = File(root, ".${voice.id}.partial")
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            unpack(archive, voice.archiveRoot, staging, reader)
            voice.requiredFiles.forEach { file ->
                if (File(staging, file).let { !it.isFile || it.length() == 0L }) throw VoiceArchiveInvalid("missing $file")
            }
            val target = voiceDirectory(root, voice)
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw VoiceArchiveInvalid("rename")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun unpack(archive: File, archiveRoot: String, destination: File, reader: ArchiveReader) {
        try {
            reader.read(archive) { entry ->
                val relative = entryPath(entry.name, archiveRoot) ?: throw VoiceArchiveInvalid("path ${entry.name}")
                if (relative.isEmpty()) return@read
                if (entry.isSymbolicLink) throw VoiceArchiveInvalid("link ${entry.name}")
                val target = File(destination, relative)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use(entry::writeTo)
                }
            }
        } catch (error: VoiceArchiveInvalid) {
            throw error
        } catch (error: Exception) {
            throw VoiceArchiveInvalid("tar: ${error.message}")
        }
    }

    /** Whether every file [voice] needs is in [directory]. */
    fun filesPresent(directory: File, voice: OfflineVoice): Boolean = voice.requiredFiles.all { File(directory, it).exists() }

    /** The bytes the files under [directory] take. */
    fun directoryBytes(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Unpacks the engine's libraries from [archive] into its folder under
     * [root], each checked against its pin; anything else in the archive is
     * left out. A library missing is a broken archive; one that does not
     * match is a checksum failure.
     */
    fun installEngine(archive: File, engine: VoiceEnginePackage, root: File, reader: ArchiveReader) {
        val target = VoiceEngines.directory(root, engine)
        val staging = File(target.path + ".partial")
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            val found = unpackLibraries(archive, engine, staging, reader)
            engine.libraries.firstOrNull { it.name !in found }?.let { throw VoiceArchiveInvalid("${it.name} is missing") }
            target.deleteRecursively()
            target.parentFile.mkdirs()
            if (!staging.renameTo(target)) throw VoiceArchiveInvalid("rename")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun unpackLibraries(archive: File, engine: VoiceEnginePackage, staging: File, reader: ArchiveReader): Set<String> {
        val found = HashSet<String>()
        try {
            reader.read(archive) { entry ->
                val library = engine.libraries.firstOrNull { it.name == entry.name.substringAfterLast('/') }
                if (entry.isDirectory || entry.isSymbolicLink || library == null || library.name in found) return@read
                val digest = MessageDigest.getInstance("SHA-256")
                val counted = CountingDigestStream(File(staging, library.name).outputStream(), digest)
                counted.use(entry::writeTo)
                if (counted.count != library.bytes || digest.digest().toHex() != library.sha256) throw VoiceChecksumMismatch()
                found.add(library.name)
            }
        } catch (error: VoiceChecksumMismatch) {
            throw error
        } catch (error: VoiceArchiveInvalid) {
            throw error
        } catch (error: Exception) {
            throw VoiceArchiveInvalid("${error.message}")
        }
        return found
    }

    /** Whether [engine] is installed under [root]: every library there, at its pinned size. */
    fun engineFilesPresent(root: File, engine: VoiceEnginePackage): Boolean {
        val folder = VoiceEngines.directory(root, engine)
        return engine.libraries.all { File(folder, it.name).let { file -> file.isFile && file.length() == it.bytes } }
    }

    private class CountingDigestStream(out: OutputStream, private val digest: MessageDigest) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            digest.update(b.toByte())
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            digest.update(b, off, len)
            count += len
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
