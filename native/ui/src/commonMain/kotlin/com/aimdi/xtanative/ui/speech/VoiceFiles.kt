package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.ArchiveEntry
import com.aimdi.xtanative.services.speech.ArchiveReader
import com.aimdi.xtanative.services.speech.VoiceDownloadCancelled
import com.aimdi.xtanative.services.speech.VoiceTransfer
import com.aimdi.xtanative.store.downloads.MediaHttp
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A voice's or the voice engine's download over the app's media requests,
 * each into a file of its own in [staging], reporting bytes as they arrive.
 * Redirects are followed (GitHub hands release files out from another host);
 * an answer that is not a whole file throws.
 */
class MediaVoiceTransfer(private val http: MediaHttp, private val staging: File) : VoiceTransfer {
    override suspend fun download(url: String, progress: (received: Long, total: Long?) -> Unit, cancelled: () -> Boolean): File =
        withContext(Dispatchers.IO) {
            staging.mkdirs()
            val file = File.createTempFile("voice-", ".part", staging)
            try {
                http.get(url, emptyMap()).use { response ->
                    if (response.code !in 200..299) throw IOException("HTTP ${response.code}")
                    val total = response.contentLength
                    var received = 0L
                    progress(0, total)
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            if (cancelled()) throw VoiceDownloadCancelled()
                            val read = response.body.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                            progress(received, total)
                        }
                    }
                    if (total != null && received != total) throw IOException("Incomplete download")
                }
                file
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }
}

/**
 * Unpacks a downloaded archive with the system's own tar (GNU tar on Linux,
 * toybox's on Android): the JVM decodes neither bzip2 nor xz. The archive is
 * unpacked into a folder of its own beside it, its entries are handed over
 * in order, directories before what they hold, symbolic links reported as
 * such and never followed, and the folder is removed afterwards. Where tar
 * cannot decompress by itself, the archive is streamed into it through the
 * decompressor its first bytes name. The archives are pinned (a voice by its
 * whole checksum before it is unpacked, the engine by each library's), so
 * tar only ever sees known files.
 */
class SystemTar(private val tar: String = "tar") : ArchiveReader {
    override fun read(archive: File, each: (ArchiveEntry) -> Unit) {
        val folder = File(archive.parentFile, archive.name + ".unpacked").toPath()
        delete(folder)
        Files.createDirectories(folder)
        try {
            unpack(archive, folder)
            entries(folder).forEach(each)
        } finally {
            delete(folder)
        }
    }

    private fun unpack(archive: File, folder: Path) {
        val direct = run(listOf(tar, "-xf", archive.path, "-C", folder.toString())) ?: return
        val decompressor = decompressorOf(archive) ?: throw IOException(direct)
        delete(folder)
        Files.createDirectories(folder)
        // The paths go in as the shell's own arguments, never into the command text.
        val piped = run(listOf("sh", "-c", "$decompressor < \"\$1\" | $tar -x -C \"\$2\"", "sh", archive.path, folder.toString()))
        if (piped != null) throw IOException(piped)
    }

    /** The command that decompresses [archive] to standard output, by its first bytes; null for anything else. */
    private fun decompressorOf(archive: File): String? {
        val head = archive.inputStream().use { input -> ByteArray(6).also { input.read(it) } }
        return when {
            head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte() -> "zcat"
            head[0] == 'B'.code.toByte() && head[1] == 'Z'.code.toByte() && head[2] == 'h'.code.toByte() -> "bzcat"
            head.contentEquals(byteArrayOf(0xfd.toByte(), '7'.code.toByte(), 'z'.code.toByte(), 'X'.code.toByte(), 'Z'.code.toByte(), 0)) -> "xzcat"
            else -> null
        }
    }

    /** Runs [command] to its end; null when it succeeded, else what it said. */
    private fun run(command: List<String>): String? = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        when {
            !process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) -> "timed out".also { process.destroyForcibly() }
            process.exitValue() == 0 -> null
            else -> output.trim().ifEmpty { "exit ${process.exitValue()}" }
        }
    } catch (error: IOException) {
        error.message ?: command.first()
    }

    private fun entries(root: Path): List<ArchiveEntry> =
        Files.walk(root).use { paths -> paths.filter { it != root }.collect(Collectors.toList()) }.map { UnpackedEntry(root, it) }

    private class UnpackedEntry(root: Path, private val path: Path) : ArchiveEntry {
        override val name: String = root.relativize(path).joinToString("/")
        override val isSymbolicLink: Boolean = Files.isSymbolicLink(path)
        override val isDirectory: Boolean = !isSymbolicLink && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)

        override fun writeTo(out: OutputStream) {
            Files.copy(path, out)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 120L

        /** Deletes [path] and what it holds; a symbolic link is removed itself, never what it points at. */
        fun delete(path: Path) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
            Files.walkFileTree(
                path,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        Files.deleteIfExists(dir)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }
    }
}
