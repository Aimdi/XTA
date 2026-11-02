package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.pluginhost.testplugin.HelloPlugin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import jdk.security.jarsigner.JarSigner

internal fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** Whether nobody may write [file], by its permissions: asking whether it can be written says yes to root whatever they are. */
internal fun isReadOnly(file: File): Boolean = Files.getPosixFilePermissions(file.toPath()).none { it.name.endsWith("_WRITE") }

/** A zip (a JAR or an APK: both are) holding [entries], in that order. */
internal fun zip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { bytes ->
    ZipOutputStream(bytes).use { out ->
        entries.forEach { (name, content) ->
            out.putNextEntry(ZipEntry(name))
            out.write(content)
            out.closeEntry()
        }
    }
}.toByteArray()

/** The entries of the zip [bytes], in order. */
internal fun unzip(bytes: ByteArray): List<Pair<String, ByteArray>> = ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
    generateSequence { input.nextEntry }.map { it.name to input.readBytes() }.toList()
}

internal fun manifestJson(id: String, version: String, apiVersion: Int = 1, entry: String = HELLO) =
    """{"id": "$id", "version": "$version", "apiVersion": $apiVersion, "entry": "$entry"}"""

internal val HELLO: String = HelloPlugin::class.java.name

/** The class file of [type], as the test build compiled it. */
internal fun classBytes(type: Class<*>): Pair<String, ByteArray> {
    val path = type.name.replace('.', '/') + ".class"
    return path to type.classLoader.getResourceAsStream(path)!!.use { it.readBytes() }
}

/** An unsigned plugin JAR: its xta-plugin.json and the test plugin's classes. */
internal fun pluginJar(id: String, version: String, apiVersion: Int = 1, entry: String = HELLO, extra: List<Pair<String, ByteArray>> = emptyList()): ByteArray =
    zip(listOf(PluginManifest.NAME to manifestJson(id, version, apiVersion, entry).toByteArray(), classBytes(HelloPlugin::class.java)) + extra)

/** One catalogue entry with a desktop file of [bytes] at [url]; [sha256] stands in for the file's own when given. */
internal fun entryJson(
    id: String,
    version: String,
    bytes: ByteArray?,
    apiVersion: Int = 1,
    available: Boolean = true,
    private: Boolean = false,
    category: String = "social",
    url: String = "https://plugins.example/xta-plugin-$id-$version.jar",
    sha256: String? = null,
): String {
    val desktop = bytes?.let { """, "desktop": {"url": "$url", "sha256": "${sha256 ?: sha256(it)}", "size": ${it.size}}""" }.orEmpty()
    return """{"id": "$id", "version": "$version", "apiVersion": $apiVersion, "available": $available, "private": $private,
        "category": "$category", "name": {"en": "Name of $id"}, "description": {"en": "About $id"}$desktop}"""
}

internal fun catalogueJson(vararg entries: String) = """{"apiVersion": 1, "plugins": [${entries.joinToString(",")}]}"""

/** Signing keys made the way the app's is: keytool, then the JDK's JarSigner. Made once per test run. */
internal object TestKeys {
    private const val PASSWORD = "plugin-tests"

    val app: KeyStore.PrivateKeyEntry by lazy { generate("app") }
    val other: KeyStore.PrivateKeyEntry by lazy { generate("other") }

    fun fingerprint(key: KeyStore.PrivateKeyEntry): String = JarSignatureVerifier.fingerprint(key.certificate)

    /** [unsigned] (a JAR's bytes) signed with [key], as jarsigner signs a plugin. */
    fun sign(unsigned: ByteArray, key: KeyStore.PrivateKeyEntry): ByteArray {
        val dir = tempDir("unsigned")
        val file = File(dir, "plugin.jar").apply { writeBytes(unsigned) }
        return ByteArrayOutputStream().also { out ->
            ZipFile(file).use { JarSigner.Builder(key).build().sign(it, out) }
        }.toByteArray().also { dir.deleteRecursively() }
    }

    private fun generate(alias: String): KeyStore.PrivateKeyEntry {
        val store = File(tempDir("keys"), "$alias.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-alias", alias, "-keyalg", "EC", "-keysize", "256",
            "-dname", "CN=XTA Native plugin test $alias", "-validity", "3650",
            "-keystore", store.path, "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD, "-noprompt",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "keytool failed: $output" }
        val keys = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, PASSWORD.toCharArray()) } }
        return keys.getEntry(alias, KeyStore.PasswordProtection(PASSWORD.toCharArray())) as KeyStore.PrivateKeyEntry
    }
}

/** A class loader that cannot see [hidden], standing for the app, which cannot see a plugin's classes. */
internal class HidingClassLoader(parent: ClassLoader, private val hidden: String) : ClassLoader(parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (name.startsWith(hidden)) throw ClassNotFoundException(name) else super.loadClass(name, resolve)
}

internal val testPluginsHidden = HidingClassLoader(HelloPlugin::class.java.classLoader, "com.aimdi.xtanative.pluginhost.testplugin.")

/**
 * Serves [files] by URL from memory, answering a Range as a server does
 * ([ranges]) or ignoring it. A transfer can be held at byte [holdAt] until
 * [release], or until the request is cancelled; [breakAt] makes the
 * connection drop there, once.
 */
internal class FakeArtifacts(
    private val files: Map<String, ByteArray>,
    private val ranges: Boolean = true,
    private val holdAt: Int? = null,
    private val breakAt: Int? = null,
    private val status: Int? = null,
) : ArtifactSource {
    /** The offset of every request opened. */
    val asked = CopyOnWriteArrayList<Long>()
    val cancels = AtomicInteger()
    val release = CountDownLatch(1)
    private val broken = AtomicBoolean(false)

    override fun request(url: String, offset: Long): ArtifactRequest = object : ArtifactRequest {
        private val cancelled = AtomicBoolean(false)

        override fun open(): ArtifactResponse {
            asked += offset
            val bytes = files[url]
            return when {
                status != null -> ArtifactResponse(status, 0, ByteArrayInputStream(ByteArray(0)))
                bytes == null -> ArtifactResponse(404, 0, ByteArrayInputStream(ByteArray(0)))
                ranges && offset > 0 && offset >= bytes.size -> ArtifactResponse(416, 0, ByteArrayInputStream(ByteArray(0)))
                ranges && offset > 0 -> ArtifactResponse(206, offset, stream(bytes, offset.toInt(), cancelled))
                else -> ArtifactResponse(200, 0, stream(bytes, 0, cancelled))
            }
        }

        override fun cancel() {
            cancelled.set(true)
            cancels.incrementAndGet()
        }
    }

    private fun stream(bytes: ByteArray, from: Int, cancelled: AtomicBoolean): InputStream = object : InputStream() {
        private var position = from

        override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) < 0) -1 else it[0].toInt() and 0xff }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == holdAt) hold(cancelled)
            if (position == breakAt && broken.compareAndSet(false, true)) throw IOException("connection reset")
            if (position >= bytes.size) return -1
            val stop = listOfNotNull(holdAt, breakAt, bytes.size).filter { it > position }.min()
            val count = minOf(length, stop - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }
    }

    /** Waits for [release] or a cancel; a test that fails first is not left waiting, the transfer breaks off after a while. */
    private fun hold(cancelled: AtomicBoolean) {
        val giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!release.await(5, TimeUnit.MILLISECONDS)) {
            if (cancelled.get()) throw IOException("Canceled")
            if (System.nanoTime() > giveUp) throw IOException("held too long")
        }
    }
}
