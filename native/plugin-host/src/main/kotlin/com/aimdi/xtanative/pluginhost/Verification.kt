package com.aimdi.xtanative.pluginhost

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * The platform's check that a plugin file was signed with the app's own key,
 * made after its size and SHA-256 matched the catalogue and before anything in
 * it loads. Android's, in `:app`, compares the APK's signing certificate with
 * the app's through PackageManager; the desktop's is [JarSignatureVerifier].
 */
fun interface PluginVerifier {
    fun verify(file: File): Verdict
}

/** What a [PluginVerifier] made of a file. */
sealed interface Verdict {
    data object Trusted : Verdict

    /** Refused; [reason] says why, for the log. */
    data class Refused(val reason: String) : Verdict
}

/**
 * The desktop's [PluginVerifier]. The JAR must be signed with jarsigner and
 * the app's key: every class and resource in it has to be signed by a
 * certificate whose SHA-256 fingerprint is [certificateSha256], the one the
 * app was built with (hex, colons allowed, as keytool prints it). One entry
 * unsigned, altered or signed by anyone else, or one name given twice, and the
 * whole JAR is refused.
 */
class JarSignatureVerifier(certificateSha256: String) : PluginVerifier {
    private val expected = certificateSha256.replace(":", "").trim().lowercase()

    init {
        require(expected.matches(Regex("^[0-9a-f]{64}$"))) { "a SHA-256 fingerprint is 64 hex digits" }
    }

    override fun verify(file: File): Verdict = try {
        JarFile(file, true).use(::verify)
    } catch (e: SecurityException) {
        Verdict.Refused("altered after signing: ${e.message}")
    } catch (e: IOException) {
        Verdict.Refused("not a readable JAR: ${e.message}")
    }

    private fun verify(jar: JarFile): Verdict {
        val all = jar.entries().toList()
        val checked = all.filterNot { it.isDirectory || isSignatureFile(it.name) }
        return when {
            all.map { it.name }.toSet().size != all.size -> Verdict.Refused("an entry is named twice")
            checked.isEmpty() -> Verdict.Refused("nothing in it is signed")
            else -> checked.firstOrNull { !signedByApp(jar, it) }
                ?.let { Verdict.Refused("${it.name} is not signed with this app's key") }
                ?: Verdict.Trusted
        }
    }

    /** Reads [entry] to its end, which is when the JAR checks its digest (throwing when it was altered), then asks who signed it. */
    private fun signedByApp(jar: JarFile, entry: JarEntry): Boolean {
        jar.getInputStream(entry).use(::drain)
        return entry.codeSigners.orEmpty().any { signer ->
            signer.signerCertPath.certificates.firstOrNull()?.let(::fingerprint) == expected
        }
    }

    companion object {
        /** The SHA-256 fingerprint of [certificate], lowercase hex: what the app is built with. */
        fun fingerprint(certificate: Certificate): String = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHex()

        /** The files a signature is made of, which sign the rest rather than being signed (the JAR specification's list). */
        private fun isSignatureFile(name: String): Boolean {
            val upper = name.uppercase()
            val inMetaInf = upper.startsWith("META-INF/") && upper.indexOf('/', "META-INF/".length) < 0
            val file = upper.removePrefix("META-INF/")
            return inMetaInf && (file == "MANIFEST.MF" || file.startsWith("SIG-") || BLOCKS.any { file.endsWith(it) })
        }

        private val BLOCKS = listOf(".SF", ".RSA", ".DSA", ".EC")

        private fun drain(input: InputStream) {
            val buffer = ByteArray(8 * 1024)
            while (input.read(buffer) >= 0) Unit
        }
    }
}

/** Whether [file] is the [artifact] the catalogue describes: null when it is, otherwise how it is not. */
internal fun checkArtifact(file: File, artifact: PluginArtifact): PluginFailure? = when {
    file.length() != artifact.size -> PluginFailure.WrongSize(artifact.size, file.length())
    sha256Of(file) != artifact.sha256 -> PluginFailure.WrongChecksum
    else -> null
}

/** [file]'s SHA-256, lowercase hex. */
internal fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
    }
    return digest.digest().toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
