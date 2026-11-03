package com.aimdi.xtanative.services.speech

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64

/**
 * A stand-in for tar: one entry per line, `name`, a kind (`f` file, `d`
 * folder, `l` link) and the content in base64, so an archive is a real file
 * with a real checksum, and a damaged one fails to read.
 */
object FakeTar {
    data class Entry(val name: String, val kind: Char = 'f', val content: String = "")

    fun bytes(entries: List<Entry>): ByteArray =
        entries.joinToString("\n") { "${it.name}\t${it.kind}\t${Base64.getEncoder().encodeToString(it.content.toByteArray())}" }.toByteArray()

    fun files(root: String, files: Map<String, String>): List<Entry> = listOf(Entry("$root/", 'd')) + files.map { (name, content) -> Entry("$root/$name", 'f', content) }

    val reader = ArchiveReader { archive, each ->
        archive.readLines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 3 || parts[1].length != 1) throw IOException("damaged")
            val content = Base64.getDecoder().decode(parts[2])
            each(object : ArchiveEntry {
                override val name = parts[0]
                override val isDirectory = parts[1] == "d"
                override val isSymbolicLink = parts[1] == "l"
                override fun writeTo(out: OutputStream) = out.write(content)
            })
        }
    }
}

fun sha256Of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

const val TEST_VOICE_ROOT = "test-voice"

val soundVoiceFiles = mapOf("voice.onnx" to "model", "tokens.txt" to "a 1", "espeak-ng-data/phontab" to "phonemes")

fun testVoice(archive: ByteArray, checksum: String? = sha256Of(archive)) = OfflineVoice(
    id = "xx-test",
    name = "Test",
    locale = "de_DE",
    url = "https://example.com/voice.tar.bz2",
    archiveBytes = archive.size.toLong(),
    sha256 = checksum,
    licence = "CC0-1.0",
    kind = VoiceModelKind.VITS,
    archiveRoot = TEST_VOICE_ROOT,
    model = "voice.onnx",
)

val fakeEngineFiles = mapOf("libonnxruntime.so" to "onnx runtime library", "libsherpa-onnx-c-api.so" to "sherpa onnx library")

fun fakeEngine(files: Map<String, String> = fakeEngineFiles) = VoiceEnginePackage(
    abi = "arm64-v8a",
    archiveBytes = 100,
    libraries = files.map { (name, content) -> VoiceEngineLibrary(name, content.length.toLong(), sha256Of(content.toByteArray())) },
)

fun engineArchive(files: Map<String, String> = fakeEngineFiles): ByteArray =
    FakeTar.bytes(listOf(FakeTar.Entry("jniLibs/", 'd')) + files.map { (name, content) -> FakeTar.Entry("jniLibs/arm64-v8a/$name", 'f', content) })

/**
 * Serves each URL from [answers]: bytes, or a number for a refusal. Each
 * download lands in a staging file of its own, reported in two halves.
 * [during] runs between the halves, so a test can cancel mid-way.
 */
class FakeTransfer(private val answers: Map<String, Any>, private val staging: File, private val during: () -> Unit = {}) : VoiceTransfer {
    val requested = ArrayList<String>()

    override suspend fun download(url: String, progress: (Long, Long?) -> Unit, cancelled: () -> Boolean): File {
        requested += url
        val bytes = answers[url] as? ByteArray ?: throw IOException("HTTP ${answers[url] ?: 404}")
        staging.mkdirs()
        val file = Files.createTempFile(staging.toPath(), "download", ".part").toFile()
        val half = bytes.size / 2
        file.writeBytes(bytes.copyOfRange(0, half))
        progress(half.toLong(), bytes.size.toLong())
        during()
        if (cancelled()) {
            file.delete()
            throw VoiceDownloadCancelled()
        }
        file.appendBytes(bytes.copyOfRange(half, bytes.size))
        progress(bytes.size.toLong(), bytes.size.toLong())
        return file
    }
}
