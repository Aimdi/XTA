package com.aimdi.xtanative.pluginhost

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginManifestTest {
    private val dir = tempDir("manifest")

    private fun file(bytes: ByteArray) = File(dir, "plugin.zip").apply { writeBytes(bytes) }

    @Test
    fun theManifestIsReadFromAJarOrAnApk() {
        val expected = PluginManifest("reddit", "1.0.3", 1, "com.aimdi.xtanative.plugins.reddit.RedditPlugin")
        val json = manifestJson("reddit", "1.0.3", entry = expected.entryClass).toByteArray()
        assertEquals(expected, PluginManifest.read(file(zip(listOf("com/example/A.class" to byteArrayOf(1), PluginManifest.NAME to json)))))
        val apk = zip(listOf("AndroidManifest.xml" to byteArrayOf(3), "classes.dex" to byteArrayOf(4), "assets/xta-plugin.json" to json))
        assertEquals(expected, PluginManifest.read(file(apk)))
    }

    @Test
    fun aMissingOrUnreadableManifestIsNone() {
        val bodies = listOf(
            "not json",
            """{"id": "reddit", "version": "1.0.3", "apiVersion": 1}""",
            """{"id": "../reddit", "version": "1.0.3", "apiVersion": 1, "entry": "a.B"}""",
            """{"id": "reddit", "version": "1.0.3", "apiVersion": 0, "entry": "a.B"}""",
            """{"id": "reddit", "version": "1.0.3", "apiVersion": 1, "entry": "a.B; rm -rf"}""",
        )
        bodies.forEach { body -> assertNull(PluginManifest.read(file(zip(listOf(PluginManifest.NAME to body.toByteArray())))), body) }
        assertNull(PluginManifest.read(file(zip(listOf("classes.dex" to byteArrayOf(4))))))
        assertNull(PluginManifest.read(file("not a zip".toByteArray())))
        val padded = manifestJson("reddit", "1.0.3").padEnd(70_000).toByteArray()
        assertNull(PluginManifest.read(file(zip(listOf(PluginManifest.NAME to padded)))), "past 64 KiB it is no manifest")
    }
}
