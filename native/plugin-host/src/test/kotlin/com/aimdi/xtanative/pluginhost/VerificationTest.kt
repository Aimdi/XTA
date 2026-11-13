package com.aimdi.xtanative.pluginhost

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VerificationTest {
    private val dir = tempDir("verify")
    private val verifier = JarSignatureVerifier(TestKeys.fingerprint(TestKeys.app))
    private val unsigned = pluginJar("reddit", "1.0.0", extra = listOf("META-INF/services/java.util.function.Supplier" to HELLO.toByteArray()))
    private val signed = TestKeys.sign(unsigned, TestKeys.app)

    private fun file(bytes: ByteArray, name: String = "plugin.jar") = File(dir, name).apply { writeBytes(bytes) }

    private fun refusal(bytes: ByteArray): String = assertIs<Verdict.Refused>(verifier.verify(file(bytes))).reason

    @Test
    fun aFileIsTheCataloguesWhenItsSizeAndDigestAre() {
        val jar = file(signed)
        val artifact = PluginArtifact("https://plugins.example/a.jar", sha256(signed), signed.size.toLong())
        assertEquals(null, checkArtifact(jar, artifact))
        assertEquals(PluginFailure.WrongSize(signed.size + 1L, signed.size.toLong()), checkArtifact(jar, artifact.copy(size = signed.size + 1L)))
        assertEquals(PluginFailure.WrongChecksum, checkArtifact(jar, artifact.copy(sha256 = sha256(unsigned))))
    }

    @Test
    fun aJarSignedWithTheAppsKeyIsTrusted() {
        assertEquals(Verdict.Trusted, verifier.verify(file(signed)))
    }

    @Test
    fun aJarWithAnEntryAlteredAfterSigningIsRefused() {
        val altered = zip(unzip(signed).map { (name, content) -> name to if (name == PluginManifest.NAME) manifestJson("reddit", "6.6.6").toByteArray() else content })
        assertTrue(refusal(altered).startsWith("altered after signing"), refusal(altered))
    }

    @Test
    fun aJarWithAnEntryAddedAfterSigningIsRefused() {
        val added = zip(unzip(signed) + ("com/example/Extra.class" to byteArrayOf(1, 2, 3)))
        assertEquals("com/example/Extra.class is not signed with this app's key", refusal(added))
    }

    @Test
    fun aJarSignedWithAnotherKeyIsRefused() {
        assertTrue(refusal(TestKeys.sign(unsigned, TestKeys.other)).endsWith("is not signed with this app's key"))
    }

    @Test
    fun anUnsignedJarAFileThatIsNoJarAndAnEmptyOneAreRefused() {
        assertTrue(refusal(unsigned).endsWith("is not signed with this app's key"))
        assertTrue(refusal("not a zip".toByteArray()).startsWith("not a readable JAR"))
        assertEquals("nothing in it is signed", refusal(zip(listOf("META-INF/" to ByteArray(0)))))
    }

    @Test
    fun aJarNamingAnEntryTwiceIsRefused() {
        // A zip writer refuses to write a name twice, so the second one is written under a name as long and renamed in the bytes.
        val decoy = PluginManifest.NAME.dropLast(1) + "X"
        val written = zip(unzip(signed) + (decoy to manifestJson("evil", "1.0.0").toByteArray()))
        val twice = String(written, Charsets.ISO_8859_1).replace(decoy, PluginManifest.NAME).toByteArray(Charsets.ISO_8859_1)
        assertEquals("an entry is named twice", refusal(twice))
    }

    @Test
    fun theFingerprintMayBeWrittenAsKeytoolPrintsIt() {
        val keytool = TestKeys.fingerprint(TestKeys.app).uppercase().chunked(2).joinToString(":")
        assertEquals(Verdict.Trusted, JarSignatureVerifier(keytool).verify(file(signed)))
        assertFailsWith<IllegalArgumentException> { JarSignatureVerifier("not a fingerprint") }
    }
}
